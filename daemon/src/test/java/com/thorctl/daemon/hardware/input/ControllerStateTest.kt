package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_CNT
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0X
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_HAT0Y
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_RZ
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_NORTH
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_SOUTH
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_ABS
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerStateTest {
    private val state = ControllerState()

    @Test
    fun tracksKeysAndAxes() {
        state.track(EV_KEY, BTN_SOUTH, 1)
        state.track(EV_KEY, BTN_EAST, 2)
        state.track(EV_ABS, ABS_RZ, 77)
        state.track(EV_ABS, ABS_CNT, 5) // out of range: ignored
        assertTrue(state.isDown(BTN_SOUTH))
        assertTrue(state.isDown(BTN_EAST))
        state.track(EV_KEY, BTN_SOUTH, 0)
        assertFalse(state.isDown(BTN_SOUTH))
        val snapshot = state.snapshot()
        assertEquals(setOf(BTN_EAST), snapshot.keys)
        assertEquals(77, snapshot.axisValues[ABS_RZ])
        state.track(EV_KEY, BTN_NORTH, 1)
        assertEquals(setOf(BTN_EAST), snapshot.keys) // a copy
    }

    @Test
    fun resyncReleasesKeysHeldAtGrabStartButNeverReplaysNewPresses() {
        state.track(EV_KEY, BTN_SOUTH, 1)
        state.track(EV_KEY, BTN_EAST, 1)
        val grabStart = state.snapshot()
        state.track(EV_KEY, BTN_SOUTH, 0)
        state.track(EV_KEY, BTN_NORTH, 1)
        assertEquals(listOf(EvdevEvent(EV_KEY, BTN_SOUTH, 0)), state.resyncEvents(grabStart))
    }

    @Test
    fun resyncUpdatesMovedAxesAndCentersHatsLetGo() {
        state.track(EV_ABS, 0, 100)
        state.track(EV_ABS, ABS_HAT0X, -1)
        state.track(EV_ABS, ABS_HAT0Y, 1)
        val grabStart = state.snapshot()
        state.track(EV_ABS, ABS_RZ, 50)
        state.track(EV_ABS, ABS_HAT0X, 0)
        state.track(EV_ABS, ABS_HAT0Y, -1) // still pressed: not replayed
        assertEquals(
            listOf(EvdevEvent(EV_ABS, ABS_RZ, 50), EvdevEvent(EV_ABS, ABS_HAT0X, 0)),
            state.resyncEvents(grabStart),
        )
    }

    @Test
    fun nothingToResyncWhenNothingChanged() {
        state.track(EV_KEY, BTN_SOUTH, 1)
        assertTrue(state.resyncEvents(state.snapshot()).isEmpty())
    }
}
