package com.thorctl.daemon.feature.recents

import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** The thread recents works on: commands, the Home animation and timers, one at a time. */
class RecentsThread(private val executor: ScheduledExecutorService) {
    /** Runs [block] on the thread; a failure is logged, never thrown. */
    fun run(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Throwable) {
                Timber.w(e, "recents failed")
            }
        }
    }

    fun schedule(delayMs: Long, block: () -> Unit): ScheduledFuture<*> = executor.schedule(Runnable(block), delayMs, TimeUnit.MILLISECONDS)

    /** Runs [block] after [delayMs], then [everyMs] after each run ends, until cancelled. */
    fun repeat(delayMs: Long, everyMs: Long, block: () -> Unit): ScheduledFuture<*> =
        executor.scheduleWithFixedDelay(Runnable(block), delayMs, everyMs, TimeUnit.MILLISECONDS)
}
