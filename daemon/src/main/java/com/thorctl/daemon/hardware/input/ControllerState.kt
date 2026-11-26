package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_CNT
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0X
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0Y
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_ABS
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY

/**
 * The controller as last seen by us: keys held and axis values. Not thread-safe;
 * [ControllerInput] guards it with its lock.
 */
class ControllerState {
    private val keys = HashSet<Int>()
    private val axisValues = IntArray(ABS_CNT)

    fun track(type: Int, code: Int, value: Int) {
        when (type) {
            EV_KEY -> if (value != 0) keys += code else keys -= code
            EV_ABS -> if (code in 0 until ABS_CNT) axisValues[code] = value
        }
    }

    fun isDown(code: Int): Boolean = code in keys

    fun snapshot(): ControllerSnapshot = ControllerSnapshot(HashSet(keys), axisValues.copyOf())

    /**
     * Android saw nothing while we held the grab that started at [grabStart]. Release anything
     * it still believes is pressed and bring analog axes up to date, but never replay a press
     * that started during the grab (that would send the combo button to the game after all).
     */
    fun resyncEvents(grabStart: ControllerSnapshot): List<EvdevEvent> {
        val events = ArrayList<EvdevEvent>()
        for (code in grabStart.keys) if (code !in keys) events += EvdevEvent(EV_KEY, code, 0)
        for (axis in ANALOG_AXES) if (axisValues[axis] != grabStart.axisValues[axis]) events += EvdevEvent(EV_ABS, axis, axisValues[axis])
        for (hat in intArrayOf(ABS_HAT0X, ABS_HAT0Y)) if (grabStart.axisValues[hat] != 0 && axisValues[hat] == 0) events += EvdevEvent(EV_ABS, hat, 0)
        return events
    }

    companion object {
        /** Sticks (X, Y, RX, RY), triggers (Z, RZ) and the gas / brake axes. */
        val ANALOG_AXES = intArrayOf(0, 1, 2, 3, 4, 5, 9, 10)
    }
}
