package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.Jpeg
import java.io.IOException

/**
 * The daemon's side of [IThorClient]: one [DaemonEvent] as the matching call. Throws
 * RemoteException when the client is gone.
 */
class ClientEventSender(private val client: IThorClient, private val jpegs: JpegExporter) {
    val binder get() = client.asBinder()

    fun send(event: DaemonEvent) {
        // Our copies of the pipes' read ends: the transaction duplicates them for the client.
        val sent = ArrayList<ParcelFileDescriptor>(4)
        fun export(jpeg: Jpeg?) = jpegs.export(jpeg)?.also { sent += it }
        try {
            when (event) {
                is DaemonEvent.StateChanged -> client.onState(event.state.toParcel())
                is DaemonEvent.StatsUpdated -> client.onStats(event.stats.toParcel())
                DaemonEvent.MenuButton -> client.onMenuButton()
                DaemonEvent.Haptic -> client.onHaptic()
                is DaemonEvent.Hud -> client.onHud(event.hud.toParcel(event.displayId))
                is DaemonEvent.RecentsOpened -> client.onRecents(
                    event.displayId,
                    event.fromHome,
                    event.originTask,
                    export(event.hero),
                    export(event.home),
                    event.tasks.map { task ->
                        RecentTaskParcel().also {
                            it.id = task.id
                            it.packageName = task.packageName
                            it.displayId = task.displayId
                            it.visible = task.visible
                            it.running = task.running
                            it.thumb = export(task.thumb)
                        }
                    }.toTypedArray(),
                )
                is DaemonEvent.RecentsThumb -> export(event.thumb)?.let { client.onRecentsThumb(event.taskId, it) }
                is DaemonEvent.RecentsButton -> client.onRecentsButton(event.action.wire)
                DaemonEvent.RecentsClosed -> client.onRecentsClosed()
                is DaemonEvent.HomeAnimation -> client.onHomeAnimation(event.seq, event.displayId, export(event.appScreen), export(event.homeScreen))
                DaemonEvent.KeyboardShift -> client.onKeyboardShift()
                is DaemonEvent.GamepadReport -> client.onGamepadReport(event.report)
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
