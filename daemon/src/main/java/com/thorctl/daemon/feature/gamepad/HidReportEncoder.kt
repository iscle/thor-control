package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.protocol.GamepadHid

/**
 * Controller state, folded from evdev events into the report layout of [GamepadHid]: buttons,
 * sticks, analog triggers and the d-pad hat. Pure and not thread-safe: [ControllerReports] owns
 * the one in use and only touches it under its lock.
 */
class HidReportEncoder {
    private val held = HashSet<Int>()
    private var leftX = 0
    private var leftY = 0
    private var rightX = 0
    private var rightY = 0
    private var leftTrigger = 0
    private var rightTrigger = 0
    private var hatX = 0
    private var hatY = 0

    fun reset() {
        held.clear()
        leftX = 0; leftY = 0; rightX = 0; rightY = 0; leftTrigger = 0; rightTrigger = 0; hatX = 0; hatY = 0
    }

    /** Starts over from a snapshot: the keys held and every axis value, indexed by ABS_* code. */
    fun load(keys: Set<Int>, axisValues: IntArray) {
        reset()
        held += keys
        leftX = axisValues[EvdevCodes.ABS_X]; leftY = axisValues[EvdevCodes.ABS_Y]; rightX = axisValues[EvdevCodes.ABS_RX]; rightY = axisValues[EvdevCodes.ABS_RY]
        leftTrigger = axisValues[EvdevCodes.ABS_Z]; rightTrigger = axisValues[EvdevCodes.ABS_RZ]
        hatX = axisValues[EvdevCodes.ABS_HAT0X]; hatY = axisValues[EvdevCodes.ABS_HAT0Y]
    }

    /** One raw evdev event. Returns the report when it completes a frame (SYN_REPORT), else null. */
    fun onEvent(type: Int, code: Int, value: Int): ByteArray? {
        when (type) {
            EvdevCodes.EV_KEY -> key(code, value != 0)
            EvdevCodes.EV_ABS -> abs(code, value)
            EvdevCodes.EV_SYN -> if (code == EvdevCodes.SYN_REPORT) return build()
        }
        return null
    }

    private fun key(code: Int, down: Boolean) {
        if (down) held += code else held -= code
    }

    private fun abs(code: Int, value: Int) {
        when (code) {
            EvdevCodes.ABS_X -> leftX = value
            EvdevCodes.ABS_Y -> leftY = value
            EvdevCodes.ABS_RX -> rightX = value
            EvdevCodes.ABS_RY -> rightY = value
            EvdevCodes.ABS_Z -> leftTrigger = value
            EvdevCodes.ABS_RZ -> rightTrigger = value
            EvdevCodes.ABS_HAT0X -> hatX = value
            EvdevCodes.ABS_HAT0Y -> hatY = value
        }
    }

    /** The current state as a [GamepadHid.PAYLOAD]-byte input report (without the report ID). */
    fun build(): ByteArray {
        val leftTriggerLevel = trigger(leftTrigger)
        val rightTriggerLevel = trigger(rightTrigger)
        var buttonByte1 = 0
        var buttonByte2 = 0
        for (code in held) {
            when (code) {
                EvdevCodes.BTN_SOUTH -> buttonByte2 = buttonByte2 or GamepadHid.B2_A
                EvdevCodes.BTN_EAST -> buttonByte2 = buttonByte2 or GamepadHid.B2_B
                EvdevCodes.BTN_NORTH -> buttonByte2 = buttonByte2 or GamepadHid.B2_X
                EvdevCodes.BTN_WEST -> buttonByte2 = buttonByte2 or GamepadHid.B2_Y
                EvdevCodes.BTN_TL -> buttonByte2 = buttonByte2 or GamepadHid.B2_L1
                EvdevCodes.BTN_TR -> buttonByte2 = buttonByte2 or GamepadHid.B2_R1
                EvdevCodes.BTN_THUMBL -> buttonByte2 = buttonByte2 or GamepadHid.B2_L3
                EvdevCodes.BTN_THUMBR -> buttonByte1 = buttonByte1 or GamepadHid.B1_R3
                EvdevCodes.BTN_TL2 -> buttonByte1 = buttonByte1 or GamepadHid.B1_L2
                EvdevCodes.BTN_TR2 -> buttonByte1 = buttonByte1 or GamepadHid.B1_R2
                EvdevCodes.BTN_SELECT -> buttonByte1 = buttonByte1 or GamepadHid.B1_SELECT
                EvdevCodes.BTN_START -> buttonByte1 = buttonByte1 or GamepadHid.B1_START
                EvdevCodes.BTN_MODE, EvdevCodes.KEY_HOME -> buttonByte1 = buttonByte1 or GamepadHid.B1_HOME
                EvdevCodes.KEY_BACK -> buttonByte1 = buttonByte1 or GamepadHid.B1_CAPTURE
            }
        }
        // The triggers' digital bits, as on the Stadia: pressed past half way.
        if (leftTriggerLevel >= 128) buttonByte1 = buttonByte1 or GamepadHid.B1_L2
        if (rightTriggerLevel >= 128) buttonByte1 = buttonByte1 or GamepadHid.B1_R2
        val report = ByteArray(GamepadHid.PAYLOAD)
        report[0] = hat().toByte()
        report[1] = buttonByte1.toByte()
        report[2] = buttonByte2.toByte()
        report[3] = stick(leftX).toByte()
        report[4] = stick(leftY).toByte()
        report[5] = stick(rightX).toByte()
        report[6] = stick(rightY).toByte()
        report[7] = leftTriggerLevel.toByte()
        report[8] = rightTriggerLevel.toByte()
        return report
    }

    /** 0 = up, clockwise in eighths, 8 = centred. */
    private fun hat(): Int = when {
        hatY < 0 && hatX == 0 -> 0
        hatY < 0 && hatX > 0 -> 1
        hatY == 0 && hatX > 0 -> 2
        hatY > 0 && hatX > 0 -> 3
        hatY > 0 && hatX == 0 -> 4
        hatY > 0 && hatX < 0 -> 5
        hatY == 0 && hatX < 0 -> 6
        hatY < 0 && hatX < 0 -> 7
        else -> 8
    }

    /** -32767..32767 (evdev) to 1..255 with 128 in the middle. */
    private fun stick(value: Int): Int = ((value.coerceIn(-32767, 32767) + 32768) shr 8).coerceIn(1, 255)

    /** The analog triggers rest a few counts above 0; a small dead zone keeps them at 0. */
    private fun trigger(value: Int): Int =
        ((value - TRIGGER_DEAD) * 255 / (255 - TRIGGER_DEAD)).coerceIn(0, 255)

    companion object {
        const val TRIGGER_DEAD = 10
    }
}
