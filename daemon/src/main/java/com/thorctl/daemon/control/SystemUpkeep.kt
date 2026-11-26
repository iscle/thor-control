package com.thorctl.daemon.control

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.Proto
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
        for (svc in STOPPED_SERVICES) {
            if (sys.prop("init.svc.$svc") == "running") {
                sys.sh("stop $svc")
                Timber.i("stopped $svc again")
            }
        }
    }

    /** The overlay lives in an accessibility service so nothing can draw above or hide it. */
    fun ensureAccessibility() {
        if (!sys.exists("/data/data/${Proto.PACKAGE}")) return
        val cur = sys.sh("cmd settings get secure enabled_accessibility_services") ?: return
        val next = withService(cur) ?: return
        sys.sh("cmd settings put secure enabled_accessibility_services '$next'")
        sys.sh("cmd settings put secure accessibility_enabled 1")
        Timber.i("re-enabled accessibility service")
    }

    /** Disabling / removing the module in the Magisk app: time to exit, so service.sh restores everything. */
    fun moduleTurnedOff(): Boolean = sys.exists("$MODULE_DIR/disable") || sys.exists("$MODULE_DIR/remove")

    companion object {
        const val MODULE_DIR = "/data/adb/modules/thorctl"
        val STOPPED_SERVICES = listOf("cameraserver", "wifidisplayhalservice")

        /** The enabled_accessibility_services value with ours added, or null if it is already there. */
        fun withService(current: String): String? {
            if (current.split(':').contains(Proto.A11Y_SERVICE)) return null
            return if (current.isBlank() || current == "null") Proto.A11Y_SERVICE else "$current:${Proto.A11Y_SERVICE}"
        }
    }
}
