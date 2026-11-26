package com.thorctl.domain.model

import com.thorctl.protocol.model.DaemonState

/** Runs [block] with the daemon's current state, only while connected. */
inline fun DaemonStatus.ifConnected(block: (DaemonState) -> Unit) {
    if (this is DaemonStatus.Connected) block(state.value)
}
