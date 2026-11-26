package com.thorctl.daemon.control

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Thor Keyboard is on screen (the app says so): L1 / R1 act as its shift key. */
@Inject
@SingleIn(DaemonScope::class)
class KeyboardPresence {
    @Volatile
    var visible = false
}
