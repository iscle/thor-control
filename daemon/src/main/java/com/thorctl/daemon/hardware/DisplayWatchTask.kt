package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/**
 * Keeps the display events coming: a system_server restart drops the registration (the daemon
 * outlives it), and this registers again. A local check otherwise, no call to the system.
 */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class DisplayWatchTask(private val watcher: DisplayWatcher) : PeriodicTask {
    override val intervalMs = 10_000L

    override fun run() = watcher.watch()
}
