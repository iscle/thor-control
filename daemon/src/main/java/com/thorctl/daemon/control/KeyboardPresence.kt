package com.thorctl.daemon.control

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.AppConnectionListener
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Thor Keyboard is on screen (the app says so): L1 / R1 act as its shift key. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesIntoSet(DaemonScope::class, binding = binding<AppConnectionListener>())
class KeyboardPresence : AppConnectionListener {
    @Volatile
    var visible = false

    /** No app, no keyboard on screen. */
    override fun onAppDisconnected() {
        visible = false
    }
}
