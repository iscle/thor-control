package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.GamepadHid
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import kotlin.concurrent.thread

/**
 * The gamepad's end of the HID function's node ([HidgNode]). A writer thread always sends the
 * newest report; f_hid only blocks while the previous one waits for the host's next poll, so
 * nothing queues up. A reader drains what the host sends (rumble), so the host never stalls on a
 * full queue.
 */
@Inject
@SingleIn(DaemonScope::class)
class UsbHidEndpoint(private val node: HidgNode) {
    private val lock = Any()
    private var pending: ByteArray? = null

    @Volatile
    private var running = false

    private val threads = ArrayList<Thread>()

    /** The newest report; the writer sends it when it gets to it (an older one not sent yet is dropped). */
    fun submit(report: ByteArray) {
        synchronized(lock) {
            pending = report
            (lock as Object).notifyAll()
        }
    }

    /**
     * Starts the writer and the reader (once; a second call while they run does nothing).
     * [current] is what a fresh host gets first: the last report submitted, of any transport.
     */
    fun start(current: () -> ByteArray) {
        if (running) return
        running = true
        synchronized(threads) {
            threads += thread(name = "gamepad-usb-tx", isDaemon = true) { writeLoop(current) }
            threads += thread(name = "gamepad-usb-rx", isDaemon = true) { node.drainHostReports { running } }
        }
    }

    /** Asks the I/O threads to finish, waking a writer that waits for a report so it finishes at once. */
    fun stop() {
        running = false
        synchronized(lock) { (lock as Object).notifyAll() }
    }

    /**
     * Before the function goes away: waits (up to [JOIN_MS] each) until the I/O threads have
     * closed the node. False if one is still running (a write the host never polls for).
     */
    fun join(): Boolean {
        val stopping = synchronized(threads) { threads.toList().also { threads.clear() } }
        stopping.forEach { it.join(JOIN_MS) }
        return stopping.none { it.isAlive }
    }

    private fun writeLoop(current: () -> ByteArray) {
        val frameBytes = ByteArray(1 + GamepadHid.PAYLOAD)
        frameBytes[0] = GamepadHid.REPORT_ID.toByte()
        while (running) {
            val hidOutput = try {
                node.openOutput()
            } catch (_: IOException) {
                Thread.sleep(300) // node not there yet (the gadget is still binding)
                continue
            }
            try {
                hidOutput.use { output ->
                    // A fresh host: send the current state first.
                    var next: ByteArray? = current().takeIf { it.size == GamepadHid.PAYLOAD }
                    while (running) {
                        val report = next ?: synchronized(lock) {
                            while (pending == null && running) (lock as Object).wait(1000)
                            pending.also { pending = null }
                        } ?: continue
                        next = null
                        System.arraycopy(report, 0, frameBytes, 1, GamepadHid.PAYLOAD)
                        try {
                            // Returns as soon as the report is queued; only blocks while the previous
                            // one is still waiting for the host's poll (newer reports replace pending).
                            output.write(frameBytes)
                        } catch (_: IOException) {
                            // Not configured (no host) or the host went away: retry shortly.
                            synchronized(lock) { if (pending == null) pending = report }
                            Thread.sleep(250)
                            break
                        }
                    }
                }
            } catch (_: IOException) {
                // Closing failed: the node is gone anyway; open it again (or stop).
            }
        }
    }

    companion object {
        const val JOIN_MS = 1000L
    }
}
