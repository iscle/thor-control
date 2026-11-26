package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.hardware.input.InputWatcher
import java.util.concurrent.ScheduledFuture
import kotlin.math.abs

/**
 * Drives the recents screen with the grabbed controller: turns its raw evdev events into the key
 * names the app understands ("left", "right", "a", "b", "x", "y", "home") and hands them to
 * [sendKey]. Held left/right moves one card, then keeps moving after a short pause, like a key
 * repeat; the stick needs a firm push and lets go only near the middle, so it never drifts.
 *
 * Events arrive on the grab reader thread; repeats run on [thread].
 */
class RecentsNavigator(private val thread: RecentsThread, private val sendKey: (String) -> Unit) {
    private var ignoreHomeUp = false
    private var homeDown = false

    /** Which inputs hold left (-1) or right (+1): d-pad hat, stick, d-pad keys, bumpers. */
    private val directions = HashMap<String, Int>()

    @Volatile
    private var held: Int? = null
    private var repeat: ScheduledFuture<*>? = null
    private var stickX = 0

    /** Recents opened; [homeHeld] when the Home hold that opened it has not been released yet. */
    fun start(homeHeld: Boolean) {
        ignoreHomeUp = homeHeld
        homeDown = false
        directions.clear()
        held = null
    }

    /** Recents closed: stop repeating. */
    fun stop() {
        held = null
        repeat?.cancel(false)
        repeat = null
    }

    /** A raw evdev event from the grabbed controller. */
    fun onEvent(type: Int, code: Int, value: Int) {
        when (type) {
            InputWatcher.EV_KEY -> onKey(code, value)
            InputWatcher.EV_ABS -> onAxis(code, value)
        }
    }

    private fun onKey(code: Int, value: Int) {
        when (code) {
            InputWatcher.KEY_HOME -> {
                // The hold that opened this screen ends here; a new press goes home.
                if (value == 0 && ignoreHomeUp) {
                    ignoreHomeUp = false
                    return
                }
                if (value == 1) homeDown = true
                if (value == 0 && homeDown) {
                    homeDown = false
                    sendKey("home")
                }
                return
            }
            BTN_TL, InputWatcher.BTN_DPAD_LEFT, InputWatcher.KEY_LEFT -> setDirection("k$code", if (value != 0) -1 else 0)
            BTN_TR, InputWatcher.BTN_DPAD_RIGHT, InputWatcher.KEY_RIGHT -> setDirection("k$code", if (value != 0) 1 else 0)
        }
        if (value != 1) return
        when (code) {
            BTN_SOUTH, BTN_START -> sendKey("a")
            InputWatcher.BTN_EAST, KEY_BACK -> sendKey("b")
            InputWatcher.BTN_NORTH -> sendKey("x")
            InputWatcher.BTN_WEST -> sendKey("y")
        }
    }

    private fun onAxis(code: Int, value: Int) {
        when (code) {
            InputWatcher.ABS_HAT0X -> setDirection("hat", value.coerceIn(-1, 1))
            ABS_X -> {
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
        val keyName = if (now < 0) "left" else "right"
        sendKey(keyName)
        repeat = thread.repeat(REPEAT_DELAY_MS, REPEAT_EVERY_MS) { if (held == now) sendKey(keyName) }
    }

    companion object {
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_EVERY_MS = 170L
        const val STICK_ON = 20000
        const val STICK_OFF = 8000
        const val BTN_SOUTH = 304 // "A"
        const val BTN_TL = 310
        const val BTN_TR = 311
        const val BTN_START = 315
        const val KEY_BACK = 158
        const val ABS_X = 0

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
