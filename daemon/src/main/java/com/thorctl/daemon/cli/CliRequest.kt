package com.thorctl.daemon.cli

import com.thorctl.protocol.model.DaemonCommand

/** What one CLI invocation asks for. */
sealed interface CliRequest {
    /** Print the state (every run prints it once the daemon answers). */
    data object State : CliRequest

    /** Print telemetry while waiting. */
    data object Stats : CliRequest

    data class Send(val command: DaemonCommand) : CliRequest
}
