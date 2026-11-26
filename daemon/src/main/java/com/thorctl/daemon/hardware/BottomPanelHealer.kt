package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.core.loggedRunnable
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.SystemServiceException
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Bottom panel watchdog. LineageOS sometimes boots with the bottom panel dark: the framework and
 * SurfaceFlinger say the display is ON, but the kernel DSI connector never got enabled. A
 * framework power cycle (off -> back to policy) makes HWC commit the panel again.
 *
 * A panel has to look stuck on two checks in a row; heals are at least [HEAL_INTERVAL_MS] apart
 * and at most [MAX_HEALS_PER_WAKE] per wake. The panel stays off [POWER_CYCLE_OFF_MS] before its
 * power is handed back, on [scheduler] rather than by blocking the caller.
 */
@Inject
@SingleIn(DaemonScope::class)
class BottomPanelHealer(
    private val sys: Sys,
    private val clock: Clock,
    private val displayService: DisplayService,
    private val scheduler: ScheduledExecutorService,
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
        } catch (_: SystemServiceException) {
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
        } catch (e: SystemServiceException) {
            Timber.w(e, "bottom panel power cycle failed")
            return
        }
        scheduler.schedule(loggedRunnable("bottom panel hand-back") { handBack(bottomId) }, POWER_CYCLE_OFF_MS, TimeUnit.MILLISECONDS)
    }

    /** Second half of the power cycle: the panel's power goes back to the framework's policy. */
    private fun handBack(bottomId: Int) {
        try {
            displayService.requestDisplayPower(bottomId, DisplayController.STATE_UNKNOWN)
        } catch (e: SystemServiceException) {
            Timber.w(e, "bottom panel power cycle failed")
        }
    }

    private companion object {
        const val DRM = "/sys/class/drm"
        const val HEAL_INTERVAL_MS = 15_000L
        const val MAX_HEALS_PER_WAKE = 3
        const val POWER_CYCLE_OFF_MS = 500L
        val DSI_CONNECTOR = Regex("card\\d+-DSI-\\d+")
    }
}
