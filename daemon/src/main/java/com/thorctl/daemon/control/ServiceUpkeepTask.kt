package com.thorctl.daemon.control

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Keeps the vendor services that fight the daemon stopped. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class ServiceUpkeepTask(private val upkeep: SystemUpkeep) : PeriodicTask {
    override val intervalMs = 30000L
    override val initialDelayMs = 4000L

    override fun run() {
        upkeep.keepServicesStopped()
    }
}
