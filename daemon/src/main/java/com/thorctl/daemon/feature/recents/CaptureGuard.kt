package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.core.Clock
import dev.zacsweers.metro.Inject

/**
 * Says when a screen capture would picture our own overlays instead of what is beneath them: one
 * of our full-screen animations is (or may still be) on screen, or the HUD was shown moments ago.
 */
@Inject
class CaptureGuard(private val clock: Clock) {
    @Volatile
    private var busyUntil = 0L

    @Volatile
    private var lastHud = 0L

    /** One of our full-screen animations is (or may still be) on screen. */
    val overlayShowing: Boolean get() = clock.elapsedRealtime() < busyUntil

    /** Time left before an overlay counts as gone even if the app never said so; 0 when none shows. */
    val overlayRemainingMs: Long get() = (busyUntil - clock.elapsedRealtime()).coerceAtLeast(0)

    /** The HUD was shown within the last [HUD_MS]. */
    val hudShowing: Boolean get() = clock.elapsedRealtime() - lastHud < HUD_MS

    /** An animation of ours covers the screen for (at most) [durationMs]. */
    fun overlayFor(durationMs: Long) {
        busyUntil = clock.elapsedRealtime() + durationMs
    }

    /** The app removed its overlay. */
    fun overlayDone() {
        busyUntil = 0
    }

    fun noteHud() {
        lastHud = clock.elapsedRealtime()
    }

    companion object {
        const val HUD_MS = 3000L
    }
}
