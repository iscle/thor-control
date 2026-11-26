package com.thorctl.daemon.feature.gamepad

import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** An in-memory /dev/hidg0: records what is written and how many handles are open right now. */
class FakeHidgNode : HidgNode {
    /** Every frame written (report ID first), in order. */
    val written = CopyOnWriteArrayList<ByteArray>()

    /** Opens for writing so far. */
    val opens = AtomicInteger()

    /** Opens that fail ("node not there yet") before one succeeds. */
    @Volatile
    var failOpens = 0

    /** Writes that fail ("not configured") before one succeeds. */
    @Volatile
    var failWrites = 0

    private val openHandles = AtomicInteger()

    /** Writer streams not closed yet, plus a reader draining. */
    val openNow: Int get() = openHandles.get()

    override fun openOutput(): OutputStream {
        if (failOpens > 0) {
            failOpens--
            throw FileNotFoundException("/dev/hidg0: no such device")
        }
        opens.incrementAndGet()
        openHandles.incrementAndGet()
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (failWrites > 0) {
                    failWrites--
                    throw IOException("not configured")
                }
                written += b.copyOfRange(off, off + len)
            }

            override fun close() {
                openHandles.decrementAndGet()
            }
        }
    }

    override fun drainHostReports(running: () -> Boolean) {
        openHandles.incrementAndGet()
        try {
            while (running()) Thread.sleep(5)
        } finally {
            openHandles.decrementAndGet()
        }
    }
}
