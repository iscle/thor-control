package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.DaemonEvent
import java.io.IOException

/**
 * The daemon's side of [IThorClient]: one [DaemonEvent] as the matching call. Throws
 * RemoteException when the client is gone.
 */
class ClientEventSender(private val client: IThorClient, private val jpegs: JpegExporter) {
    val binder get() = client.asBinder()

    fun send(e: DaemonEvent) {
        // Our copies of the pipes' read ends: the transaction duplicates them for the client.
        val sent = ArrayList<ParcelFileDescriptor>(4)
        fun export(j: com.thorctl.protocol.model.Jpeg?) = jpegs.export(j)?.also { sent += it }
        try {
            when (e) {
                is DaemonEvent.StateChanged -> client.onState(e.state.toParcel())
                is DaemonEvent.StatsUpdated -> client.onStats(e.stats.toParcel())
                DaemonEvent.MenuButton -> client.onMenuButton()
                DaemonEvent.Haptic -> client.onHaptic()
                is DaemonEvent.Hud -> client.onHud(e.hud.toParcel(e.displayId))
                is DaemonEvent.RecentsOpened -> client.onRecents(
                    e.displayId,
                    e.fromHome,
                    e.originTask,
                    export(e.hero),
                    export(e.home),
                    e.tasks.map { t ->
                        RecentTaskParcel().also {
                            it.id = t.id
                            it.pkg = t.pkg
                            it.displayId = t.displayId
                            it.visible = t.visible
                            it.running = t.running
                            it.thumb = export(t.thumb)
                        }
                    }.toTypedArray(),
                )
                is DaemonEvent.RecentsThumb -> export(e.thumb)?.let { client.onRecentsThumb(e.taskId, it) }
                is DaemonEvent.RecentsKey -> client.onRecentsKey(e.key)
                DaemonEvent.RecentsClosed -> client.onRecentsClosed()
                is DaemonEvent.HomeAnimation -> client.onHomeAnimation(e.seq, e.displayId, export(e.app), export(e.home))
                is DaemonEvent.KeyboardKey -> client.onKeyboardKey(e.key)
                is DaemonEvent.GamepadReport -> client.onGamepadReport(e.report)
            }
        } finally {
            sent.forEach {
                try {
                    it.close()
                } catch (_: IOException) {
                }
            }
        }
    }
}
