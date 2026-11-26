package com.thorctl.ui.state

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.model.DaemonState

/** The screen's state right now (a StateFlow's first value), from the daemon's state only. */
fun <T> DaemonStatus.currentUiState(content: (DaemonState) -> T): DaemonUiState<T> = when (this) {
    DaemonStatus.Disconnected -> DaemonUiState.Disconnected
    is DaemonStatus.Connected -> DaemonUiState.Connected(content(state.value))
}
