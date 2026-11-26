package com.thorctl.ui

import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.usecase.CycleFanProfileUseCase
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Screen
import com.thorctl.testing.FakeAppInfo
import com.thorctl.testing.FakeBattery
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakeDisplayPreferences
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.sampleState
import com.thorctl.testing.sampleStats
import com.thorctl.ui.menu.MenuViewModel
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()
    private val prefs = FakeDisplayPreferences()
    private val battery = FakeBattery()
    private fun vm() = MenuViewModel(daemon, FakeAppInfo(), battery, CycleFanProfileUseCase(daemon), prefs)

    @Test
    fun followsTheDaemonAndThePreferences() {
        val vm = vm()
        assertTrue(vm.uiState.value.enabled)
        daemon.stats.value = sampleStats
        prefs.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        daemon.state.value = sampleState.copy(game = GameState(enabled = true, pkg = "com.game"))
        with(vm.uiState.value) {
            assertEquals(sampleStats, stats)
            assertEquals(TemperatureUnit.FAHRENHEIT, unit)
            assertEquals("Space Game", gameLabel)
        }
        daemon.connected.value = false
        assertFalse(vm.uiState.value.enabled)
    }

    @Test
    fun brightnessSlidersSendLinearValues() {
        vm().setBrightness(Screen.BOTTOM, 0.6f, final = true)
        assertEquals(listOf<DaemonCommand>(DaemonCommand.SetBrightness(Screen.BOTTOM, BrightnessCurve.toLinear(0.6f), true)), daemon.sent)
    }

    @Test
    fun tileIconsToggleAndCycle() {
        val vm = vm()
        vm.cycleFan()
        vm.cycleController()
        vm.toggleLights()
        vm.toggleGameMode()
        vm.turnOffBottomScreen()
        assertEquals(
            listOf(
                DaemonCommand.SetFanProfile("sport"),
                DaemonCommand.SetController(ControllerTarget.TOP),
                DaemonCommand.SetLeds(LedState(enabled = false)),
                DaemonCommand.SetGameMode(true),
                DaemonCommand.SetBottomPower(false),
            ),
            daemon.sent,
        )
    }

    @Test
    fun nothingIsSentWithoutAState() {
        daemon.state.value = null
        val vm = vm()
        vm.cycleFan()
        vm.cycleController()
        vm.toggleLights()
        vm.toggleGameMode()
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun batteryIsRead() = runTest {
        vm().battery.test {
            // Read as soon as someone looks.
            assertEquals(BatteryStatus(64, true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
