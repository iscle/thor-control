package com.thorctl.daemon.cli

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.Screen

/**
 * The debug CLI's syntax: a command name and its arguments, as separate words.
 *
 *   state | stats
 *   fan <profile id> | fan-delete <id>
 *   brightness <top|bottom> <0..1>
 *   controller <free|top|bottom> | move <top|bottom>
 *   bottom-power <on|off> | game <on|off> | keep-accessibility <on|off>
 *   leds <on|off|solid|breathe|rainbow>
 *   gamepad <on|off> | gamepad-transport <usb|bt> | gamepad-screens-off <on|off>
 *   recents-close
 */
object CliCommands {
    const val USAGE = """usage: Cli <command> [args]
  state | stats
  fan <profile id> | fan-delete <id>
  brightness <top|bottom> <0..1>
  controller <free|top|bottom> | move <top|bottom>
  bottom-power <on|off> | game <on|off> | keep-accessibility <on|off>
  leds <on|off|solid|breathe|rainbow>
  gamepad <on|off> | gamepad-transport <usb|bt> | gamepad-screens-off <on|off>
  recents-close"""

    /** Null if the words don't make a command. [state] is the daemon's, for commands that change part of it. */
    fun parse(words: List<String>, state: DaemonState?): CliRequest? {
        val name = words.firstOrNull() ?: return null
        val argument = words.getOrNull(1)
        fun onOff(): Boolean? = when (argument) {
            "on" -> true
            "off" -> false
            else -> null
        }
        fun send(command: DaemonCommand?) = command?.let { CliRequest.Send(it) }
        return when (name) {
            "state" -> CliRequest.State
            "stats" -> CliRequest.Stats
            "fan" -> send(argument?.let { DaemonCommand.SetFanProfile(it) })
            "fan-delete" -> send(argument?.let { DaemonCommand.DeleteFanProfile(it) })
            "brightness" -> {
                val screen = Screen.fromWire(argument)
                val value = words.getOrNull(2)?.toFloatOrNull()?.takeIf { it in 0f..1f }
                send(if (screen != null && value != null) DaemonCommand.SetBrightness(screen, value, final = true) else null)
            }
            "controller" -> send(ControllerTarget.fromWire(argument)?.let { DaemonCommand.SetController(it) })
            "move" -> send(Screen.fromWire(argument)?.let { DaemonCommand.MoveApp(it) })
            "bottom-power" -> send(onOff()?.let { DaemonCommand.SetBottomPower(it) })
            "game" -> send(onOff()?.let { DaemonCommand.SetGameMode(it) })
            "keep-accessibility" -> send(onOff()?.let { DaemonCommand.SetKeepAccessibility(it) })
            "leds" -> {
                val leds = state?.leds ?: return null
                val mode = LedMode.fromWire(argument)
                send(
                    when {
                        mode != null -> DaemonCommand.SetLeds(leds.copy(mode = mode, enabled = true))
                        onOff() != null -> DaemonCommand.SetLeds(leds.copy(enabled = onOff()!!))
                        else -> null
                    },
                )
            }
            "gamepad" -> send(onOff()?.let { DaemonCommand.SetGamepad(it) })
            "gamepad-transport" -> send(GamepadTransport.fromWire(argument)?.let { DaemonCommand.SetGamepadTransport(it) })
            "gamepad-screens-off" -> send(onOff()?.let { DaemonCommand.SetGamepadScreensOff(it) })
            "recents-close" -> send(DaemonCommand.RecentsClose)
            else -> null
        }
    }
}
