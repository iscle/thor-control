package com.thorctl.ui.gamepad

import androidx.compose.material.icons.rounded.Bluetooth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.domain.repository.GamepadPairing
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
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
class GamepadViewModel(private val daemon: DaemonRepository, private val pairing: GamepadPairing) : ViewModel() {
    val uiState: StateFlow<GamepadUiState> = daemon.state
        .map { GamepadUiState(it?.gamepad ?: GamepadState(), enabled = it != null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GamepadUiState())

    fun setOn(on: Boolean) = daemon.send(DaemonCommand.SetGamepad(on))

    fun setTransport(t: GamepadTransport) = daemon.send(DaemonCommand.SetGamepadTransport(t))

    fun setScreensOff(on: Boolean) = daemon.send(DaemonCommand.SetGamepadScreensOff(on))

    /** Makes the Thor visible for a new Bluetooth host (the menu closes: it covers the prompt). */
    fun pairNewDevice() = pairing.makeDiscoverable()
}
