package com.thorctl.ui.fan

import com.thorctl.protocol.model.DaemonState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.ui.state.uiStates
import com.thorctl.ui.state.currentUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.DisplayPreferencesRepository
import com.thorctl.domain.usecase.CreateCustomFanProfileUseCase
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.state.DaemonUiState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FanViewModel(
    private val daemon: DaemonRepository,
    private val preferences: DisplayPreferencesRepository,
    private val createCustomProfile: CreateCustomFanProfileUseCase,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<FanContent>> =
        combine(daemon.status, preferences.temperatureUnit) { status, unit -> status to unit }
            .flatMapLatest { (status, unit) -> status.uiStates { state, stats -> content(state, stats, unit) } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                daemon.status.value.currentUiState { state, stats -> content(state, stats, preferences.temperatureUnit.value) },
            )

    private fun content(state: DaemonState, stats: Stats, unit: TemperatureUnit) =
        FanContent(state.profiles, state.activeProfile, stats, unit)

    fun select(id: String) = daemon.send(DaemonCommand.SetFanProfile(id))

    /** A custom copy of [base], selected right away. */
    fun createCustom(base: FanProfile) {
        val state = uiState.value as? DaemonUiState.Connected ?: return
        createCustomProfile(state.content.profiles, base)
    }

    /** A dragged curve point was let go. */
    fun save(profile: FanProfile) = daemon.send(DaemonCommand.SaveFanProfile(profile, activate = false))

    fun delete(id: String) = daemon.send(DaemonCommand.DeleteFanProfile(id))

    fun setUnit(unit: TemperatureUnit) = preferences.setTemperatureUnit(unit)
}
