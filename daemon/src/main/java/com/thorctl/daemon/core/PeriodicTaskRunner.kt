package com.thorctl.daemon.core

import dev.zacsweers.metro.Inject
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** Schedules every contributed [PeriodicTask]. A failing run is logged and the task keeps its schedule. */
@Inject
class PeriodicTaskRunner(
    private val tasks: Set<PeriodicTask>,
    private val scheduler: ScheduledExecutorService,
) {
    fun start() {
        for (task in tasks) {
            scheduler.scheduleWithFixedDelay({ runSafely(task) }, task.initialDelayMs, task.intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun runSafely(task: PeriodicTask) {
        try {
            task.run()
        } catch (e: Exception) {
            // Task boundary: an exception would otherwise end the task's schedule silently.
            Timber.w(e, "${task.javaClass.simpleName} failed")
        }
    }
}
