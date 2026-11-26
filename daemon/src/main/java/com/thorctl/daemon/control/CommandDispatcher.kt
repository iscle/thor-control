package com.thorctl.daemon.control

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import com.thorctl.daemon.feature.recents.Recents
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
    private val config: ConfigStore,
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
    fun handle(command: DaemonCommand) {
        when (command) {
            is DaemonCommand.SetController -> actions.setController(command.target, announce = false)
            is DaemonCommand.MoveApp -> actions.moveApp(command.to)
            is DaemonCommand.SetGameMode -> actions.setGame(command.on, announce = false)
            is DaemonCommand.KeyboardVisible -> keyboard.visible = command.on
            is DaemonCommand.SetGamepad -> gamepad.setOn(command.on)
            is DaemonCommand.SetGamepadTransport -> {
                gamepad.setTransport(command.transport)
                synchronized(lock) {
                    config.gamepadTransport = gamepad.state.transport
                    actions.saveSoon()
                }
            }
            is DaemonCommand.SetGamepadScreensOff -> {
                gamepad.setScreensOff(command.on)
                synchronized(lock) {
                    config.gamepadScreensOff = gamepad.state.screensOff
                    actions.saveSoon()
                }
            }
            is DaemonCommand.GamepadBluetooth -> gamepad.onBluetooth(command.connected, command.host)
            is DaemonCommand.RecentsOpen, DaemonCommand.RecentsHome, is DaemonCommand.RecentsRemove,
            is DaemonCommand.RecentsClear, DaemonCommand.RecentsClose, is DaemonCommand.HomeGo, DaemonCommand.OverlayDone,
            -> recents.handle(command)
            else -> if (updateSettings(command)) notifier.stateChanged()
        }
    }

    /** The commands that change settings; true when the state changed. */
    private fun updateSettings(command: DaemonCommand): Boolean = synchronized(lock) {
        when (command) {
            is DaemonCommand.SetFanProfile -> {
                if (config.profile(command.id) == null) return false
                config.fanProfile = command.id
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SaveFanProfile -> {
                val profile = FanProfiles.sanitize(command.profile)
                if (profile == null || profile.isBuiltin) return false
                val index = config.customProfiles.indexOfFirst { it.id == profile.id }
                if (index >= 0) config.customProfiles[index] = profile else config.customProfiles += profile
                if (command.activate) config.fanProfile = profile.id
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.DeleteFanProfile -> {
                if (!config.customProfiles.removeAll { it.id == command.id }) return false
                if (config.fanProfile == command.id) config.fanProfile = FanProfile.SMART_ID
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SetBrightness -> {
                displays.setBrightness(command.screen, command.value)
                // Brightness drags are high-frequency; the slider owns its value while dragging.
                return command.final
            }
            is DaemonCommand.SetLeds -> {
                config.leds = command.state
                leds.apply(config.leds)
                actions.saveSoon()
            }
            is DaemonCommand.SetBottomPower -> displays.setBottomPower(command.on)
            is DaemonCommand.SetGameOptions -> {
                config.gameOptions = command.options
                game.setOptions(config.gameOptions)
                actions.saveSoon()
                actions.fanTick()
            }
            is DaemonCommand.SetKeepAccessibility -> {
                config.keepAccessibility = command.on
                actions.saveSoon()
            }
            else -> return false
        }
        true
    }
}
