package com.thorctl.daemon.ipc

/**
 * Told when the app's last connection to the daemon goes (the app died, was updated or
 * unregistered). Contribute one with
 * `@ContributesIntoSet(DaemonScope::class, binding = binding<AppConnectionListener>())`.
 */
fun interface AppConnectionListener {
    fun onAppDisconnected()
}
