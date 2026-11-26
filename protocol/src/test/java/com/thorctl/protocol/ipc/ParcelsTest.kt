package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

class ParcelsTest {
    @Test
    fun stateRoundTrips() {
        assertEquals(Samples.state, Samples.state.toParcel().toModel())
    }

    @Test
    fun statsRoundTrip() {
        assertEquals(Samples.stats, Samples.stats.toParcel().toModel())
    }

    @Test
    fun everyHudEventRoundTrips() {
        val events = listOf(
            HudEvent.Controller(ControllerTarget.TOP),
            HudEvent.Moved("com.app", Screen.BOTTOM),
            HudEvent.Brightness(Screen.TOP, 45),
            HudEvent.NothingToMove(Screen.BOTTOM),
            HudEvent.Game(GameHudState.STARTED, "com.game", 2048),
            HudEvent.Game(GameHudState.OFF),
            HudEvent.Text("hello"),
        )
        for (event in events) {
            val parcel = event.toParcel(displayId = 7)
            assertEquals(event, parcel.toModel())
            assertEquals(7, parcel.displayId)
        }
    }

    @Test
    fun unknownValuesFromANewerPeerFallBackToDefaults() {
        val parcel = Samples.state.toParcel().apply {
            controller = "sideways"
            leds.mode = "strobe"
            gamepad.transport = "serial"
            profiles = null
            fanProfile = null
        }
        val state = parcel.toModel()
        assertEquals(ControllerTarget.FREE, state.controller)
        assertEquals(LedMode.SOLID, state.leds.mode)
        assertEquals(Samples.state.gamepad.copy(transport = com.thorctl.protocol.model.GamepadTransport.USB), state.gamepad)
        assertEquals(emptyList(), state.profiles)
        assertEquals("auto", state.fanProfile)
    }

    @Test
    fun ledsAreClampedToWhatTheHardwareTakes() {
        val parcel = LedState(brightness = 400, speed = 10f, left = 0x7F123456).toParcel()
        val leds = parcel.toModel()
        assertEquals(255, leds.brightness)
        assertEquals(LedState.MAX_SPEED, leds.speed)
        assertEquals(0x123456, leds.left)
    }

    @Test
    fun aMissingNestedParcelIsTheDefault() {
        val parcel = Samples.state.toParcel().apply {
            leds = null
            game = null
            gamepad = null
        }
        val state = parcel.toModel()
        assertEquals(LedState(), state.leds)
        assertEquals(com.thorctl.protocol.model.GameState(), state.game)
        assertEquals(com.thorctl.protocol.model.GamepadState(), state.gamepad)
    }

    @Test
    fun fanCurvesWithUnevenArraysKeepTheCompletePoints() {
        val parcel = Samples.custom.toParcel().apply { percents = floatArrayOf(10f) }
        assertEquals(1, parcel.toModel().points.size)
    }
}
