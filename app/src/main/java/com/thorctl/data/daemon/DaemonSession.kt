package com.thorctl.data.daemon

import android.os.IBinder
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.ipc.IThorDaemon
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * One connection to the daemon, from registering until it ends. The daemon answers a registration
 * with its state and a telemetry reading; the session is connected once both are in, so neither is
 * ever missing on screen. After that, newer ones update the connection's flows.
 */
class DaemonSession(val daemon: IThorDaemon, val binder: IBinder) {
    /** Registered with the daemon (its first state may still be on the way). */
    @Volatile
    var registered = false

    private var firstState: DaemonState? = null
    private var firstStats: Stats? = null
    /** Written under the session's lock on binder threads, read by [updateState] from any thread. */
    @Volatile
    private var state: MutableStateFlow<DaemonState>? = null

    @Volatile
    private var stats: MutableStateFlow<Stats>? = null

    /** Takes a state from the daemon; returns the connection when this completes it. */
    @Synchronized
    fun onState(newState: DaemonState): DaemonStatus.Connected? {
        val current = state
        if (current != null) {
            current.value = newState
            return null
        }
        firstState = newState
        return connectWhenComplete()
    }

    /** Takes a reading from the daemon; returns the connection when this completes it. */
    @Synchronized
    fun onStats(reading: Stats): DaemonStatus.Connected? {
        val current = stats
        if (current != null) {
            current.value = reading
            return null
        }
        firstStats = reading
        return connectWhenComplete()
    }

    /** Changes the connection's state ahead of the daemon (nothing before it is connected). */
    fun updateState(transform: (DaemonState) -> DaemonState) {
        state?.update(transform)
    }

    private fun connectWhenComplete(): DaemonStatus.Connected? {
        val initialState = firstState ?: return null
        val initialStats = firstStats ?: return null
        val stateFlow = MutableStateFlow(initialState)
        val statsFlow = MutableStateFlow(initialStats)
        state = stateFlow
        stats = statsFlow
        firstState = null
        firstStats = null
        return DaemonStatus.Connected(stateFlow, statsFlow)
    }
}
