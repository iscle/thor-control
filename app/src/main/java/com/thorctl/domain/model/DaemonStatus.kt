package com.thorctl.domain.model

import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the root daemon is reachable. It changes only when the connection does: what the daemon
 * reports while connected flows inside [Connected].
 */
sealed interface DaemonStatus {
    /** Not connected (starting up, restarting, or not installed): nothing can be changed. */
    data object Disconnected : DaemonStatus

    /**
     * Connected. [state] is the daemon's latest state, [stats] its latest telemetry reading (live
     * while someone subscribes; a new subscriber gets a fresh one at once). Both belong to this
     * connection, and both exist from its start.
     */
    class Connected(val state: StateFlow<DaemonState>, val stats: StateFlow<Stats>) : DaemonStatus
}
