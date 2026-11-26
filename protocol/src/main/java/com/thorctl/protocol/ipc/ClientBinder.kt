package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.RecentTask

/** The app's side of [IThorClient]: every call becomes a [DaemonEvent] for [onEvent], on a binder thread. */
class ClientBinder(
    private val onEvent: (DaemonEvent) -> Unit,
    private val jpegs: JpegImporter = PipeJpegImporter,
) : IThorClient.Stub() {
    override fun onState(state: DaemonStateParcel?) {
        state?.let { onEvent(DaemonEvent.StateChanged(it.toModel())) }
    }

    override fun onStats(stats: StatsParcel?) {
        stats?.let { onEvent(DaemonEvent.StatsUpdated(it.toModel())) }
    }

    override fun onMenuButton() = onEvent(DaemonEvent.MenuButton)

    override fun onHaptic() = onEvent(DaemonEvent.Haptic)

    override fun onHud(hud: HudParcel?) {
        hud?.let { onEvent(DaemonEvent.Hud(it.toModel(), it.displayId)) }
    }

    override fun onRecents(
        displayId: Int,
        fromHome: Boolean,
        originTask: Int,
        hero: ParcelFileDescriptor?,
        home: ParcelFileDescriptor?,
        tasks: Array<out RecentTaskParcel?>?,
    ) = onEvent(
        DaemonEvent.RecentsOpened(
            displayId = displayId,
            fromHome = fromHome,
            originTask = originTask,
            hero = jpegs.import(hero),
            home = jpegs.import(home),
            tasks = tasks.orEmpty().filterNotNull().map {
                RecentTask(it.id, it.packageName.orEmpty(), it.displayId, it.visible, it.running, jpegs.import(it.thumb))
            },
        ),
    )

    override fun onRecentsThumb(taskId: Int, thumb: ParcelFileDescriptor?) {
        jpegs.import(thumb)?.let { onEvent(DaemonEvent.RecentsThumb(taskId, it)) }
    }

    override fun onRecentsKey(key: String?) = onEvent(DaemonEvent.RecentsKey(key.orEmpty()))

    override fun onRecentsClosed() = onEvent(DaemonEvent.RecentsClosed)

    override fun onHomeAnimation(seq: Int, displayId: Int, appScreen: ParcelFileDescriptor?, homeScreen: ParcelFileDescriptor?) =
        onEvent(DaemonEvent.HomeAnimation(seq, displayId, jpegs.import(appScreen), jpegs.import(homeScreen)))

    override fun onKeyboardKey(key: String?) = onEvent(DaemonEvent.KeyboardKey(key.orEmpty()))

    override fun onGamepadReport(report: ByteArray?) {
        report?.let { onEvent(DaemonEvent.GamepadReport(it)) }
    }
}
