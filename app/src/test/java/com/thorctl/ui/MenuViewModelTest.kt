package com.thorctl.ui

import app.cash.turbine.test
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.usecase.CycleFanProfileUseCase
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Screen
import com.thorctl.testing.FakeAppInfo
import com.thorctl.testing.FakeBattery
import com.thorctl.testing.FakeDaemonRepository
import com.thorctl.testing.FakeDisplayPreferences
import com.thorctl.testing.MainDispatcherRule
import com.thorctl.testing.content
import com.thorctl.testing.sampleState
import com.thorctl.testing.sampleStats
import com.thorctl.ui.menu.MenuViewModel
import com.thorctl.ui.menu.TileUi
import com.thorctl.ui.state.DaemonUiState
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val daemon = FakeDaemonRepository()
    private val preferences = FakeDisplayPreferences()
    private val battery = FakeBattery()
    private val viewModel by lazy {
        MenuViewModel(
            daemon,
            FakeAppInfo(),
            battery,
            CycleFanProfileUseCase(daemon),
            preferences
        )
    }

    @Test
    fun tilesDescribeTheDaemonsState() {
        daemon.push(
            sampleState.copy(
                fanOverride = "max",
                controller = ControllerTarget.TOP,
                leds = LedState(mode = LedMode.BREATHE),
                game = GameState(enabled = true, packageName = "com.game", boosted = false),
            ),
        )
        daemon.pushStats(sampleStats)
        with(viewModel.uiState.value.content) {
            assertEquals(TileUi("Max (game) · 50%", active = true), fan)
            assertEquals(50, fanPercent)
            assertEquals(TileUi("Top screen", active = true), controller)
            assertEquals(TileUi("Breathe", active = true), lights)
            assertEquals(TileUi("Space Game · paused", active = true), game)
            assertEquals(BrightnessCurve.toPosition(sampleState.brightnessTop), brightnessTop)
            assertEquals(sampleStats, stats)
        }
    }

    @Test
    fun gamepadModeTakesOverTheControllerTile() {
        daemon.push(sampleState.copy(gamepad = GamepadState(on = true, connected = true)))
        assertEquals(
            TileUi("Gamepad · connected", active = true),
            viewModel.uiState.value.content.controller
        )
    }

    @Test
    fun followsThePreferredUnit() {
        preferences.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        assertEquals(TemperatureUnit.FAHRENHEIT, viewModel.uiState.value.content.unit)
    }

    @Test
    fun followsEveryReading() {
        val hotter = sampleStats.copy(cpuTemp = 80f)
        daemon.pushStats(hotter)
        assertEquals(hotter, viewModel.uiState.value.content.stats)
    }

    @Test
    fun disconnectedAndBack() {
        daemon.disconnect()
        assertEquals(DaemonUiState.Disconnected, viewModel.uiState.value)
        // A new connection brings its own state and reading.
        val reading = sampleStats.copy(fanPwm = 255)
        daemon.connect(sampleState, reading)
        assertEquals(reading, viewModel.uiState.value.content.stats)
        assertEquals(100, viewModel.uiState.value.content.fanPercent)
    }

    @Test
    fun brightnessSlidersSendLinearValues() {
        viewModel.setBrightness(Screen.BOTTOM, 0.6f, final = true)
        assertEquals(
            listOf<DaemonCommand>(
                DaemonCommand.SetBrightness(
                    Screen.BOTTOM,
                    BrightnessCurve.toLinear(0.6f),
                    true
                )
            ), daemon.sent
        )
    }

    @Test
    fun tileIconsToggleAndCycle() {
        viewModel.cycleFan()
        viewModel.cycleController()
        viewModel.toggleLights()
        viewModel.toggleGameMode()
        viewModel.turnOffBottomScreen()
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
    fun nothingIsSentWhileDisconnected() {
        daemon.disconnect()
        viewModel.cycleFan()
        viewModel.cycleController()
        viewModel.toggleLights()
        viewModel.toggleGameMode()
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun batteryIsRead() = runTest {
        viewModel.battery.test {
            assertEquals(BatteryStatus(64, true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
