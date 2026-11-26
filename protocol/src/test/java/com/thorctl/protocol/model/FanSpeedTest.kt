package com.thorctl.protocol.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FanSpeedTest {
    @Test
    fun zeroIsOffAndAnythingElseTurns() {
        assertEquals(0, FanSpeed.pwmFor(0f))
        assertEquals(FanSpeed.MIN_SPIN_PWM + 2, FanSpeed.pwmFor(1f))
        assertEquals(FanSpeed.MAX_PWM, FanSpeed.pwmFor(100f))
        assertEquals(FanSpeed.MAX_PWM, FanSpeed.pwmFor(150f))
    }

    @Test
    fun theAppShowsTheCurvesPercentBack() {
        for (percent in 0..100) assertEquals(percent, FanSpeed.percentOf(FanSpeed.pwmFor(percent.toFloat())))
        assertEquals(1, FanSpeed.percentOf(FanSpeed.MIN_SPIN_PWM)) // turning, at its slowest
    }
}
