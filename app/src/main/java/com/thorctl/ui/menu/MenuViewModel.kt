package com.thorctl.ui.menu

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.ui.state.uiStates
import com.thorctl.ui.state.currentUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.model.ifConnected
import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.domain.repository.BatteryRepository
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.DisplayPreferencesRepository
import com.thorctl.domain.usecase.CycleFanProfileUseCase
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Screen
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.format.Format
import com.thorctl.ui.state.DaemonUiState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The menu's main page: brightness, the four tiles and the telemetry. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MenuViewModel(
    private val daemon: DaemonRepository,
    private val apps: AppInfoRepository,
    private val batteries: BatteryRepository,
    private val cycleFanProfile: CycleFanProfileUseCase,
    preferences: DisplayPreferencesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<MenuContent>> =
        combine(daemon.status, preferences.temperatureUnit) { status, unit -> status to unit }
            .flatMapLatest { (status, unit) -> status.uiStates { state, stats -> content(state, stats, unit) } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                daemon.status.value.currentUiState { state, stats -> content(state, stats, preferences.temperatureUnit.value) },
            )

    /** Polled: the battery's level hardly moves while the menu is open. */
    val battery: StateFlow<BatteryStatus> = flow {
        while (true) {
            emit(batteries.current())
            delay(BATTERY_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryStatus.UNKNOWN)

    private fun content(s: DaemonState, stats: Stats, unit: TemperatureUnit): MenuContent {
        val fanPct = stats.fanPercent
        val profile = s.runningProfile
        val gameLabel = apps.label(s.game.packageName)
        return MenuContent(
            brightnessTop = BrightnessCurve.toPosition(s.brightnessTop),
            brightnessBottom = BrightnessCurve.toPosition(s.brightnessBottom),
            fan = TileUi(
                secondary = if (s.fanOverride.isNotEmpty()) "${profile.name} (game) · $fanPct%" else "${profile.name} · $fanPct%",
                active = !profile.isAuto,
            ),
            fanPercent = fanPct,
            controller = TileUi(
                secondary = when {
                    !s.gamepad.on -> Format.controller(s.controller)
                    s.gamepad.connected -> "Gamepad · connected"
                    else -> "Gamepad · waiting"
                },
                active = s.controller != ControllerTarget.FREE || s.gamepad.on,
            ),
            lights = TileUi(
                secondary = if (s.leds.enabled) s.leds.mode.wire.replaceFirstChar { it.uppercase() } else "Off",
                active = s.leds.enabled,
            ),
            leds = s.leds,
            game = TileUi(
                secondary = when {
                    !s.game.enabled -> "Off"
                    s.game.packageName.isEmpty() -> "Waiting for a game"
                    !s.game.boosted -> "$gameLabel · paused"
                    else -> gameLabel
                },
                active = s.game.enabled,
            ),
            stats = stats,
            unit = unit,
        )
    }

    /** [position] is the slider's (perceptual) position, 0..1. */
    fun setBrightness(screen: Screen, position: Float, final: Boolean) {
        daemon.send(DaemonCommand.SetBrightness(screen, BrightnessCurve.toLinear(position), final))
    }

    fun cycleFan() = daemon.status.value.ifConnected { cycleFanProfile(it) }

    fun cycleController() = daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetController(it.controller.next())) }

    fun toggleLights() = daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetLeds(it.leds.copy(enabled = !it.leds.enabled))) }

    fun toggleGameMode() = daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetGameMode(!it.game.enabled)) }

    fun turnOffBottomScreen() = daemon.send(DaemonCommand.SetBottomPower(false))

    private companion object {
        const val BATTERY_POLL_MS = 5_000L
    }
}
