package com.thorctl.daemon.hardware.input

/**
 * The controller's physical Home key: short press = Home on release (like stock), long press =
 * app switcher while still held. The module removes HOME from the controller's key layout so
 * Android does not also act on it ([homeRemapped]); without that remap Android already handles
 * the short press and we only add the app switcher on long press.
 */
class HomeButton(
    private val homeRemapped: Boolean,
    private val worker: InputWorker,
    private val listener: InputWatcher.Listener,
) {
    private val lock = Any()
    private var down = false
    private var longFired = false
    private var longPress: Cancellable? = null

    /** A KEY_HOME event from the controller (1 = down, 0 = up, 2 = repeat). */
    fun onKey(value: Int) {
        synchronized(lock) {
            when (value) {
                1 -> {
                    if (down) return
                    down = true
                    longFired = false
                    if (homeRemapped) worker.post { listener.onHomeDown() }
                    // Runs on the worker, which logs a listener failure like for posted calls.
                    longPress = worker.schedule(LONG_HOME_MS) {
                        val fire = synchronized(lock) {
                            if (down) longFired = true
                            down
                        }
                        if (fire) listener.onHomeLong()
                    }
                }
                0 -> {
                    if (!down) return
                    down = false
                    longPress?.cancel()
                    longPress = null
                    // Without the key layout remap Android already handled the short press.
                    if (!longFired && homeRemapped) worker.post { listener.onHomeSingle() }
                }
            }
        }
    }

    /**
     * A Home release that went to a grab never reaches our own reader (and the kernel drops a
     * re-injected one, since the key is already up): forget the press if [stillHeld] says so.
     */
    fun resetIfReleased(stillHeld: () -> Boolean) {
        synchronized(lock) {
            if (down && !stillHeld()) {
                down = false
                longPress?.cancel()
                longPress = null
            }
        }
    }

    companion object {
        const val LONG_HOME_MS = 450L
    }
}
