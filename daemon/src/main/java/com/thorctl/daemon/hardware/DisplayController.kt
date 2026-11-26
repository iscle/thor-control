package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.WakeListener
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.input.Touchscreens
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.SystemServiceException
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
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
@ContributesIntoSet(DaemonScope::class, binding = binding<WakeListener>())
class DisplayController(
    private val displayService: DisplayService,
    private val touchscreens: Touchscreens,
    private val panelHealer: BottomPanelHealer,
    private val backlight: PanelBacklight,
) : WakeListener {
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

    /**
     * A display's power state changed. Waking powers every panel on and drops our overrides with
     * it, while the display manager keeps reporting the overridden state: so whenever a panel we
     * hold off changes state, hold it off again (asking again for a panel already off changes
     * nothing).
     */
    fun onDisplayStateChanged(displayId: Int) {
        val heldOff = if (gamepadDark) displayId == topId else bottomForcedOff && displayId == bottomId
        if (heldOff) reassertOverrides()
        if (gamepadDark && displayId == bottomId) backlight.holdDark()
    }

    /** Android sets a panel's backlight whenever its brightness changes: the dark bottom one stays at 0. */
    fun onBrightnessChanged(displayId: Int) {
        if (gamepadDark && displayId == bottomId) backlight.holdDark()
    }

    /** Puts our power overrides back (they can be dropped when the device wakes). */
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
     * goes off; the bottom one stays powered with its backlight at 0, which on this OLED is no
     * light at all, next to no power and no burn-in. Touch is off on both meanwhile.
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
        if (dark) backlight.darken() else backlight.restore()
        touchscreens.setEnabled(TOP_TOUCH_NAME, !dark)
        setBottomTouch(!dark && !bottomForcedOff)
        Timber.i("gamepad screens ${if (dark) "dark (top off, bottom backlight off)" else "back on"}")
    }

    /** The device woke up: the bottom panel watchdog starts over. */
    override fun onWakeChanged(awake: Boolean) {
        if (awake) panelHealer.onWake()
    }

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
