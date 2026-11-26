package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvdevTouchscreensTest {
    private val sys = FakeSys().apply {
        this[InputNodes.DEVICES] = """
            I: Bus=0018 Vendor=0000 Product=0000 Version=0000
            N: Name="fts_ts"
            H: Handlers=event3

            I: Bus=0018 Vendor=0000 Product=0000 Version=0000
            N: Name="fts_ts_secondary"
            H: Handlers=event4
        """.trimIndent()
    }
    private val evdev = FakeEvdev()
    private val touchscreens = EvdevTouchscreens(InputNodes(sys), evdev)

    @Test
    fun offTakesTheNodeAndOnLetsItGo() {
        touchscreens.setEnabled("fts_ts_secondary", enabled = false)
        val node = evdev.opened.single()
        assertEquals("/dev/input/event4", node.path)
        assertTrue(node.grabbed)
        assertFalse(node.closed)

        touchscreens.setEnabled("fts_ts_secondary", enabled = true)
        assertTrue(node.closed)
    }

    @Test
    fun turningOffTwiceTakesItOnce() {
        touchscreens.setEnabled("fts_ts", enabled = false)
        touchscreens.setEnabled("fts_ts", enabled = false)
        assertEquals(1, evdev.opened.size)
    }

    @Test
    fun aFailedGrabClosesTheNodeAndIsTriedAgainNextTime() {
        evdev.failGrab = true
        touchscreens.setEnabled("fts_ts", enabled = false)
        assertTrue(evdev.opened.single().closed)

        evdev.failGrab = false
        touchscreens.setEnabled("fts_ts", enabled = false)
        assertEquals(2, evdev.opened.size)
        assertTrue(evdev.opened.last().grabbed)
    }

    @Test
    fun anUnknownOrUnopenableScreenIsLeftAlone() {
        touchscreens.setEnabled("nope", enabled = false)
        evdev.failOpen = true
        touchscreens.setEnabled("fts_ts", enabled = false)
        assertTrue(evdev.opened.isEmpty())
        touchscreens.setEnabled("fts_ts", enabled = true)
    }
}
