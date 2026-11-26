package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * The bottom panel's backlight (/sys/class/backlight/panelN-backlight, the last one). At 0 the
 * OLED gives no light at all while the panel stays powered, which keeps the controller powered
 * too (its driver cuts it once both panels are off). The value Android had set is kept in
 * [SAVED], so restore-kernel.sh puts it back if the daemon dies while the panel is dark.
 */
@Inject
@SingleIn(DaemonScope::class)
class PanelBacklight(private val sys: Sys) {
    private val bottom: String? by lazy {
        sys.list(BACKLIGHTS).filter { PANEL.matches(it.substringAfterLast('/')) }.maxOrNull()?.let { "$it/brightness" }
    }

    /** Turns the bottom panel's light off, remembering the brightness Android had set. */
    fun darken() {
        if (bottom == null) return Timber.w("no bottom panel backlight")
        sys.mkdirs(SAVED.substringBeforeLast('/'))
        holdDark()
    }

    /**
     * Android writes the backlight again whenever the brightness changes: remember that value (it
     * is the one to come back to) and go back to 0.
     */
    fun holdDark() {
        val node = bottom ?: return
        val current = sys.read(node) ?: return
        if (current == "0") return
        sys.write(SAVED, "$node $current\n")
        sys.write(node, "0")
    }

    /** The brightness Android had set, back again. */
    fun restore() {
        val saved = sys.read(SAVED) ?: return
        val node = saved.substringBefore(' ')
        val value = saved.substringAfter(' ', "")
        if (value.isNotEmpty()) sys.write(node, value)
        sys.remove(SAVED)
    }

    companion object {
        const val BACKLIGHTS = "/sys/class/backlight"
        const val SAVED = "/dev/thorctl/backlight.orig"
        private val PANEL = Regex("panel\\d+-backlight")
    }
}
