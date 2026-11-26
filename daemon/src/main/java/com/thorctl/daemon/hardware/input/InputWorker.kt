package com.thorctl.daemon.hardware.input

/**
 * The single thread that runs input reactions (listener calls, long-press and grab timeouts), so
 * the evdev readers never block on them and reactions never run concurrently.
 */
interface InputWorker {
    /** Runs [block] on the worker; a failure is logged, never thrown. */
    fun post(block: () -> Unit)

    /** Runs [block] on the worker after [delayMs] unless cancelled first. A failure there is swallowed, not logged. */
    fun schedule(delayMs: Long, block: () -> Unit): Cancellable
}
