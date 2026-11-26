package com.thorctl.daemon.control

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Exits once the Magisk module is disabled or removed, so its changes go away without a reboot. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class ModuleRemovalTask(private val upkeep: SystemUpkeep, private val shutdown: DaemonShutdown) : PeriodicTask {
    override val intervalMs = 10000L
    override val initialDelayMs = 6000L

    override fun run() {
        if (upkeep.moduleTurnedOff()) shutdown.exit("module disabled or removed")
    }
}
