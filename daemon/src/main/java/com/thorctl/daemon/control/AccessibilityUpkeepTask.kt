package com.thorctl.daemon.control

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Turns the app's accessibility service back on if the system dropped it (when the user wants it kept). */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class AccessibilityUpkeepTask(private val upkeep: SystemUpkeep, private val config: ConfigStore) : PeriodicTask {
    override val intervalMs = 30000L
    override val initialDelayMs = 2000L

    override fun run() {
        if (config.keepAccessibility) upkeep.ensureAccessibility()
    }
}
