package com.thorctl.ui.state

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.model.DaemonState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A screen's states for this connection: [content] again whenever the daemon's state changes (telemetry doesn't count). */
fun <T> DaemonStatus.stateUiStates(content: (DaemonState) -> T): Flow<DaemonUiState<T>> =
    when (this) {
        DaemonStatus.Disconnected -> flowOf(DaemonUiState.Disconnected)
        is DaemonStatus.Connected -> state.map { daemonState ->
            DaemonUiState.Connected(
                content(
                    daemonState
                )
            )
        }
    }
