package com.thorctl.daemon.feature.gamepad

import java.util.concurrent.Future

/**
 * Tells a power button press from a hold while gamepad mode has the button: held for
 * [LONG_PRESS_MS] it is a hold ([onHold] fires while it is still down and its release does
 * nothing), let go sooner it is a press ([onPress] on release).
 *
 * Not thread-safe: the owner serializes [onKey] and the hold that [schedule] runs later (one lock
 * around both), so a release and a hold never cross.
 */
class PowerButton(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Future<*>,
    private val onPress: () -> Unit,
    private val onHold: () -> Unit,
) {
    private var holdFired = false
    private var hold: Future<*>? = null

    fun onKey(down: Boolean) {
        if (down) {
            holdFired = false
            hold?.cancel(false)
            hold = schedule(LONG_PRESS_MS) {
                holdFired = true
                onHold()
            }
            return
        }
        hold?.cancel(false)
        if (holdFired) return
        onPress()
    }

    companion object {
        /** Holding the power button this long opens the power menu. */
        const val LONG_PRESS_MS = 600L
    }
}
