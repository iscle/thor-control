package com.thorctl.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.AppInfoRepository
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GameState
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
class GameViewModel(private val daemon: DaemonRepository, private val apps: AppInfoRepository) : ViewModel() {
    val uiState: StateFlow<GameUiState> =
        combine(daemon.state, daemon.stats) { s, stats ->
            val g = s?.game ?: GameState()
            GameUiState(g, stats, apps.label(g.pkg), enabled = s != null)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GameUiState())

    fun setEnabled(on: Boolean) = daemon.send(DaemonCommand.SetGameMode(on))

    fun updateOptions(change: (GameOptions) -> GameOptions) =
        daemon.send(DaemonCommand.SetGameOptions(change(uiState.value.game.options)))

    companion object {
        /** The "Fan while gaming" choices: unchanged, Sport, Max. */
        val FAN_CHOICES = listOf("", "sport", "max")
    }
}
