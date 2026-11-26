package com.thorctl.daemon.core

/** Monotonic time, injectable so timing logic can be tested. */
interface Clock {
    /** Milliseconds since boot, including deep sleep. */
    fun elapsedRealtime(): Long

    /** Milliseconds since boot, not counting deep sleep. */
    fun uptimeMillis(): Long
}
