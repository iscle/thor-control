package com.thorctl.daemon.control

import android.view.KeyEvent
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import com.thorctl.daemon.feature.Recents
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputRouter
import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.Proto
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/** The AYN button, its shortcuts and the Home button (from [InputWatcher]'s thread). */
@Inject
@SingleIn(DaemonScope::class)
class ButtonHandler(
    private val actions: DaemonActions,
    private val displays: DisplayController,
    private val router: InputRouter,
    private val game: GameMode,
    private val gamepad: Gamepad,
    private val recents: Recents,
    private val keyboard: KeyboardPresence,
    private val link: ClientLink,
    private val system: SystemServices,
    private val sys: Sys,
) : InputWatcher.Listener {
    override fun onAynTap() {
        // Gamepad mode with the screens dark (or asleep after all): AYN brings them back (the
        // controller stays on the other device).
        if (gamepad.lightUp()) return
        if (gamepad.state.on && !system.isInteractive()) {
            system.injectKey(KeyEvent.KEYCODE_WAKEUP, displays.topId)
            return
        }
        if (recents.isOpen) {
            recents.back()
            return
        }
        if (displays.bottomForcedOff) {
            actions.setBottomPower(true)
            return
        }
        if (link.appConnected) {
            link.broadcast(DaemonEvent.MenuButton)
        } else {
            // App not connected (still starting, or crashed): wake it through an explicit broadcast.
            sys.sh("am broadcast -a ${Proto.ACTION_TOGGLE_MENU} -n ${Proto.COMMAND_RECEIVER} --receiver-foreground")
        }
    }

    override fun onCombo(combo: InputWatcher.Combo) {
        Timber.i("combo AYN+$combo")
        when (combo) {
            InputWatcher.Combo.DOWN -> actions.setController(ControllerTarget.BOTTOM, announce = true)
            InputWatcher.Combo.UP -> actions.setController(ControllerTarget.TOP, announce = true)
            InputWatcher.Combo.LEFT, InputWatcher.Combo.RIGHT -> actions.setController(ControllerTarget.FREE, announce = true)
            InputWatcher.Combo.B -> actions.moveApp(Screen.BOTTOM)
            InputWatcher.Combo.X -> actions.moveApp(Screen.TOP)
            InputWatcher.Combo.Y -> actions.setGame(!game.enabled, announce = true)
            InputWatcher.Combo.L1 -> actions.stepBrightness(Screen.BOTTOM, -1)
            InputWatcher.Combo.R1 -> actions.stepBrightness(Screen.BOTTOM, 1)
            InputWatcher.Combo.L2 -> actions.stepBrightness(Screen.TOP, -1)
            InputWatcher.Combo.R2 -> actions.stepBrightness(Screen.TOP, 1)
        }
    }

    /** The screen Home acts on: where the controller is routed, or the focused one when it follows focus. */
    private fun homeDisplay(): Int {
        val d = when (router.target) {
            ControllerTarget.TOP -> displays.topId
            ControllerTarget.BOTTOM -> displays.bottomId
            ControllerTarget.FREE -> system.focusedDisplayId()
        }
        return if (d < 0 || (d == displays.bottomId && !displays.bottomOn)) displays.topId else d
    }

    override fun onShoulder() {
        if (keyboard.visible) link.broadcast(DaemonEvent.KeyboardKey("shift"))
    }

    override fun onHomeDown() {
        recents.homeDown(homeDisplay())
    }

    override fun onHomeSingle() {
        val d = homeDisplay()
        // Inside an app: Thor Control animates to the home screen and sends Home itself.
        if (recents.homeShort(d)) return
        system.injectKey(KeyEvent.KEYCODE_HOME, d)
        game.nudge()
    }

    override fun onHomeLong() {
        link.broadcast(DaemonEvent.Haptic)
        if (!link.appConnected) {
            // App not connected: fall back to the system's switcher (Trebuchet, if enabled).
            system.injectKey(KeyEvent.KEYCODE_APP_SWITCH, displays.topId)
            return
        }
        recents.homeLong(homeDisplay())
    }
}
