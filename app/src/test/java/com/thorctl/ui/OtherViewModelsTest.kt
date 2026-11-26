package com.thorctl.ui

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.testing.FakeAppInfo
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakePairing
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.sampleState
import com.thorctl.testing.sampleStats
import com.thorctl.ui.controller.ControllerViewModel
import com.thorctl.ui.game.GameViewModel
import com.thorctl.ui.gamepad.GamepadViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OtherViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()

    @Test
    fun controllerTargets() {
        val vm = ControllerViewModel(daemon)
        vm.setTarget(ControllerTarget.BOTTOM)
        assertEquals(listOf<DaemonCommand>(DaemonCommand.SetController(ControllerTarget.BOTTOM)), daemon.sent)
        daemon.state.value = sampleState.copy(controller = ControllerTarget.BOTTOM)
        assertEquals(ControllerTarget.BOTTOM, vm.uiState.value.target)
    }

    @Test
    fun gamepadMode() {
        val pairing = FakePairing()
        val vm = GamepadViewModel(daemon, pairing)
        vm.setOn(true)
        vm.setTransport(GamepadTransport.BLUETOOTH)
        vm.setScreensOff(false)
        vm.pairNewDevice()
        assertEquals(
            listOf(DaemonCommand.SetGamepad(true), DaemonCommand.SetGamepadTransport(GamepadTransport.BLUETOOTH), DaemonCommand.SetGamepadScreensOff(false)),
            daemon.sent,
        )
        assertEquals(1, pairing.prompts)
        daemon.state.value = null
        assertEquals(GamepadState(), vm.uiState.value.gamepad)
        assertFalse(vm.uiState.value.enabled)
    }

    @Test
    fun gameModeStatus() {
        val vm = GameViewModel(daemon, FakeAppInfo())
        assertEquals("Off", vm.uiState.value.status.first)
        daemon.state.value = sampleState.copy(game = GameState(enabled = true))
        assertEquals("Waiting for a game", vm.uiState.value.status.first)
        daemon.state.value = sampleState.copy(game = GameState(enabled = true, pkg = "com.game", boosted = true, freedMb = 1536))
        daemon.stats.value = sampleStats
        assertEquals("Space Game" to "Boosting · 1.5 GB freed · 3.0 GB free now", vm.uiState.value.status)
        vm.updateOptions { it.copy(cpu = false) }
        assertEquals(DaemonCommand.SetGameOptions(GameState().options.copy(cpu = false)), daemon.sent.last())
    }
}
