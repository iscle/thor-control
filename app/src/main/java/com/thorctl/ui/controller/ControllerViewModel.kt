package com.thorctl.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ControllerViewModel(private val daemon: DaemonRepository) : ViewModel() {
    val uiState: StateFlow<ControllerUiState> = daemon.state
        .map { ControllerUiState(it?.controller ?: ControllerTarget.FREE, it?.gamepad) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControllerUiState())

    fun setTarget(target: ControllerTarget) = daemon.send(DaemonCommand.SetController(target))
}
