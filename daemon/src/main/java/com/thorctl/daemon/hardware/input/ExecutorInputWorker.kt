package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** [InputWorker] on a single-thread scheduled executor ("input-worker"). */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class ExecutorInputWorker : InputWorker {
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "input-worker") }

    override fun post(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Throwable) {
                Timber.w(e, "input handler failed")
            }
        }
    }

    override fun schedule(delayMs: Long, block: () -> Unit): Cancellable {
        val future = executor.schedule(Runnable { block() }, delayMs, TimeUnit.MILLISECONDS)
        return Cancellable { future.cancel(false) }
    }
}
