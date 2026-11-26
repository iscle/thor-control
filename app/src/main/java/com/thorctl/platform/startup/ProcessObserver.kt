package com.thorctl.platform.startup

import kotlinx.coroutines.CoroutineScope

/**
 * Something that follows the daemon (or the system) for the whole life of the process, such as
 * the Bluetooth gamepad. Contributed into a set with
 * `@ContributesIntoSet(AppScope::class, binding = binding<ProcessObserver>())`; `AppStartup`
 * starts each one once, in [scope].
 */
fun interface ProcessObserver {
    fun observe(scope: CoroutineScope)
}
