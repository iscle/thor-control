package com.thorctl.daemon.core

/**
 * Work the daemon repeats for as long as it runs. To add some, implement this and contribute it
 * with `@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())`; the
 * [PeriodicTaskRunner] schedules every contributed task, each on its own cadence.
 */
interface PeriodicTask {
    /** Time between the end of one run and the start of the next. */
    val intervalMs: Long

    /** Time before the first run; tasks sharing an interval use it to stay out of each other's way. */
    val initialDelayMs: Long get() = intervalMs

    fun run()
}
