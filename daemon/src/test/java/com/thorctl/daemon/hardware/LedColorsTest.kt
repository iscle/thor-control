package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeSys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedColorsTest {
    @Test
    fun huesAreFullySaturated() {
        assertEquals(0xFF0000, LedController.hue(0.0))
        assertEquals(0x00FF00, LedController.hue(120.0))
        assertEquals(0x0000FF, LedController.hue(240.0))
        assertEquals(0xFF0000, LedController.hue(360.0))
        assertEquals(0xFF00FF, LedController.hue(-60.0))
    }

    @Test
    fun findsBothSticksLeds() {
        val sys = FakeSys().apply {
            for (i in 0..3) {
                this["/sys/class/leds/left:stick:$i/brightness"] = 0
                this["/sys/class/leds/right:stick:$i/brightness"] = 0
            }
            this["/sys/class/leds/lcd-backlight/brightness"] = 0
        }
        assertTrue(LedController(sys).available)
        assertTrue(!LedController(FakeSys()).available)
    }
}
