package com.thorctl.daemon.ipc

import com.thorctl.daemon.core.Clock

/**
 * When to offer the app the binder again after offers failed. Each failed offer asks Android to
 * start the app, so while it can't be reached (being reinstalled, or crashing on start) the
 * offers space out: 2, 4, 8, 16 s, then every 30 s. A successful offer starts over.
 */
class OfferBackoff(private val clock: Clock) {
    private var failures = 0
    private var nextOfferAt = 0L

    @Synchronized
    fun ready(): Boolean = clock.elapsedRealtime() >= nextOfferAt

    @Synchronized
    fun succeeded() {
        failures = 0
        nextOfferAt = 0L
    }

    @Synchronized
    fun failed() {
        failures++
        val waitMs = (FIRST_WAIT_MS shl (failures - 1).coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_WAIT_MS)
        nextOfferAt = clock.elapsedRealtime() + waitMs
    }

    private companion object {
        const val FIRST_WAIT_MS = 2000L
        const val MAX_DOUBLINGS = 4
        const val MAX_WAIT_MS = 30_000L
    }
}
