package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.DaemonNotifier
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Picks up displays that come and go (the bottom panel can disappear while it is off). */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class DisplayRefreshTask(private val displays: DisplayController, private val notifier: DaemonNotifier) : PeriodicTask {
    override val intervalMs = 10000L

    override fun run() {
        val before = displays.bottomId
        displays.refresh()
        if (before != displays.bottomId) notifier.stateChanged()
    }
}
