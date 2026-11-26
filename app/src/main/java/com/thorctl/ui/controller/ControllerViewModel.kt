package com.thorctl.ui.controller

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.ui.state.stateUiStates
import com.thorctl.ui.state.currentUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
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
class ControllerViewModel(private val daemon: DaemonRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<ControllerContent>> = daemon.status
        .flatMapLatest { status -> status.stateUiStates { state -> ControllerContent(state.controller, state.gamepad) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, daemon.status.value.currentUiState { state -> ControllerContent(state.controller, state.gamepad) })

    fun setTarget(target: ControllerTarget) = daemon.send(DaemonCommand.SetController(target))
}
