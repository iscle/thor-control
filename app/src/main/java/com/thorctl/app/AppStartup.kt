package com.thorctl.app

import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.startup.ProcessObserver
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What runs for the whole life of the process: the daemon's events, handled by [dispatcher],
 * and the [observers] that follow the daemon's state. Everything else lives as long as its own
 * owner: the keyboard as long as the keyboard service, the menu's watchers while it is open.
 */
@Inject
class AppStartup(
    private val daemon: DaemonRepository,
    private val dispatcher: DaemonEventDispatcher,
    private val observers: Set<ProcessObserver>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { daemon.events.collect(dispatcher::dispatch) }
        observers.forEach { it.observe(scope) }
    }
}
