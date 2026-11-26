package com.thorctl.daemon.ipc

import android.os.IBinder
import android.os.RemoteException
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.ipc.ClientEventSender
import com.thorctl.protocol.ipc.IThorClient
import com.thorctl.protocol.ipc.JpegExporter
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Stats
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/** The registered [IThorClient]s. A client is dropped when its process dies or a call to it fails. */
@Inject
@SingleIn(DaemonScope::class)
class ClientRegistry(private val jpegs: JpegExporter, private val uids: UidPolicy) : ClientLink {
    private inner class Client(client: IThorClient, val uid: Int) : IBinder.DeathRecipient {
        val sender = ClientEventSender(client, jpegs)
        val binder: IBinder = client.asBinder()

        @Volatile
        var wantsStats = false

        override fun binderDied() {
            remove(binder)
        }
    }

    private val clients = CopyOnWriteArrayList<Client>()

    override val appConnected: Boolean get() = clients.any { uids.isApp(it.uid) }

    override val hasStatsSubscribers: Boolean get() = clients.any { it.wantsStats }

    /** Adds (or keeps) [client]; false if it is already dead. */
    fun add(client: IThorClient, uid: Int): Boolean {
        val binder = client.asBinder()
        if (clients.any { it.binder == binder }) return true
        val newClient = Client(client, uid)
        try {
            binder.linkToDeath(newClient, 0)
        } catch (_: RemoteException) {
            return false
        }
        clients += newClient
        Timber.i("client uid=$uid registered (${clients.size} now)")
        return true
    }

    fun remove(binder: IBinder) {
        val client = clients.firstOrNull { it.binder == binder } ?: return
        if (clients.remove(client)) {
            try {
                binder.unlinkToDeath(client, 0)
            } catch (_: Exception) {
            }
            Timber.i("client uid=${client.uid} gone (${clients.size} left)")
        }
    }

    fun setStats(binder: IBinder, on: Boolean): Boolean {
        val client = clients.firstOrNull { it.binder == binder } ?: return false
        client.wantsStats = on
        return true
    }

    /** To one client. */
    fun send(binder: IBinder, event: DaemonEvent) {
        clients.firstOrNull { it.binder == binder }?.let { deliver(it, event) }
    }

    override fun broadcast(event: DaemonEvent) {
        clients.forEach { deliver(it, event) }
    }

    override fun broadcastStats(stats: Stats) {
        val event = DaemonEvent.StatsUpdated(stats)
        clients.forEach { if (it.wantsStats) deliver(it, event) }
    }

    private fun deliver(client: Client, event: DaemonEvent) {
        try {
            client.sender.send(event)
        } catch (error: RemoteException) {
            // A oneway call also fails while the client's buffer is full; only a dead client goes
            // (its death recipient fires too). If it is dropped anyway, the next binder offer
            // makes the app register again.
            Timber.w("client uid=${client.uid} unreachable: ${error.javaClass.simpleName}")
            if (!client.binder.isBinderAlive) remove(client.binder)
        } catch (error: RuntimeException) {
            // A parcel too large or a bug in one event must not take the caller's thread down.
            Timber.w(error, "sending ${event.javaClass.simpleName} to uid=${client.uid} failed")
        }
    }
}
