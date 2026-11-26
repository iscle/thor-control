package com.thorctl.daemon.feature

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Keeps a fresh picture of the home screen for the recents and home animations. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class HomeCaptureTask(private val recents: Recents, private val powerService: PowerService) : PeriodicTask {
    override val intervalMs = 20000L
    override val initialDelayMs = 8000L

    override fun run() {
        if (!recents.isOpen) recents.maybeCaptureHome(powerService.isInteractive())
    }
}
