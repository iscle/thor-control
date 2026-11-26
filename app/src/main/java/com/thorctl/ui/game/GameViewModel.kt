package com.thorctl.ui.game

import com.thorctl.protocol.model.DaemonState
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.ui.state.uiStatesWithStats
import com.thorctl.ui.state.currentUiStateWithStats
import com.thorctl.domain.model.ifConnected
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.state.DaemonUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameOptions
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
class GameViewModel(private val daemon: DaemonRepository, private val apps: AppInfoRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<GameContent>> = daemon.status
        .flatMapLatest { status -> status.uiStatesWithStats(::content) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, daemon.status.value.currentUiStateWithStats(::content))

    private fun content(state: DaemonState, stats: Stats) = GameContent(state.game, stats, apps.label(state.game.packageName))

    fun setEnabled(on: Boolean) = daemon.send(DaemonCommand.SetGameMode(on))

    fun updateOptions(change: (GameOptions) -> GameOptions) =
        daemon.status.value.ifConnected { daemon.send(DaemonCommand.SetGameOptions(change(it.game.options))) }

    companion object {
        /** The "Fan while gaming" choices: unchanged, Sport, Max. */
        val FAN_CHOICES = listOf("", "sport", "max")
    }
}
