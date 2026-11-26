package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.DaemonCommand

/** What the daemon does with calls on its binder (the daemon's side of [IThorDaemon]). */
interface DaemonEndpoint {
    fun isAllowed(uid: Int): Boolean

    fun register(client: IThorClient, uid: Int)

    fun unregister(client: IThorClient)

    fun setStatsSubscribed(client: IThorClient, on: Boolean)

    fun handle(command: DaemonCommand)
}
