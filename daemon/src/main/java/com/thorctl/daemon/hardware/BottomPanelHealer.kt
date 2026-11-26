package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.DisplayService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Bottom panel watchdog. LineageOS sometimes boots with the bottom panel dark: the framework and
 * SurfaceFlinger say the display is ON, but the kernel DSI connector never got enabled. A
 * framework power cycle (off -> back to policy) makes HWC commit the panel again.
 *
 * A panel has to look stuck on two checks in a row; heals are at least [HEAL_INTERVAL_MS] apart
 * and at most [MAX_HEALS_PER_WAKE] per wake.
 */
@Inject
@SingleIn(DaemonScope::class)
class BottomPanelHealer(
    private val sys: Sys,
    private val clock: Clock,
    private val displayService: DisplayService,
) {
    /** The bottom panel's DRM connector: the last of the (two) DSI connectors. */
    private val connector: String? = sys.list(DRM)
        .filter { it.substringAfterLast('/').matches(DSI_CONNECTOR) }
        .sortedBy { it.substringAfterLast('/') }
        .takeIf { it.size >= 2 }
        ?.last()

    private var stuckChecks = 0
    private var lastHeal = 0L
    private var healsThisWake = 0

    fun onWake() {
        stuckChecks = 0
        healsThisWake = 0
    }

    /** Called every couple of seconds while the device is awake. */
    fun heal(bottomId: Int, bottomForcedOff: Boolean) {
        val connector = connector ?: return
        if (bottomId < 0 || bottomForcedOff) {
            stuckChecks = 0
            return
        }
        val frameworkOn = try {
            displayService.displays().firstOrNull { it.id == bottomId }?.state == DisplayController.STATE_ON
        } catch (_: Throwable) {
            false
        }
        val panelEnabled = sys.read("$connector/enabled") == "enabled"
        if (!frameworkOn || panelEnabled) {
            stuckChecks = 0
            return
        }
        if (++stuckChecks < 2) return
        val now = clock.elapsedRealtime()
        if (now - lastHeal < HEAL_INTERVAL_MS || healsThisWake >= MAX_HEALS_PER_WAKE) return
        lastHeal = now
        healsThisWake++
        stuckChecks = 0
        Timber.i("bottom panel stuck (display ON, ${connector.substringAfterLast('/')} not enabled); power-cycling it")
        try {
            displayService.requestDisplayPower(bottomId, DisplayController.STATE_OFF)
            Thread.sleep(500)
            displayService.requestDisplayPower(bottomId, DisplayController.STATE_UNKNOWN)
        } catch (e: Throwable) {
            Timber.w(e, "bottom panel power cycle failed")
        }
    }

    private companion object {
        const val DRM = "/sys/class/drm"
        const val HEAL_INTERVAL_MS = 15_000L
        const val MAX_HEALS_PER_WAKE = 3
        val DSI_CONNECTOR = Regex("card\\d+-DSI-\\d+")
    }
}
