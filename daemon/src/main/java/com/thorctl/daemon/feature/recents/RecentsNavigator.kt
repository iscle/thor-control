package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.daemon.hardware.input.EventHandler
import com.thorctl.protocol.model.RecentsAction
import java.util.concurrent.ScheduledFuture
import kotlin.math.abs

/**
 * Drives the recents screen with the grabbed controller: turns its raw evdev events into the key
 * names the app understands ("left", "right", "a", "b", "x", "y", "home") and hands them to
 * [send]. Held left/right moves one card, then keeps moving after a short pause, like a key
 * repeat; the stick needs a firm push and lets go only near the middle, so it never drifts.
 *
 * Lives on [thread]: the controller's events are handed over to it by the handler [start] returns,
 * so none of this state is shared with the grab reader thread.
 */
class RecentsNavigator(private val thread: RecentsThread, private val send: (RecentsAction) -> Unit) {
    /** Counts sessions, so events still queued from an ended one are dropped. */
    private var session = 0
    private var ignoreHomeUp = false
    private var homeDown = false

    /** Which inputs hold left (-1) or right (+1): d-pad hat, stick, d-pad keys, bumpers. */
    private val directions = HashMap<String, Int>()
    private var held: Int? = null
    private var repeat: ScheduledFuture<*>? = null
    private var stickX = 0

    /**
     * Recents opened; [homeHeld] when the Home hold that opened it has not been released yet.
     * Returns the handler for the grabbed controller's events (called on any thread), which passes
     * them on to [thread] until [stop].
     */
    fun start(homeHeld: Boolean): EventHandler {
        val current = ++session
        ignoreHomeUp = homeHeld
        homeDown = false
        directions.clear()
        held = null
        return { type, code, value -> thread.run { if (session == current) onEvent(type, code, value) } }
    }

    /** Recents closed: stop repeating, and drop the events still on their way. */
    fun stop() {
        session++
        held = null
        repeat?.cancel(false)
        repeat = null
    }

    private fun onEvent(type: Int, code: Int, value: Int) {
        when (type) {
            EvdevCodes.EV_KEY -> onKey(code, value)
            EvdevCodes.EV_ABS -> onAxis(code, value)
        }
    }

    private fun onKey(code: Int, value: Int) {
        when (code) {
            EvdevCodes.KEY_HOME -> {
                // The hold that opened this screen ends here; a new press goes home.
                if (value == 0 && ignoreHomeUp) {
                    ignoreHomeUp = false
                    return
                }
                if (value == 1) homeDown = true
                if (value == 0 && homeDown) {
                    homeDown = false
                    send(RecentsAction.HOME)
                }
                return
            }
            EvdevCodes.BTN_TL, EvdevCodes.BTN_DPAD_LEFT, EvdevCodes.KEY_LEFT -> setDirection("k$code", if (value != 0) -1 else 0)
            EvdevCodes.BTN_TR, EvdevCodes.BTN_DPAD_RIGHT, EvdevCodes.KEY_RIGHT -> setDirection("k$code", if (value != 0) 1 else 0)
        }
        if (value != 1) return
        when (code) {
            EvdevCodes.BTN_SOUTH, EvdevCodes.BTN_START -> send(RecentsAction.OPEN)
            EvdevCodes.BTN_EAST, EvdevCodes.KEY_BACK -> send(RecentsAction.BACK)
            EvdevCodes.BTN_NORTH -> send(RecentsAction.CLOSE)
            EvdevCodes.BTN_WEST -> send(RecentsAction.CLOSE_ALL)
        }
    }

    private fun onAxis(code: Int, value: Int) {
        when (code) {
            EvdevCodes.ABS_HAT0X -> setDirection("hat", value.coerceIn(-1, 1))
            EvdevCodes.ABS_X -> {
                stickX = stickDirection(value, stickX)
                setDirection("stick", stickX)
            }
        }
    }

    /** Held left/right moves one card, then keeps moving after a short pause (like a key repeat). */
    private fun setDirection(source: String, direction: Int) {
        if (direction == 0) directions.remove(source) else directions[source] = direction
        val now = directions.values.firstOrNull()
        if (now == held) return
        held = now
        repeat?.cancel(false)
        repeat = null
        if (now == null) return
        val action = if (now < 0) RecentsAction.PREVIOUS else RecentsAction.NEXT
        send(action)
        repeat = thread.repeat(REPEAT_DELAY_MS, REPEAT_EVERY_MS) { if (held == now) send(action) }
    }

    companion object {
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_EVERY_MS = 170L
        const val STICK_ON = 20000
        const val STICK_OFF = 8000

        /**
         * Where the stick points (-1, 0, 1) at [value], given where it pointed before: it takes
         * [STICK_ON] to point, and it lets go only below [STICK_OFF].
         */
        fun stickDirection(value: Int, previous: Int): Int = when {
            value <= -STICK_ON -> -1
            value >= STICK_ON -> 1
            abs(value) < STICK_OFF -> 0
            else -> previous
        }
    }
}
