package com.thorctl.daemon.core

/**
 * Told when the device goes to sleep or wakes up. Contribute one with
 * `@ContributesIntoSet(DaemonScope::class, binding = binding<WakeListener>())`.
 */
fun interface WakeListener {
    fun onWakeChanged(awake: Boolean)
}
