package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats

/** The daemon's registered clients: the app, and the debug CLI while it runs. */
interface ClientLink {
    /** The app is registered (the CLI doesn't count: it draws no menu, recents or keyboard). */
    val appConnected: Boolean

    val hasStatsSubscribers: Boolean

    /** To every client. A client that is gone is dropped. */
    fun broadcast(event: DaemonEvent)

    /** To the clients that asked for live stats. */
    fun broadcastStats(stats: Stats)
}
