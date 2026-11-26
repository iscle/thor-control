package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats
import java.util.Collections

class FakeClientLink(override var appConnected: Boolean = true) : ClientLink {
    override var hasStatsSubscribers = false

    /** Every broadcast event, in order (thread-safe: events may come from worker threads). */
    val events: MutableList<DaemonEvent> = Collections.synchronizedList(mutableListOf())
    val stats: MutableList<Stats> = Collections.synchronizedList(mutableListOf())

    override fun broadcast(event: DaemonEvent) {
        events += event
    }

    override fun broadcastStats(stats: Stats) {
        this.stats += stats
    }
}
