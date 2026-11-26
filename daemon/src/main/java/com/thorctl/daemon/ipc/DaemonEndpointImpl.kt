package com.thorctl.daemon.ipc

import com.thorctl.daemon.control.CommandDispatcher
import com.thorctl.daemon.control.StateSnapshot
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.StatsCollector
import com.thorctl.protocol.ipc.DaemonEndpoint
import com.thorctl.protocol.ipc.IThorClient
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/** Calls on the daemon's binder (binder threads): connection handling here, commands to [dispatcher]. */
@Inject
@SingleIn(DaemonScope::class)
class DaemonEndpointImpl(
    private val uids: UidPolicy,
    private val clients: ClientRegistry,
    private val snapshot: StateSnapshot,
    private val stats: StatsCollector,
    private val dispatcher: CommandDispatcher,
) : DaemonEndpoint {
    override fun isAllowed(uid: Int): Boolean = uids.isAllowed(uid).also { if (!it) Timber.w("rejecting caller uid=$uid") }

    /** A new client starts from the full state and a telemetry reading: it never has to show "no data". */
    override fun register(client: IThorClient, uid: Int) {
        if (!clients.add(client, uid)) return
        clients.send(client.asBinder(), DaemonEvent.StateChanged(snapshot.current()))
        clients.send(client.asBinder(), DaemonEvent.StatsUpdated(stats.collect()))
    }

    override fun unregister(client: IThorClient) = clients.remove(client.asBinder())

    override fun setStatsSubscribed(client: IThorClient, on: Boolean) {
        if (clients.setStats(client.asBinder(), on) && on) {
            clients.send(client.asBinder(), DaemonEvent.StatsUpdated(stats.collect()))
        }
    }

    override fun handle(command: DaemonCommand) {
        try {
            dispatcher.handle(command)
        } catch (error: Exception) {
            Timber.w(error, "command $command failed")
        }
    }
}
