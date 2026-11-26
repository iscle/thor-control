package com.thorctl.ui.state

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** A screen's states for this connection: [content] again whenever the daemon's state or telemetry changes. */
fun <T> DaemonStatus.uiStates(content: (DaemonState, Stats) -> T): Flow<DaemonUiState<T>> = when (this) {
    DaemonStatus.Disconnected -> flowOf(DaemonUiState.Disconnected)
    is DaemonStatus.Connected -> combine(state, stats) { daemonState, reading -> DaemonUiState.Connected(content(daemonState, reading)) }
}
