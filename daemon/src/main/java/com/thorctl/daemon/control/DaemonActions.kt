package com.thorctl.daemon.control

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.FanController
import com.thorctl.daemon.hardware.InputRouter
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GameHudState
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Screen
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** What both the AYN shortcuts and the app's commands do. */
@Inject
@SingleIn(DaemonScope::class)
class DaemonActions(
    private val cfg: ConfigStore,
    private val displays: DisplayController,
    private val router: InputRouter,
    private val fan: FanController,
    private val game: GameMode,
    private val system: SystemServices,
    private val notifier: DaemonNotifier,
    private val lock: DaemonLock,
    private val sched: ScheduledExecutorService,
) {
    private var saveTask: ScheduledFuture<*>? = null

    /** Writes the config shortly, once a burst of changes is over. */
    fun saveSoon() {
        synchronized(lock) {
            saveTask?.cancel(false)
            saveTask = sched.schedule({ cfg.save() }, 400, TimeUnit.MILLISECONDS)
        }
    }

    /** One fan step with the profile that should drive it now (game mode's, or the selected one). */
    fun fanTick() {
        synchronized(lock) {
            val override = game.fanOverride?.let { cfg.profile(it) }
            fan.tick(override ?: cfg.profile(cfg.fanProfile) ?: FanProfile.BUILTINS.first(), screenOn = system.isInteractive())
        }
    }

    fun setController(target: ControllerTarget, announce: Boolean) {
        synchronized(lock) {
            cfg.controller = target
            router.apply(target)
            saveSoon()
        }
        notifier.stateChanged()
        if (announce) {
            val display = if (target == ControllerTarget.BOTTOM) displays.bottomId else displays.topId
            notifier.hud(HudEvent.Controller(target), display)
        }
    }

    fun setGame(on: Boolean, announce: Boolean) {
        game.setEnabled(on)
        notifier.stateChanged()
        if (announce) notifier.hud(HudEvent.Game(if (on) GameHudState.ON else GameHudState.OFF), displays.topId)
        // Picking the game and closing other apps takes a moment; keep it off the caller's thread.
        if (on) game.nudge(delayMs = 0)
    }

    fun setBottomPower(on: Boolean) {
        synchronized(lock) { displays.setBottomPower(on) }
        notifier.stateChanged()
    }

    /** Moves the app in front of the other screen to [to]. */
    fun moveApp(to: Screen) {
        val toBottom = to == Screen.BOTTOM
        val from = if (toBottom) displays.topId else displays.bottomId
        val dest = if (toBottom) displays.bottomId else displays.topId
        if (from < 0 || dest < 0) return
        val task = try {
            system.topAppTask(from)
        } catch (e: Throwable) {
            Timber.w(e, "topAppTask failed")
            null
        }
        if (task == null) {
            notifier.hud(HudEvent.NothingToMove(if (toBottom) Screen.TOP else Screen.BOTTOM), from)
            return
        }
        if (toBottom && displays.bottomForcedOff) setBottomPower(true)
        try {
            system.moveTaskToDisplay(task.taskId, dest)
            // With "follow focus" routing, input should follow the app to its new screen.
            if (router.target == ControllerTarget.FREE) system.focusTopTask(dest)
            Timber.i("moved task ${task.taskId} (${task.packageName}) to display $dest")
            notifier.hud(HudEvent.Moved(task.packageName.orEmpty(), to), dest)
        } catch (e: Throwable) {
            Timber.w(e, "move task failed")
        }
    }

    /** One brightness shortcut step ([BrightnessCurve.step]), confirmed on the screen that changed. */
    fun stepBrightness(screen: Screen, direction: Int) {
        val next = BrightnessCurve.step(displays.getBrightness(screen), direction)
        synchronized(lock) { displays.setBrightness(screen, BrightnessCurve.toLinear(next)) }
        notifier.stateChanged()
        // The pill shows on the screen that changed (on the top one if the bottom one is off).
        val d = if (screen == Screen.BOTTOM && displays.bottomOn && displays.bottomId >= 0) displays.bottomId else displays.topId
        notifier.hud(HudEvent.Brightness(screen, (next * 100f).roundToInt()), d)
    }
}
