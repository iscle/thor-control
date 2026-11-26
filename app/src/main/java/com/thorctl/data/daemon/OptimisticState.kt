package com.thorctl.data.daemon

import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonState

/**
 * The state as it will be once the daemon has carried out [command], for the commands whose
 * effect sliders and switches must show at once, before the daemon echoes it. Other commands
 * leave the state as it is.
 */
fun DaemonState.withExpectedEffectOf(command: DaemonCommand): DaemonState = when (command) {
    is DaemonCommand.SetLeds -> copy(leds = command.state)
    is DaemonCommand.SetGameMode -> copy(game = game.copy(enabled = command.on))
    is DaemonCommand.SetGameOptions -> copy(game = game.copy(options = command.options))
    else -> this
}
