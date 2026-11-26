package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats

class FakeClientLink(override var appConnected: Boolean = true) : ClientLink {
    override var hasStatsSubscribers = false

    /** Every broadcast event, in order. */
    val events = mutableListOf<DaemonEvent>()
    val stats = mutableListOf<Stats>()

    override fun broadcast(event: DaemonEvent) {
        events += event
    }

    override fun broadcastStats(stats: Stats) {
        this.stats += stats
    }
}
