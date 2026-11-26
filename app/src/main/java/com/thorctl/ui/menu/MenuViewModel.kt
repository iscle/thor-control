package com.thorctl.ui.menu

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
import com.thorctl.ui.state.currentUiStateWithStats
import com.thorctl.ui.state.uiStatesWithStats
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
            .flatMapLatest { (status, unit) ->
                status.uiStatesWithStats { state, stats ->
                    content(
                        state,
                        stats,
                        unit
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                daemon.status.value.currentUiStateWithStats { state, stats ->
                    content(
                        state,
                        stats,
                        preferences.temperatureUnit.value
                    )
                },
            )

    /** Polled: the battery's level hardly moves while the menu is open. */
    val battery: StateFlow<BatteryStatus> = flow {
        while (true) {
            emit(batteries.current())
            delay(BATTERY_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryStatus.UNKNOWN)

    private fun content(state: DaemonState, stats: Stats, unit: TemperatureUnit): MenuContent {
        val fanPercent = stats.fanPercent
        val profile = state.runningProfile
        val gameLabel = apps.label(state.game.packageName)
        return MenuContent(
            brightnessTop = BrightnessCurve.toPosition(state.brightnessTop),
            brightnessBottom = BrightnessCurve.toPosition(state.brightnessBottom),
            fan = TileUi(
                secondary = if (state.fanOverride.isNotEmpty()) "${profile.name} (game) · $fanPercent%" else "${profile.name} · $fanPercent%",
                active = !profile.isAuto,
            ),
            fanPercent = fanPercent,
            controller = TileUi(
                secondary = when {
                    !state.gamepad.on -> Format.controller(state.controller)
                    state.gamepad.connected -> "Gamepad · connected"
                    else -> "Gamepad · waiting"
                },
                active = state.controller != ControllerTarget.FREE || state.gamepad.on,
            ),
            lights = TileUi(
                secondary = if (state.leds.enabled) state.leds.mode.wire.replaceFirstChar { it.uppercase() } else "Off",
                active = state.leds.enabled,
            ),
            leds = state.leds,
            game = TileUi(
                secondary = when {
                    !state.game.enabled -> "Off"
                    state.game.packageName.isEmpty() -> "Waiting for a game"
                    !state.game.boosted -> "$gameLabel · paused"
                    else -> gameLabel
                },
                active = state.game.enabled,
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

    fun cycleController() =
        daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetController(it.controller.next())) }

    fun toggleLights() =
        daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetLeds(it.leds.copy(enabled = !it.leds.enabled))) }

    fun toggleGameMode() =
        daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetGameMode(!it.game.enabled)) }

    fun turnOffBottomScreen() = daemon.send(DaemonCommand.SetBottomPower(false))

    private companion object {
        const val BATTERY_POLL_MS = 5_000L
    }
}
