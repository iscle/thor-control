package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_CNT
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0X
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0Y
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_RZ
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_Z
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_DPAD_LEFT
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_NORTH
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_SOUTH
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL2
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR2
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_WEST
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_ABS
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_SYN
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_UP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComboDetectorTest {
    private val detector = ComboDetector().apply { arm(held()) }

    private fun held(keys: Set<Int> = emptySet(), vararg axes: Pair<Int, Int>): ControllerSnapshot {
        val axisValues = IntArray(ABS_CNT)
        for ((axis, value) in axes) axisValues[axis] = value
        return ControllerSnapshot(keys, axisValues)
    }

    private fun key(code: Int, value: Int) = detector.detect(EV_KEY, code, value)

    private fun abs(code: Int, value: Int) = detector.detect(EV_ABS, code, value)

    @Test
    fun buttonsFireOnPressOnly() {
        assertEquals(AynCombo.B, key(BTN_EAST, 1))
        assertNull(key(BTN_EAST, 2))
        assertNull(key(BTN_EAST, 0))
        assertEquals(AynCombo.X, key(BTN_NORTH, 1))
        assertEquals(AynCombo.Y, key(BTN_WEST, 1))
        assertEquals(AynCombo.L1, key(BTN_TL, 1))
        assertEquals(AynCombo.R1, key(BTN_TR, 1))
        assertEquals(AynCombo.UP, key(KEY_UP, 1))
        assertEquals(AynCombo.LEFT, key(BTN_DPAD_LEFT, 1))
        assertNull(key(BTN_SOUTH, 1))
        assertNull(detector.detect(EV_SYN, 0, 0))
    }

    @Test
    fun hatFiresOncePerNewDirection() {
        assertEquals(AynCombo.UP, abs(ABS_HAT0Y, -1))
        assertNull(abs(ABS_HAT0Y, -1))
        assertNull(abs(ABS_HAT0Y, 0))
        assertEquals(AynCombo.DOWN, abs(ABS_HAT0Y, 1))
        assertEquals(AynCombo.UP, abs(ABS_HAT0Y, -1))
        assertEquals(AynCombo.RIGHT, abs(ABS_HAT0X, 1))
        assertEquals(AynCombo.LEFT, abs(ABS_HAT0X, -1))
    }

    @Test
    fun hatHeldWhenArmedIsNotAPress() {
        detector.arm(held(emptySet(), ABS_HAT0X to -1))
        assertNull(abs(ABS_HAT0X, -1))
        assertNull(abs(ABS_HAT0X, 0))
        assertEquals(AynCombo.LEFT, abs(ABS_HAT0X, -1))
    }

    @Test
    fun analogTriggerFiresOncePerPullWithHysteresis() {
        assertNull(abs(ABS_Z, ComboDetector.TRIGGER_ON - 1))
        assertEquals(AynCombo.L2, abs(ABS_Z, ComboDetector.TRIGGER_ON))
        assertNull(abs(ABS_Z, 255))
        assertNull(abs(ABS_Z, ComboDetector.TRIGGER_OFF)) // still pulled
        assertNull(abs(ABS_Z, ComboDetector.TRIGGER_OFF - 1)) // let go
        assertNull(abs(ABS_Z, ComboDetector.TRIGGER_ON - 1))
        assertEquals(AynCombo.L2, abs(ABS_Z, ComboDetector.TRIGGER_ON))
    }

    @Test
    fun triggerButtonAndAxisAreOnePull() {
        assertEquals(AynCombo.R2, key(BTN_TR2, 1))
        assertNull(abs(ABS_RZ, 255))
        assertNull(key(BTN_TR2, 0)) // the axis still holds it
        assertNull(abs(ABS_RZ, 0))
        assertEquals(AynCombo.R2, abs(ABS_RZ, 255))
    }

    @Test
    fun triggerHeldWhenArmedIsNotAPress() {
        detector.arm(held(setOf(BTN_TL2), ABS_RZ to 200))
        assertNull(key(BTN_TL2, 1))
        assertNull(abs(ABS_RZ, 255))
        assertNull(key(BTN_TL2, 0))
        assertEquals(AynCombo.L2, key(BTN_TL2, 1))
        assertNull(abs(ABS_RZ, 0))
        assertEquals(AynCombo.R2, abs(ABS_RZ, 255))
    }
}
