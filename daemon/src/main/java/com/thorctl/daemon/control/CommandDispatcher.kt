package com.thorctl.daemon.control

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import com.thorctl.daemon.feature.Recents
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.LedController
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.FanProfile
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Carries out a client's [DaemonCommand] (on the binder thread that received it). */
@Inject
@SingleIn(DaemonScope::class)
class CommandDispatcher(
    private val cfg: ConfigStore,
    private val actions: DaemonActions,
    private val displays: DisplayController,
    private val leds: LedController,
    private val game: GameMode,
    private val gamepad: Gamepad,
    private val recents: Recents,
    private val keyboard: KeyboardPresence,
    private val notifier: DaemonNotifier,
    private val lock: DaemonLock,
) {
    fun handle(cmd: DaemonCommand) {
        when (cmd) {
            is DaemonCommand.SetController -> actions.setController(cmd.target, announce = false)
            is DaemonCommand.MoveApp -> actions.moveApp(cmd.to)
            is DaemonCommand.SetGameMode -> actions.setGame(cmd.on, announce = false)
            is DaemonCommand.KeyboardVisible -> keyboard.visible = cmd.on
            is DaemonCommand.SetGamepad -> gamepad.setOn(cmd.on)
            is DaemonCommand.SetGamepadTransport -> {
                gamepad.setTransport(cmd.transport)
                synchronized(lock) {
                    cfg.gamepadTransport = gamepad.state.transport
                    actions.saveSoon()
                }
            }
            is DaemonCommand.SetGamepadScreensOff -> {
                gamepad.setScreensOff(cmd.on)
                synchronized(lock) {
                    cfg.gamepadScreensOff = gamepad.state.screensOff
                    actions.saveSoon()
                }
            }
            is DaemonCommand.GamepadBluetooth -> gamepad.onBluetooth(cmd.connected, cmd.host)
            is DaemonCommand.RecentsOpen, DaemonCommand.RecentsHome, is DaemonCommand.RecentsRemove,
            is DaemonCommand.RecentsClear, DaemonCommand.RecentsClose, is DaemonCommand.HomeGo, DaemonCommand.OverlayDone,
            -> recents.handle(cmd)
            else -> if (updateSettings(cmd)) notifier.stateChanged()
        }
    }

    /** The commands that change settings; true when the state changed. */
    private fun updateSettings(cmd: DaemonCommand): Boolean = synchronized(lock) {
        when (cmd) {
            is DaemonCommand.SetFanProfile -> {
                if (cfg.profile(cmd.id) == null) return false
                cfg.fanProfile = cmd.id
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SaveFanProfile -> {
                val p = FanProfiles.sanitize(cmd.profile)
                if (p == null || p.isBuiltin) return false
                val i = cfg.customProfiles.indexOfFirst { it.id == p.id }
                if (i >= 0) cfg.customProfiles[i] = p else cfg.customProfiles += p
                if (cmd.activate) cfg.fanProfile = p.id
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.DeleteFanProfile -> {
                if (!cfg.customProfiles.removeAll { it.id == cmd.id }) return false
                if (cfg.fanProfile == cmd.id) cfg.fanProfile = FanProfile.SMART_ID
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SetBrightness -> {
                displays.setBrightness(cmd.screen, cmd.value)
                // Brightness drags are high-frequency; the slider owns its value while dragging.
                return cmd.final
            }
            is DaemonCommand.SetLeds -> {
                cfg.leds = cmd.state
                leds.apply(cfg.leds)
                actions.saveSoon()
            }
            is DaemonCommand.SetBottomPower -> displays.setBottomPower(cmd.on)
            is DaemonCommand.SetGameOptions -> {
                cfg.gameOptions = cmd.options
                game.setOptions(cfg.gameOptions)
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SetKeepAccessibility -> {
                cfg.keepAccessibility = cmd.on
                actions.saveSoon()
            }
            else -> return false
        }
        true
    }
}
