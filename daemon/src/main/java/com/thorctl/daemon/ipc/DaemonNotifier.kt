package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.HudEvent

/** How the daemon's features tell the app that something changed. */
interface DaemonNotifier {
    /** The full state changed: push it to every client. */
    fun stateChanged()

    /** Confirmation pill for an AYN shortcut, on [displayId]. */
    fun hud(event: HudEvent, displayId: Int)
}
