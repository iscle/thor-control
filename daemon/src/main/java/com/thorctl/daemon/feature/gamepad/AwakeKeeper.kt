package com.thorctl.daemon.feature.gamepad

import android.view.KeyEvent
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Keeps Android and the SoC awake while gamepad mode is on. The controller's driver cuts the
 * controller's power when both panels are off, and Android's sleep turns both off, so user
 * activity is reported on every panel (no screen timeout) and a kernel wakelock keeps the SoC out
 * of suspend besides.
 */
@Inject
@SingleIn(DaemonScope::class)
class AwakeKeeper(
    private val displays: DisplayController,
    private val powerService: PowerService,
    private val inputService: InputService,
    private val sys: Sys,
) {
    /** Takes (or refreshes) the wakelock and reports user activity on every panel. */
    fun keepAwake() {
        // Timed, so it lapses by itself if the daemon dies; refreshed on every keepAwake().
        sys.write(WAKE_LOCK, "$WAKELOCK $WAKELOCK_NS")
        for (id in intArrayOf(displays.topId, displays.bottomId)) {
            if (id < 0) continue
            try {
                powerService.userActivity(id)
            } catch (e: ReflectiveOperationException) {
                Timber.w(e, "gamepad: user activity on display $id failed")
            }
        }
    }

    /** Drops the wakelock. */
    fun release() {
        sys.write(WAKE_UNLOCK, WAKELOCK)
    }

    fun isInteractive(): Boolean = powerService.isInteractive()

    /** Android went to sleep anyway (not the lid): wake it. */
    fun wakeUp() {
        try {
            inputService.injectKey(KeyEvent.KEYCODE_WAKEUP, displays.topId)
        } catch (e: ReflectiveOperationException) {
            Timber.w(e, "gamepad: waking Android failed")
        }
    }

    companion object {
        const val WAKE_LOCK = "/sys/power/wake_lock"
        const val WAKE_UNLOCK = "/sys/power/wake_unlock"
        const val WAKELOCK = "thorctl_gamepad"
        const val WAKELOCK_NS = 60_000_000_000L
    }
}
