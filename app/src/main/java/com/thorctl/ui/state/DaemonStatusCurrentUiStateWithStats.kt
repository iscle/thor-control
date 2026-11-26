package com.thorctl.ui.state

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats

/** The screen's state right now (a StateFlow's first value), with the latest reading. */
fun <T> DaemonStatus.currentUiStateWithStats(content: (DaemonState, Stats) -> T): DaemonUiState<T> =
    when (this) {
        DaemonStatus.Disconnected -> DaemonUiState.Disconnected
        is DaemonStatus.Connected -> DaemonUiState.Connected(content(state.value, stats.value))
    }
