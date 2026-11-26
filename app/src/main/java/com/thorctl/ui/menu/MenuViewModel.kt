package com.thorctl.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.domain.repository.BatteryRepository
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.DisplayPreferencesRepository
import com.thorctl.domain.usecase.CycleFanProfileUseCase
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Screen
import com.thorctl.protocol.model.Stats
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
    private fun build(state: DaemonState?, stats: Stats?, connected: Boolean, unit: TemperatureUnit) =
        MenuUiState(state, stats, connected, unit, apps.label(state?.game?.pkg.orEmpty()))

    val uiState: StateFlow<MenuUiState> =
        combine(daemon.state, daemon.stats, daemon.connected, preferences.temperatureUnit, ::build)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(daemon.state.value, daemon.stats.value, daemon.connected.value, preferences.temperatureUnit.value),
            )

    /** Polled: the battery's level hardly moves while the menu is open. */
    val battery: StateFlow<BatteryStatus> = flow {
        while (true) {
            emit(batteries.current())
            delay(BATTERY_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryStatus.UNKNOWN)

    /** [position] is the slider's (perceptual) position, 0..1. */
    fun setBrightness(screen: Screen, position: Float, final: Boolean) {
        daemon.send(DaemonCommand.SetBrightness(screen, BrightnessCurve.toLinear(position), final))
    }

    fun cycleFan() {
        uiState.value.state?.let { cycleFanProfile(it) }
    }

    fun cycleController() {
        uiState.value.state?.let { daemon.send(DaemonCommand.SetController(it.controller.next())) }
    }

    fun toggleLights() {
        uiState.value.state?.leds?.let { daemon.send(DaemonCommand.SetLeds(it.copy(enabled = !it.enabled))) }
    }

    fun toggleGameMode() {
        uiState.value.state?.game?.let { daemon.send(DaemonCommand.SetGameMode(!it.enabled)) }
    }

    fun turnOffBottomScreen() = daemon.send(DaemonCommand.SetBottomPower(false))

    private companion object {
        const val BATTERY_POLL_MS = 5_000L
    }
}
