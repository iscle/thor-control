package com.thorctl.daemon.feature

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Closes recents when nobody can see it any more: the screen went off or the app is gone. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class RecentsCloseTask(private val recents: Recents, private val link: ClientLink, private val powerService: PowerService) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        if (recents.isOpen && (!powerService.isInteractive() || !link.appConnected)) recents.close()
    }
}
