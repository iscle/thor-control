package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats
import java.util.Collections

class FakeClientLink(override var appConnected: Boolean = true) : ClientLink {
    /** Everything broadcast, in order (thread-safe: events may come from worker threads). */
    val events: MutableList<DaemonEvent> = Collections.synchronizedList(mutableListOf())

    override val hasStatsSubscribers: Boolean = false

    override fun broadcast(event: DaemonEvent) {
        events += event
    }

    override fun broadcastStats(stats: Stats) {}
}
