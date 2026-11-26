package com.thorctl.daemon.hardware.input

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/** An evdev node fed by the test: [send] hands an event to the reader and waits until it was handled. */
class FakeEvdevDevice(val path: String, private val failGrab: Boolean = false, private val switchBits: Int? = 0) : EvdevDevice {
    private class Item(val type: Int = 0, val code: Int = 0, val value: Int = 0, val handled: CountDownLatch? = null)

    private val queue = LinkedBlockingQueue<Item>()

    @Volatile
    var grabbed = false
        private set

    @Volatile
    var closed = false
        private set

    /** A [readEvents] call has returned or thrown. */
    @Volatile
    var readerDone = false
        private set

    fun send(type: Int, code: Int, value: Int) {
        val handled = CountDownLatch(1)
        queue.put(Item(type, code, value, handled))
        assertTrue(handled.await(2, TimeUnit.SECONDS), "$path: event ($type, $code, $value) was not read")
    }

    /** The node goes away: the reader sees end of input. */
    fun disconnect() = queue.put(END)

    override fun readEvents(onEvent: EventHandler) {
        try {
            while (true) {
                val item = queue.take()
                if (item === END) return
                if (item === CLOSED) throw IOException("closed")
                try {
                    onEvent(item.type, item.code, item.value)
                } finally {
                    item.handled?.countDown()
                }
            }
        } finally {
            readerDone = true
        }
    }

    override fun grab() {
        if (failGrab) throw IOException("EBUSY")
        grabbed = true
    }

    override fun switches(): Int = switchBits ?: throw IOException("EVIOCGSW failed")

    override fun close() {
        closed = true
        queue.put(CLOSED)
    }

    private companion object {
        val END = Item()
        val CLOSED = Item()
    }
}
