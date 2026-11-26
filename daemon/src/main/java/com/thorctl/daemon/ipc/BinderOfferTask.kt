package com.thorctl.daemon.ipc

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Hands the app the daemon's binder again whenever the app has no connection (it restarted, or was updated). */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class BinderOfferTask(private val publisher: BinderPublisher) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        publisher.offerIfNeeded()
    }
}
