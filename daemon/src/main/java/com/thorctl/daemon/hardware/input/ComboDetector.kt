package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0X
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0Y
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_RZ
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_Z
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_DPAD_DOWN
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_DPAD_LEFT
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_DPAD_RIGHT
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_DPAD_UP
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_NORTH
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL2
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR2
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_WEST
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_ABS
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_DOWN
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_LEFT
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_RIGHT
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_UP

/**
 * Turns raw controller events into [AynCombo] presses while AYN is held. Only new presses count:
 * [arm] it with what is held when AYN goes down. Not thread-safe; [ControllerInput] arms it under
 * its lock and feeds it from one reader thread.
 */
class ComboDetector {
    private var hatX = 0
    private var hatY = 0

    // L2 / R2 are analog (ABS_Z / ABS_RZ, 0..255) and also report a button when fully
    // pulled; either one counts, and a pull fires once until the trigger is let go again.
    private val triggerKey = BooleanArray(2)
    private val triggerAxis = BooleanArray(2)
    private val triggerDown = BooleanArray(2)

    /** Combo detection starts from [held]: nothing already down counts as a press. */
    fun arm(held: ControllerSnapshot) {
        hatX = held.axisValues[ABS_HAT0X]
        hatY = held.axisValues[ABS_HAT0Y]
        // A trigger already held when AYN goes down doesn't count as a press.
        triggerKey[0] = BTN_TL2 in held.keys
        triggerKey[1] = BTN_TR2 in held.keys
        triggerAxis[0] = held.axisValues[ABS_Z] >= TRIGGER_ON
        triggerAxis[1] = held.axisValues[ABS_RZ] >= TRIGGER_ON
        triggerDown[0] = triggerKey[0] || triggerAxis[0]
        triggerDown[1] = triggerKey[1] || triggerAxis[1]
    }

    /** The combo this event presses, if any (an event presses at most one). */
    fun detect(type: Int, code: Int, value: Int): AynCombo? = when (type) {
        EV_KEY -> when (code) {
            BTN_TL2 -> trigger(LEFT_TRIGGER, key = value != 0)
            BTN_TR2 -> trigger(RIGHT_TRIGGER, key = value != 0)
            else -> if (value == 1) BUTTONS[code] else null
        }
        EV_ABS -> when (code) {
            ABS_Z -> trigger(LEFT_TRIGGER, axis = value)
            ABS_RZ -> trigger(RIGHT_TRIGGER, axis = value)
            ABS_HAT0Y -> hat(value, hatY, AynCombo.UP, AynCombo.DOWN).also { hatY = value }
            ABS_HAT0X -> hat(value, hatX, AynCombo.LEFT, AynCombo.RIGHT).also { hatX = value }
            else -> null
        }
        else -> null
    }

    private fun hat(value: Int, previous: Int, negative: AynCombo, positive: AynCombo): AynCombo? = when {
        value < 0 && previous >= 0 -> negative
        value > 0 && previous <= 0 -> positive
        else -> null
    }

    private fun trigger(side: Int, key: Boolean? = null, axis: Int? = null): AynCombo? {
        if (key != null) triggerKey[side] = key
        if (axis != null) triggerAxis[side] = if (triggerAxis[side]) axis >= TRIGGER_OFF else axis >= TRIGGER_ON
        val down = triggerKey[side] || triggerAxis[side]
        val pressed = down && !triggerDown[side]
        triggerDown[side] = down
        return if (!pressed) null else if (side == LEFT_TRIGGER) AynCombo.L2 else AynCombo.R2
    }

    companion object {
        const val TRIGGER_ON = 180
        const val TRIGGER_OFF = 110

        private const val LEFT_TRIGGER = 0
        private const val RIGHT_TRIGGER = 1

        private val BUTTONS = mapOf(
            BTN_TL to AynCombo.L1,
            BTN_TR to AynCombo.R1,
            BTN_EAST to AynCombo.B,
            BTN_NORTH to AynCombo.X,
            BTN_WEST to AynCombo.Y,
            BTN_DPAD_UP to AynCombo.UP,
            KEY_UP to AynCombo.UP,
            BTN_DPAD_DOWN to AynCombo.DOWN,
            KEY_DOWN to AynCombo.DOWN,
            BTN_DPAD_LEFT to AynCombo.LEFT,
            KEY_LEFT to AynCombo.LEFT,
            BTN_DPAD_RIGHT to AynCombo.RIGHT,
            KEY_RIGHT to AynCombo.RIGHT,
        )
    }
}
