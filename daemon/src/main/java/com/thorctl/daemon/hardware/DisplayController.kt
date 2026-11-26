package com.thorctl.daemon.hardware

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.input.Touchscreens
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.SystemServiceException
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Tracks which logical display is the top (6") and bottom (3.92") panel and controls their
 * brightness and power independently.
 *
 * Independent brightness requires the bottom panel to not be a "brightness follower" of the top
 * one; the Magisk module ships a display_layout_configuration.xml without leadDisplayAddress.
 * Also watches over a bottom panel that comes back blank ([BottomPanelHealer]).
 */
@Inject
@SingleIn(DaemonScope::class)
class DisplayController(
    private val displayService: DisplayService,
    private val touchscreens: Touchscreens,
    private val panelHealer: BottomPanelHealer,
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
        } catch (e: SystemServiceException) {
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
        } catch (_: SystemServiceException) {
            0f
        }
    }

    fun setBrightness(screen: Screen, value: Float) {
        val id = idFor(screen)
        if (id < 0) return
        try {
            displayService.setBrightness(id, value.coerceIn(MIN_BRIGHTNESS, 1f))
        } catch (e: SystemServiceException) {
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
        } catch (e: SystemServiceException) {
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
        } catch (_: SystemServiceException) {
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
        } catch (e: SystemServiceException) {
            Timber.w(e, "gamepad screens ${if (dark) "dark" else "back"}: display power failed")
        }
        touchscreens.setEnabled(TOP_TOUCH_NAME, !dark)
        setBottomTouch(!dark && !bottomForcedOff)
        Timber.i("gamepad screens ${if (dark) "dark (top off, bottom black)" else "back on"}")
    }

    /** The device woke up: the bottom panel watchdog starts over. */
    fun onWake() = panelHealer.onWake()

    /** Called every couple of seconds while the device is awake; see [BottomPanelHealer]. */
    fun healBottomPanel() = panelHealer.heal(bottomId, bottomForcedOff)

    /** Ignore touches on the dark panel so nothing invisible gets tapped. */
    private fun setBottomTouch(enabled: Boolean) = touchscreens.setEnabled(BOTTOM_TOUCH_NAME, enabled)

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
