package com.thorctl.domain.repository

/** Wall-clock time, injectable for tests. */
fun interface Clock {
    fun nowMillis(): Long
}
