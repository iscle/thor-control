package com.thorctl.testing

import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was sent; tests set the state the daemon "pushes". */
class FakeDaemonRepository(initial: DaemonState? = sampleState) : DaemonRepository {
    override val state = MutableStateFlow(initial)
    override val stats = MutableStateFlow<Stats?>(null)
    override val connected = MutableStateFlow(initial != null)
    override val events = MutableSharedFlow<DaemonEvent>(extraBufferCapacity = 16)
    override val gamepadReports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)

    val sent = mutableListOf<DaemonCommand>()
    var statsSubscribers = 0

    override fun send(command: DaemonCommand) {
        sent += command
    }

    override fun subscribeStats(on: Boolean) {
        statsSubscribers += if (on) 1 else -1
    }
}
