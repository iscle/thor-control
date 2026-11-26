package com.thorctl.testing

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was sent; tests push the states and readings the daemon would. Starts connected. */
class FakeDaemonRepository(initial: DaemonState = sampleState, initialStats: Stats = sampleStats) : DaemonRepository {
    private var state = MutableStateFlow(initial)
    private var stats = MutableStateFlow(initialStats)
    override val status = MutableStateFlow<DaemonStatus>(DaemonStatus.Connected(state, stats))
    override val events = MutableSharedFlow<DaemonEvent>(extraBufferCapacity = 16)
    override val gamepadReports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)

    val sent = mutableListOf<DaemonCommand>()
    var statsSubscribers = 0

    /** A new state on the current connection. */
    fun push(newState: DaemonState) {
        state.value = newState
    }

    fun pushStats(reading: Stats) {
        stats.value = reading
    }

    fun disconnect() {
        status.value = DaemonStatus.Disconnected
    }

    /** A new connection, starting from [newState] and [reading]. */
    fun connect(newState: DaemonState = sampleState, reading: Stats = sampleStats) {
        state = MutableStateFlow(newState)
        stats = MutableStateFlow(reading)
        status.value = DaemonStatus.Connected(state, stats)
    }

    override fun send(command: DaemonCommand) {
        sent += command
    }

    override fun subscribeStats(on: Boolean) {
        statsSubscribers += if (on) 1 else -1
    }
}
