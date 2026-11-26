package com.thorctl.daemon.feature.gamepad

import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.GamepadHid
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * The gamepad's end of /dev/hidg0, the HID function's device node. A writer thread always sends
 * the newest report; f_hid only blocks while the previous one waits for the host's next poll, so
 * nothing queues up. A reader drains what the host sends (rumble), so the host never stalls on a
 * full queue.
 */
@Inject
@SingleIn(DaemonScope::class)
class UsbHidEndpoint {
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
            threads += thread(name = "gamepad-usb-rx", isDaemon = true) { readLoop() }
        }
    }

    /**
     * Asks the I/O threads to finish. [wakeWriter] also wakes a writer waiting for a report;
     * without it, the writer notices within a second (its wait times out).
     */
    fun stop(wakeWriter: Boolean) {
        running = false
        if (wakeWriter) synchronized(lock) { (lock as Object).notifyAll() }
    }

    /** Before the function goes away: waits (up to a second each) until the I/O threads have closed /dev/hidg0. */
    fun join() {
        val stopping = synchronized(threads) { threads.toList().also { threads.clear() } }
        stopping.forEach { it.join(1000) }
    }

    /**
     * Drains reports from the host (rumble, output report 5). f_hid hands the host a new buffer
     * only once the previous one is read, so unread reports would leave the host waiting.
     */
    private fun readLoop() {
        val buffer = ByteArray(64)
        while (running) {
            val fileDescriptor = try {
                Os.open(HIDG, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
            } catch (_: Exception) {
                Thread.sleep(300) // node not there yet
                continue
            }
            try {
                val pollRequest = StructPollfd().apply {
                    this.fd = fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                while (running) {
                    pollRequest.revents = 0
                    if (Os.poll(arrayOf(pollRequest), 250) <= 0) continue
                    val returnedEvents = pollRequest.revents.toInt()
                    if (returnedEvents and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) break
                    if (returnedEvents and OsConstants.POLLIN != 0) Os.read(fileDescriptor, buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
                // Host gone or nothing to read after all: open again shortly.
            } finally {
                try {
                    Os.close(fileDescriptor)
                } catch (_: Exception) {
                }
            }
            if (running) Thread.sleep(250)
        }
    }

    private fun writeLoop(current: () -> ByteArray) {
        val frameBytes = ByteArray(1 + GamepadHid.PAYLOAD)
        frameBytes[0] = GamepadHid.REPORT_ID.toByte()
        while (running) {
            val hidOutput = try {
                FileOutputStream(HIDG)
            } catch (_: Exception) {
                Thread.sleep(300) // node not there yet (the gadget is still binding)
                continue
            }
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
                    } catch (_: Exception) {
                        // Not configured (no host) or the host went away: retry shortly.
                        synchronized(lock) { if (pending == null) pending = report }
                        Thread.sleep(250)
                        break
                    }
                }
            }
        }
    }

    companion object {
        const val HIDG = "/dev/hidg0"
    }
}
