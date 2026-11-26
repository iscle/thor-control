package com.thorctl.daemon.cli

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CliCommandsTest {
    private val state = DaemonState(FanProfile.SMART_ID, FanProfile.BUILTINS, 0.5f, 0.5f, ControllerTarget.FREE, LedState(enabled = false), true, 0, 1, true)

    private fun parse(line: String) = CliCommands.parse(line.split(' '), state)

    private fun sent(line: String) = (parse(line) as? CliRequest.Send)?.command

    @Test
    fun commands() {
        assertEquals(CliRequest.State, parse("state"))
        assertEquals(CliRequest.Stats, parse("stats"))
        assertEquals(DaemonCommand.SetFanProfile("quiet"), sent("fan quiet"))
        assertEquals(DaemonCommand.SetBrightness(Screen.BOTTOM, 0.4f, true), sent("brightness bottom 0.4"))
        assertEquals(DaemonCommand.SetController(ControllerTarget.TOP), sent("controller top"))
        assertEquals(DaemonCommand.MoveApp(Screen.BOTTOM), sent("move bottom"))
        assertEquals(DaemonCommand.SetGameMode(true), sent("game on"))
        assertEquals(DaemonCommand.SetGamepadTransport(GamepadTransport.BLUETOOTH), sent("gamepad-transport bt"))
        assertEquals(DaemonCommand.RecentsClose, sent("recents-close"))
    }

    @Test
    fun ledsChangeOnlyWhatIsAsked() {
        assertEquals(DaemonCommand.SetLeds(LedState(enabled = true)), sent("leds on"))
        assertEquals(DaemonCommand.SetLeds(LedState(enabled = true, mode = LedMode.RAINBOW)), sent("leds rainbow"))
        assertNull(CliCommands.parse(listOf("leds", "on"), null))
    }

    @Test
    fun rejectsWhatItDoesNotKnow() {
        assertNull(parse("self-destruct"))
        assertNull(parse("controller left"))
        assertNull(parse("brightness top 2"))
        assertNull(parse("brightness top"))
        assertNull(parse("game maybe"))
        assertNull(parse("fan"))
        assertNull(CliCommands.parse(emptyList(), state))
    }
}
