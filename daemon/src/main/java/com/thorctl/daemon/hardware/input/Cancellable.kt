package com.thorctl.daemon.hardware.input

/** A delayed task (see [InputWorker.schedule]) that can still be called off. */
fun interface Cancellable {
    /** Stops the task if it hasn't started yet; a running one is left to finish. */
    fun cancel()
}
