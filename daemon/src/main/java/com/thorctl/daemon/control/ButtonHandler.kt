package com.thorctl.daemon.control

import android.view.KeyEvent
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.gamemode.GameMode
import com.thorctl.daemon.feature.gamepad.Gamepad
import com.thorctl.daemon.feature.recents.Recents
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputRouter
import com.thorctl.daemon.hardware.input.AynCombo
import com.thorctl.daemon.hardware.input.InputWatcher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.system.InputService
import com.thorctl.daemon.system.PowerService
import com.thorctl.daemon.system.TaskService
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
    private val inputService: InputService,
    private val powerService: PowerService,
    private val taskService: TaskService,
    private val sys: Sys,
) : InputWatcher.Listener {
    override fun onAynTap() {
        // Gamepad mode with the screens dark (or asleep after all): AYN brings them back (the
        // controller stays on the other device).
        if (gamepad.lightUp()) return
        if (gamepad.state.on && !powerService.isInteractive()) {
            inputService.injectKey(KeyEvent.KEYCODE_WAKEUP, displays.topId)
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

    override fun onCombo(combo: AynCombo) {
        Timber.i("combo AYN+$combo")
        when (combo) {
            AynCombo.DOWN -> actions.setController(ControllerTarget.BOTTOM, announce = true)
            AynCombo.UP -> actions.setController(ControllerTarget.TOP, announce = true)
            AynCombo.LEFT, AynCombo.RIGHT -> actions.setController(ControllerTarget.FREE, announce = true)
            AynCombo.B -> actions.moveApp(Screen.BOTTOM)
            AynCombo.X -> actions.moveApp(Screen.TOP)
            AynCombo.Y -> actions.setGame(!game.enabled, announce = true)
            AynCombo.L1 -> actions.stepBrightness(Screen.BOTTOM, -1)
            AynCombo.R1 -> actions.stepBrightness(Screen.BOTTOM, 1)
            AynCombo.L2 -> actions.stepBrightness(Screen.TOP, -1)
            AynCombo.R2 -> actions.stepBrightness(Screen.TOP, 1)
        }
    }

    /** The screen Home acts on: where the controller is routed, or the focused one when it follows focus. */
    private fun homeDisplay(): Int {
        val displayId = when (router.target) {
            ControllerTarget.TOP -> displays.topId
            ControllerTarget.BOTTOM -> displays.bottomId
            ControllerTarget.FREE -> taskService.focusedDisplayId()
        }
        return if (displayId < 0 || (displayId == displays.bottomId && !displays.bottomOn)) displays.topId else displayId
    }

    override fun onShoulder() {
        if (keyboard.visible) link.broadcast(DaemonEvent.KeyboardKey("shift"))
    }

    override fun onHomeDown() {
        recents.homeDown(homeDisplay())
    }

    override fun onHomeSingle() {
        val displayId = homeDisplay()
        // Inside an app: Thor Control animates to the home screen and sends Home itself.
        if (recents.homeShort(displayId)) return
        inputService.injectKey(KeyEvent.KEYCODE_HOME, displayId)
        game.nudge()
    }

    override fun onHomeLong() {
        link.broadcast(DaemonEvent.Haptic)
        if (!link.appConnected) {
            // App not connected: fall back to the system's switcher (Trebuchet, if enabled).
            inputService.injectKey(KeyEvent.KEYCODE_APP_SWITCH, displays.topId)
            return
        }
        recents.homeLong(homeDisplay())
    }
}
