package com.thorctl.daemon.control

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.Recents
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.HudEvent
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Pushes changes to the clients. The snapshot and recents are lazy: game mode and the gamepad
 * report through here, and the snapshot reads them back.
 */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class ClientNotifier(
    private val link: ClientLink,
    private val snapshot: Lazy<StateSnapshot>,
    private val recents: Lazy<Recents>,
) : DaemonNotifier {
    override fun stateChanged() {
        link.broadcast(DaemonEvent.StateChanged(snapshot.value.current()))
    }

    override fun hud(event: HudEvent, displayId: Int) {
        recents.value.noteHud()
        link.broadcast(DaemonEvent.Hud(event, displayId))
    }
}
