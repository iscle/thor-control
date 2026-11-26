package com.thorctl.ui.gamepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.GamepadPairing
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.ui.state.DaemonUiState
import com.thorctl.ui.state.currentUiState
import com.thorctl.ui.state.stateUiStates
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class GamepadViewModel(private val daemon: DaemonRepository, private val pairing: GamepadPairing) :
    ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<GamepadContent>> = daemon.status
        .flatMapLatest { status -> status.stateUiStates { state -> GamepadContent(state.gamepad) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            daemon.status.value.currentUiState { state -> GamepadContent(state.gamepad) })

    fun setOn(on: Boolean) = daemon.send(DaemonCommand.SetGamepad(on))

    fun setTransport(transport: GamepadTransport) =
        daemon.send(DaemonCommand.SetGamepadTransport(transport))

    fun setScreensOff(on: Boolean) = daemon.send(DaemonCommand.SetGamepadScreensOff(on))

    /** Makes the Thor visible for a new Bluetooth host (the menu closes: it covers the prompt). */
    fun pairNewDevice() = pairing.makeDiscoverable()
}
