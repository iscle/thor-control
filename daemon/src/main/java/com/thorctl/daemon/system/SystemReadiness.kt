package com.thorctl.daemon.system

import android.content.Context
import android.os.ServiceManager
import dev.zacsweers.metro.Inject

/** Whether the system services the daemon starts with are up (early in boot they may not be). */
@Inject
class SystemReadiness {
    fun servicesReady(): Boolean = REQUIRED.all { ServiceManager.getService(it) != null }

    private companion object {
        val REQUIRED = listOf(Context.DISPLAY_SERVICE, Context.INPUT_SERVICE, Context.POWER_SERVICE, HiddenServiceNames.ACTIVITY_TASK_SERVICE)
    }
}
