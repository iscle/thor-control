package com.thorctl.daemon.hardware

import android.os.SystemClock
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.InputService
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.File

/**
 * Tracks which logical display is the top (6") and bottom (3.92") panel and controls their
 * brightness and power independently.
 *
 * Independent brightness requires the bottom panel to not be a "brightness follower" of the top
 * one; the Magisk module ships a display_layout_configuration.xml without leadDisplayAddress.
 */
@Inject
@SingleIn(DaemonScope::class)
class DisplayController(
    private val sys: Sys,
    private val displayService: DisplayService,
    private val inputService: InputService,
) {
    @Volatile
    var topId = 0
        private set

    @Volatile
    var bottomId = -1
        private set

    @Volatile
    var topUniqueId = ""
        private set

    @Volatile
    var bottomUniqueId = ""
        private set

    /** True while we hold the bottom panel off. */
    @Volatile
    var bottomForcedOff = false
        private set

    val bottomOn: Boolean get() = !bottomForcedOff

    /** Gamepad mode has the screens dark ([setGamepadDark]). */
    @Volatile
    var gamepadDark = false
        private set

    fun refresh() {
        val internal = try {
            displayService.displays().filter { it.type == TYPE_INTERNAL }
        } catch (e: Throwable) {
            Timber.w(e, "display refresh failed")
            return
        }
        when {
            internal.size >= 2 -> {
                val sorted = internal.sortedBy { it.width.toLong() * it.height }
                val bottom = sorted.first()
                val top = sorted.last()
                bottomId = bottom.id
                bottomUniqueId = bottom.uniqueId
                topId = top.id
                topUniqueId = top.uniqueId
            }
            internal.size == 1 -> {
                val only = internal.first()
                if (bottomUniqueId.isNotEmpty() && only.uniqueId == bottomUniqueId) {
                    bottomId = only.id
                    topId = -1
                } else {
                    topId = only.id
                    topUniqueId = only.uniqueId
                    bottomId = -1
                }
            }
        }
    }

    private fun idFor(screen: Screen): Int = if (screen == Screen.BOTTOM) bottomId else topId

    fun getBrightness(screen: Screen): Float {
        val id = idFor(screen)
        if (id < 0) return 0f
        return try {
            displayService.brightness(id)
        } catch (e: Throwable) {
            0f
        }
    }

    fun setBrightness(screen: Screen, value: Float) {
        val id = idFor(screen)
        if (id < 0) return
        try {
            displayService.setBrightness(id, value.coerceIn(MIN_BRIGHTNESS, 1f))
        } catch (e: Throwable) {
            Timber.w(e, "setBrightness($screen) failed")
        }
    }

    fun setBottomPower(on: Boolean): Boolean {
        val id = bottomId
        if (id < 0) return false
        if (gamepadDark) {
            // The bottom panel has to stay on while the screens are dark; applied when they light up.
            bottomForcedOff = !on
            return true
        }
        val succeeded = try {
            displayService.requestDisplayPower(id, if (on) STATE_UNKNOWN else STATE_OFF)
        } catch (e: Throwable) {
            Timber.w(e, "requestDisplayPower failed")
            false
        }
        if (succeeded) {
            bottomForcedOff = !on
            setBottomTouch(on)
        }
        Timber.i("bottom display ${if (on) "on" else "off"} -> $succeeded")
        return succeeded
    }

    /** The power overrides can be dropped when the device wakes; put them back. */
    fun reassertOverrides() {
        try {
            if (gamepadDark) {
                if (topId >= 0) displayService.requestDisplayPower(topId, STATE_OFF)
            } else if (bottomForcedOff && bottomId >= 0) {
                displayService.requestDisplayPower(bottomId, STATE_OFF)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Gamepad mode's "screens off". The controller's driver cuts the controller's power as soon
     * as both panels are off (it follows the panels, not Android's sleep), so only the top panel
     * goes off; the bottom one stays powered and the app covers it in black, which on OLED is no
     * light, next to no power and no burn-in. Touch is off on both meanwhile.
     */
    fun setGamepadDark(dark: Boolean) {
        if (dark == gamepadDark) return
        gamepadDark = dark
        try {
            if (topId >= 0) displayService.requestDisplayPower(topId, if (dark) STATE_OFF else STATE_UNKNOWN)
            // A bottom screen the user switched off has to be on while dark, and off again after.
            if (bottomForcedOff && bottomId >= 0) displayService.requestDisplayPower(bottomId, if (dark) STATE_UNKNOWN else STATE_OFF)
        } catch (e: Throwable) {
            Timber.w(e, "gamepad screens ${if (dark) "dark" else "back"}: display power failed")
        }
        setTouch(TOP_TOUCH_NAME, !dark)
        setBottomTouch(!dark && !bottomForcedOff)
        Timber.i("gamepad screens ${if (dark) "dark (top off, bottom black)" else "back on"}")
    }

    // ---- Bottom panel watchdog ------------------------------------------------------------
    // LineageOS sometimes boots with the bottom panel dark: the framework and SurfaceFlinger
    // say the display is ON, but the kernel DSI connector never got enabled. A framework power
    // cycle (off -> back to policy) makes HWC commit the panel again.

    private val bottomConnector: String? = File("/sys/class/drm").listFiles()
        ?.filter { it.name.matches(Regex("card\\d+-DSI-\\d+")) }
        ?.sortedBy { it.name }
        ?.takeIf { it.size >= 2 }
        ?.last()?.path

    private var stuckChecks = 0
    private var lastHeal = 0L
    private var healsThisWake = 0

    fun onWake() {
        stuckChecks = 0
        healsThisWake = 0
    }

    /** Called every couple of seconds while the device is awake. */
    fun healBottomPanel() {
        val connector = bottomConnector ?: return
        val id = bottomId
        if (id < 0 || bottomForcedOff) {
            stuckChecks = 0
            return
        }
        val frameworkOn = try {
            displayService.displays().firstOrNull { it.id == id }?.state == STATE_ON
        } catch (_: Throwable) {
            false
        }
        val panelEnabled = sys.read("$connector/enabled") == "enabled"
        if (!frameworkOn || panelEnabled) {
            stuckChecks = 0
            return
        }
        if (++stuckChecks < 2) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeal < 15_000 || healsThisWake >= 3) return
        lastHeal = now
        healsThisWake++
        stuckChecks = 0
        Timber.i("bottom panel stuck (display ON, ${connector.substringAfterLast('/')} not enabled); power-cycling it")
        try {
            displayService.requestDisplayPower(id, STATE_OFF)
            Thread.sleep(500)
            displayService.requestDisplayPower(id, STATE_UNKNOWN)
        } catch (e: Throwable) {
            Timber.w(e, "bottom panel power cycle failed")
        }
    }

    /** Ignore touches on the dark panel so nothing invisible gets tapped. */
    fun setBottomTouch(enabled: Boolean) = setTouch(BOTTOM_TOUCH_NAME, enabled)

    private fun setTouch(name: String, enabled: Boolean) {
        try {
            inputService.devices()
                .filter { it.name == name }
                .forEach { inputService.setDeviceEnabled(it.id, enabled) }
        } catch (e: Throwable) {
            Timber.w(e, "toggle touch ($name) failed")
        }
    }

    companion object {
        const val TYPE_INTERNAL = 1
        const val STATE_UNKNOWN = 0
        const val STATE_OFF = 1
        const val STATE_ON = 2
        const val MIN_BRIGHTNESS = 0.004f
        const val BOTTOM_TOUCH_NAME = "fts_ts_secondary"
        const val TOP_TOUCH_NAME = "fts_ts"
    }
}
