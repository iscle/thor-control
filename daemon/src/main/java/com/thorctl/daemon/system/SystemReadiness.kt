package com.thorctl.daemon.system

import dev.zacsweers.metro.Inject

/** Whether the system services the daemon starts with are up (early in boot they may not be). */
@Inject
class SystemReadiness {
    fun servicesReady(): Boolean = REQUIRED.all { SystemServiceProxy.lookup(it) != null }

    private companion object {
        val REQUIRED = listOf("display", "input", "power", "activity_task")
    }
}
