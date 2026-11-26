package com.thorctl.domain.repository

import com.thorctl.domain.model.DaemonStatus
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** The root daemon, as the app sees it: its state, its telemetry, its events, and commands to it. */
interface DaemonRepository {
    /**
     * [DaemonStatus.Connected] once the daemon has sent its state, with every newer state and
     * the connection's telemetry.
     */
    val status: StateFlow<DaemonStatus>

    /**
     * Everything else the daemon says, in order (state and stats go to their own flows, gamepad
     * reports to [gamepadReports]). Images in events must be read or closed.
     */
    val events: SharedFlow<DaemonEvent>

    /** Bluetooth gamepad reports; newest wins. */
    val gamepadReports: SharedFlow<ByteArray>

    /** Fire and forget; dropped while disconnected. Some commands update [status] right away. */
    fun send(command: DaemonCommand)

    /** Reference counted: the overlay and the activity can both want live stats ([DaemonStatus.Connected.stats]). */
    fun subscribeStats(on: Boolean)
}
