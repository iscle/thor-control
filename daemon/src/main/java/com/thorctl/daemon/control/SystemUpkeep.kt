package com.thorctl.daemon.control

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/** Keeps the system the way the module set it up. */
@Inject
@SingleIn(DaemonScope::class)
class SystemUpkeep(private val sys: Sys) {
    /**
     * system-tweaks.sh stops services for hardware the Thor doesn't have. A zygote restart
     * (soft reboot) starts some of them again through its onrestart list; stop them again.
     */
    fun keepServicesStopped() {
        for (service in STOPPED_SERVICES) {
            if (sys.prop("init.svc.$service") == "running") {
                sys.sh("stop $service")
                Timber.i("stopped $service again")
            }
        }
    }

    /** Disabling / removing the module in the Magisk app: time to exit, so service.sh restores everything. */
    fun moduleTurnedOff(): Boolean = sys.exists("$MODULE_DIRECTORY/disable") || sys.exists("$MODULE_DIRECTORY/remove")

    companion object {
        const val MODULE_DIRECTORY = "/data/adb/modules/thorctl"
        val STOPPED_SERVICES = listOf("cameraserver", "wifidisplayhalservice")
    }
}
