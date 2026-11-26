package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Recovers the bottom panel when it comes back blank while awake. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class BottomPanelHealTask(private val displays: DisplayController, private val powerService: PowerService) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        if (powerService.isInteractive()) displays.healBottomPanel()
    }
}
