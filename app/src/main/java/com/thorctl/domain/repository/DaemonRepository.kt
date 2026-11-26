package com.thorctl.domain.repository

import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** The root daemon, as the app sees it: its state, its telemetry, its events, and commands to it. */
interface DaemonRepository {
    /** Null until the daemon has sent its state. */
    val state: StateFlow<DaemonState?>

    /** Null until the first telemetry arrives (only while someone [subscribeStats]). */
    val stats: StateFlow<Stats?>

    val connected: StateFlow<Boolean>

    /**
     * Everything else the daemon says, in order (state and stats go to their own flows, gamepad
     * reports to [gamepadReports]). Images in events must be read or closed.
     */
    val events: SharedFlow<DaemonEvent>

    /** Bluetooth gamepad reports; newest wins. */
    val gamepadReports: SharedFlow<ByteArray>

    /** Fire and forget; dropped while disconnected. Some commands update [state] right away. */
    fun send(command: DaemonCommand)

    /** Reference counted: the overlay and the activity can both want live stats. */
    fun subscribeStats(on: Boolean)
}
