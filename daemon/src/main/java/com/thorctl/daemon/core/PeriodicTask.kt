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

    /**
     * False (the default): runs on the thread all such tasks share, one at a time, so tasks never
     * race each other. True: on a thread of its own, for work that must not wait behind a slow
     * task (the fan).
     */
    val ownThread: Boolean get() = false

    fun run()
}
