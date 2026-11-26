package com.thorctl.daemon.feature.gamepad

import java.util.concurrent.Future

/**
 * Tells a power button press from a hold while gamepad mode has the button: held for
 * [LONG_PRESS_MS] it is a hold ([onHold] fires while it is still down and its release does
 * nothing), let go sooner it is a press ([onPress] on release).
 *
 * Not thread-safe: the owner serializes [onKey], [cancel] and the hold that [schedule] runs later
 * (one lock around all three). A hold timer that already started but waits for that lock while
 * the button is released or [cancel] runs finds its press gone and does nothing.
 */
class PowerButton(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Future<*>,
    private val onPress: () -> Unit,
    private val onHold: () -> Unit,
) {
    /** Between a down and its release. */
    private var down = false

    /** The current press already did its thing (a hold) or was cancelled: its release does nothing. */
    private var spent = false

    private var hold: Future<*>? = null

    /** Bumped on every key and [cancel]; a hold timer only fires for the press that scheduled it. */
    private var press = 0

    fun onKey(down: Boolean) {
        stopHoldTimer()
        this.down = down
        if (down) {
            spent = false
            val thisPress = press
            hold = schedule(LONG_PRESS_MS) {
                if (thisPress == press) {
                    spent = true
                    onHold()
                }
            }
            return
        }
        if (spent) return
        onPress()
    }

    /** Gamepad mode is going: a hold pending now never fires, and a press in progress is spent. */
    fun cancel() {
        stopHoldTimer()
        if (down) spent = true
    }

    private fun stopHoldTimer() {
        hold?.cancel(false)
        hold = null
        press++
    }

    companion object {
        /** Holding the power button this long opens the power menu. */
        const val LONG_PRESS_MS = 600L
    }
}
