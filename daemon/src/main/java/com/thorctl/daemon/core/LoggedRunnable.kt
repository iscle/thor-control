package com.thorctl.daemon.core

import timber.log.Timber

/**
 * [block] as a Runnable for an executor, logging whatever it throws as "[what] failed": an
 * executor would otherwise keep it in a Future nobody reads (or end its thread).
 */
fun loggedRunnable(what: String, block: () -> Unit) = Runnable {
    try {
        block()
    } catch (e: Throwable) {
        Timber.w(e, "$what failed")
    }
}
