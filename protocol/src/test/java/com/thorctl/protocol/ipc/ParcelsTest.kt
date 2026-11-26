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
        for (e in events) {
            val p = e.toParcel(displayId = 7)
            assertEquals(e, p.toModel())
            assertEquals(7, p.displayId)
        }
    }

    @Test
    fun unknownValuesFromANewerPeerFallBackToDefaults() {
        val p = Samples.state.toParcel().apply {
            controller = "sideways"
            leds.mode = "strobe"
            gamepad.transport = "serial"
            profiles = null
            fanProfile = null
        }
        val s = p.toModel()
        assertEquals(ControllerTarget.FREE, s.controller)
        assertEquals(LedMode.SOLID, s.leds.mode)
        assertEquals(Samples.state.gamepad.copy(transport = com.thorctl.protocol.model.GamepadTransport.USB), s.gamepad)
        assertEquals(emptyList(), s.profiles)
        assertEquals("auto", s.fanProfile)
    }

    @Test
    fun ledsAreClampedToWhatTheHardwareTakes() {
        val p = LedState(brightness = 400, speed = 10f, left = 0x7F123456).toParcel()
        val l = p.toModel()
        assertEquals(255, l.brightness)
        assertEquals(LedState.MAX_SPEED, l.speed)
        assertEquals(0x123456, l.left)
    }

    @Test
    fun aMissingNestedParcelIsTheDefault() {
        val p = Samples.state.toParcel().apply {
            leds = null
            game = null
            gamepad = null
        }
        val s = p.toModel()
        assertEquals(LedState(), s.leds)
        assertEquals(com.thorctl.protocol.model.GameState(), s.game)
        assertEquals(com.thorctl.protocol.model.GamepadState(), s.gamepad)
    }

    @Test
    fun fanCurvesWithUnevenArraysKeepTheCompletePoints() {
        val p = Samples.custom.toParcel().apply { percents = floatArrayOf(10f) }
        assertEquals(1, p.toModel().points.size)
    }
}
