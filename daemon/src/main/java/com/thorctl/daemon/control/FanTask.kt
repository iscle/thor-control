package com.thorctl.daemon.control

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Follows the fan curve (or game mode's override) with the current temperature. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class FanTask(private val actions: DaemonActions) : PeriodicTask {
    override val intervalMs = 1000L
    override val ownThread = true

    override fun run() {
        actions.fanTick()
    }
}
