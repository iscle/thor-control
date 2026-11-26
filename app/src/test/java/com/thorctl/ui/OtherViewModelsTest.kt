package com.thorctl.ui

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.testing.FakeAppInfo
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakePairing
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.content
import com.thorctl.testing.sampleState
import com.thorctl.testing.sampleStats
import com.thorctl.ui.controller.ControllerViewModel
import com.thorctl.ui.game.GameViewModel
import com.thorctl.ui.gamepad.GamepadViewModel
import com.thorctl.ui.state.DaemonUiState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OtherViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()

    @Test
    fun controllerTargets() {
        val viewModel = ControllerViewModel(daemon)
        viewModel.setTarget(ControllerTarget.BOTTOM)
        assertEquals(
            listOf<DaemonCommand>(DaemonCommand.SetController(ControllerTarget.BOTTOM)),
            daemon.sent
        )
        daemon.push(sampleState.copy(controller = ControllerTarget.BOTTOM))
        assertEquals(ControllerTarget.BOTTOM, viewModel.uiState.value.content.target)
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
    }

    @Test
    fun gamepadMode() {
        val pairing = FakePairing()
        val viewModel = GamepadViewModel(daemon, pairing)
        viewModel.setOn(true)
        viewModel.setTransport(GamepadTransport.BLUETOOTH)
        viewModel.setScreensOff(false)
        viewModel.pairNewDevice()
        assertEquals(
            listOf(
                DaemonCommand.SetGamepad(true),
                DaemonCommand.SetGamepadTransport(GamepadTransport.BLUETOOTH),
                DaemonCommand.SetGamepadScreensOff(false)
            ),
            daemon.sent,
        )
        assertEquals(1, pairing.prompts)
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
    }

    @Test
    fun gameModeStatus() {
        val viewModel = GameViewModel(daemon, FakeAppInfo())
        assertEquals("Off", viewModel.uiState.value.content.status.first)
        daemon.push(sampleState.copy(game = GameState(enabled = true)))
        assertEquals("Waiting for a game", viewModel.uiState.value.content.status.first)
        daemon.push(
            sampleState.copy(
                game = GameState(
                    enabled = true,
                    packageName = "com.game",
                    boosted = true,
                    freedMb = 1536
                )
            )
        )
        daemon.pushStats(sampleStats)
        assertEquals(
            "Space Game" to "Boosting · 1.5 GB freed · 3.0 GB free now",
            viewModel.uiState.value.content.status
        )
        viewModel.updateOptions { it.copy(cpu = false) }
        assertEquals(
            DaemonCommand.SetGameOptions(GameState().options.copy(cpu = false)),
            daemon.sent.last()
        )
    }

    @Test
    fun gameOptionsWaitForTheDaemon() {
        val viewModel = GameViewModel(daemon, FakeAppInfo())
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
        viewModel.updateOptions { it.copy(cpu = false) }
        assertTrue(daemon.sent.isEmpty())
    }
}
