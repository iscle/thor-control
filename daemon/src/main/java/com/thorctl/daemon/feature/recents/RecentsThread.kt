package com.thorctl.daemon.feature.recents

import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The thread recents works on: commands, the Home animation, the controller and timers, one at a
 * time. State only this thread touches needs no lock.
 */
class RecentsThread(private val executor: ScheduledExecutorService) {
    /** Runs [block] on the thread; a failure is logged, never thrown. */
    fun run(block: () -> Unit) {
        executor.execute { logFailure(block) }
    }

    fun schedule(delayMs: Long, block: () -> Unit): ScheduledFuture<*> =
        executor.schedule(Runnable { logFailure(block) }, delayMs, TimeUnit.MILLISECONDS)

    /** Runs [block] after [delayMs], then [everyMs] after each run ends, until cancelled. */
    fun repeat(delayMs: Long, everyMs: Long, block: () -> Unit): ScheduledFuture<*> =
        executor.scheduleWithFixedDelay(Runnable { logFailure(block) }, delayMs, everyMs, TimeUnit.MILLISECONDS)

    /**
     * The top of every task on this thread, so it catches everything: escaping from [run] it would
     * reach Android's default uncaught exception handler, which ends the whole daemon, and from a
     * scheduled task it would vanish silently (and stop a repeat).
     */
    private fun logFailure(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Timber.w(e, "recents failed")
        }
    }
}
