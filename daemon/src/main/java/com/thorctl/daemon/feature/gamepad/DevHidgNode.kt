package com.thorctl.daemon.feature.gamepad

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** [HidgNode] on the real /dev/hidg0. */
@Inject
@ContributesBinding(DaemonScope::class)
class DevHidgNode : HidgNode {
    override fun openOutput(): OutputStream = FileOutputStream(HIDG)

    /**
     * f_hid hands the host a new buffer only once the previous one is read, so unread reports
     * would leave the host waiting.
     */
    override fun drainHostReports(running: () -> Boolean) {
        val buffer = ByteArray(64)
        while (running()) {
            val fileDescriptor = try {
                Os.open(HIDG, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
            } catch (_: ErrnoException) {
                Thread.sleep(300) // node not there yet
                continue
            }
            try {
                val pollRequest = StructPollfd().apply {
                    this.fd = fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                while (running()) {
                    pollRequest.revents = 0
                    if (Os.poll(arrayOf(pollRequest), POLL_MS) <= 0) continue
                    val returnedEvents = pollRequest.revents.toInt()
                    if (returnedEvents and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) break
                    if (returnedEvents and OsConstants.POLLIN != 0) Os.read(fileDescriptor, buffer, 0, buffer.size)
                }
            } catch (_: ErrnoException) {
                // Host gone or nothing to read after all: open again shortly.
            } catch (_: IOException) {
                // An interrupted read (InterruptedIOException): the same, open again shortly.
            } finally {
                try {
                    Os.close(fileDescriptor)
                } catch (_: ErrnoException) {
                }
            }
            if (running()) Thread.sleep(250)
        }
    }

    companion object {
        const val HIDG = "/dev/hidg0"

        /** How long a poll waits; also how soon the reader notices it should stop. */
        const val POLL_MS = 250
    }
}
