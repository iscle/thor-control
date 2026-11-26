package com.thorctl.ui.fan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.DisplayPreferencesRepository
import com.thorctl.domain.usecase.CreateCustomFanProfileUseCase
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FanViewModel(
    private val daemon: DaemonRepository,
    private val preferences: DisplayPreferencesRepository,
    private val createCustomProfile: CreateCustomFanProfileUseCase,
) : ViewModel() {
    val uiState: StateFlow<FanUiState> =
        combine(daemon.state, daemon.stats, preferences.temperatureUnit) { state, stats, unit ->
            FanUiState(
                profiles = state?.profiles ?: FanProfile.BUILTINS,
                active = state?.activeProfile ?: FanProfile.BUILTINS.first(),
                stats = stats,
                unit = unit,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FanUiState())

    fun select(id: String) = daemon.send(DaemonCommand.SetFanProfile(id))

    /** A custom copy of [base], selected right away. */
    fun createCustom(base: FanProfile) {
        createCustomProfile(uiState.value.profiles, base)
    }

    /** A dragged curve point was let go. */
    fun save(profile: FanProfile) = daemon.send(DaemonCommand.SaveFanProfile(profile, activate = false))

    fun delete(id: String) = daemon.send(DaemonCommand.DeleteFanProfile(id))

    fun setUnit(unit: TemperatureUnit) = preferences.setTemperatureUnit(unit)
}
