package com.thorctl.daemon.control

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.ClientLink
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Forgets the on-screen keyboard when the app that shows it is gone. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class KeyboardResetTask(private val keyboard: KeyboardPresence, private val link: ClientLink) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        if (!link.appConnected) keyboard.visible = false
    }
}
