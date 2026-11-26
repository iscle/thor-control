package com.thorctl.daemon.hardware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputWatcherTest {
    @Test
    fun parsesProcInputDevices() {
        val text = """
            I: Bus=0019 Vendor=0001 Product=0001 Version=0100
            N: Name="gpio-keys"
            H: Handlers=kbd event2
            B: KEY=4000000000000 0

            N: Name="Xbox Wireless Controller"
            H: Handlers=event5 js0
            B: KEY=7fff000000000000 0 100040000000 0 0

            N: Name="no handler"
        """.trimIndent()
        val devs = InputWatcher.parseDevices(text)
        assertEquals(listOf("gpio-keys", "Xbox Wireless Controller"), devs.map { it.name })
        assertEquals(listOf("event2", "event5"), devs.map { it.handler })
        assertTrue(devs[0].hasKey(114)) // bit 50 of the first word
        assertTrue(devs[1].hasKey(InputWatcher.BTN_SOUTH))
        assertTrue(InputWatcher.parseDevices(null).isEmpty())
    }
}
