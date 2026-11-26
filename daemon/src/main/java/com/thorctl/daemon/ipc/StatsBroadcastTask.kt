package com.thorctl.daemon.ipc

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.StatsCollector
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Sends live stats to the clients that subscribed to them. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class StatsBroadcastTask(private val link: ClientLink, private val stats: StatsCollector) : PeriodicTask {
    override val intervalMs = 1000L

    override fun run() {
        if (link.hasStatsSubscribers) link.broadcastStats(stats.collect())
    }
}
