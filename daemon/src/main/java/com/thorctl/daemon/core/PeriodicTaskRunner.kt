package com.thorctl.daemon.core

import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Schedules every contributed [PeriodicTask]: the shared ones one at a time on a "periodic"
 * thread, the others on threads of their own. A failing run is logged and the task keeps its
 * schedule.
 */
@Inject
class PeriodicTaskRunner(private val tasks: Set<PeriodicTask>) {
    fun start() {
        val shared = Executors.newSingleThreadScheduledExecutor { Thread(it, "periodic") }
        for (task in tasks) {
            val executor = if (task.ownThread) Executors.newSingleThreadScheduledExecutor { Thread(it, task.javaClass.simpleName) } else shared
            executor.schedule(task)
        }
    }

    private fun ScheduledExecutorService.schedule(task: PeriodicTask) =
        scheduleWithFixedDelay({ runSafely(task) }, task.initialDelayMs, task.intervalMs, TimeUnit.MILLISECONDS)

    private fun runSafely(task: PeriodicTask) {
        try {
            task.run()
        } catch (e: Throwable) {
            // Task boundary: anything escaping would end the task's schedule, silently.
            Timber.w(e, "${task.javaClass.simpleName} failed")
        }
    }
}
