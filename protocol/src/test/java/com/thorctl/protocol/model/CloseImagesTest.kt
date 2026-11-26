package com.thorctl.protocol.model

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class CloseImagesTest {
    private class CountingJpeg : Jpeg {
        var closed = 0

        override fun open() = ByteArrayInputStream(ByteArray(0))

        override fun close() {
            closed++
        }
    }

    @Test
    fun closesEveryImageOfAnEvent() {
        val images = List(4) { CountingJpeg() }
        DaemonEvent.RecentsOpened(0, false, 1, images[0], images[1], listOf(RecentTask(1, "a", 0, true, true, images[2]), RecentTask(2, "b", 0, false, true, null))).closeImages()
        DaemonEvent.RecentsThumb(1, images[3]).closeImages()
        assertEquals(listOf(1, 1, 1, 1), images.map { it.closed })
    }
}
