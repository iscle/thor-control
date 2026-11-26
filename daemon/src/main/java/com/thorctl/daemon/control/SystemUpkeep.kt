package com.thorctl.daemon.control

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Watches for the Magisk module being switched off. */
@Inject
@SingleIn(DaemonScope::class)
class SystemUpkeep(private val sys: Sys) {
    /** Disabling / removing the module in the Magisk app: time to exit, so service.sh restores everything. */
    fun moduleTurnedOff(): Boolean = sys.exists("$MODULE_DIRECTORY/disable") || sys.exists("$MODULE_DIRECTORY/remove")

    companion object {
        const val MODULE_DIRECTORY = "/data/adb/modules/thorctl"
    }
}
