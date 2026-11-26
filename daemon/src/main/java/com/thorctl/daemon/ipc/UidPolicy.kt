package com.thorctl.daemon.ipc

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.ipc.ThorIpc
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Who may call the daemon: root, system, the shell (the debug CLI) and the app. The app's uid is
 * read from its data directory and read again when an unknown uid calls (the app was reinstalled).
 */
@Inject
@SingleIn(DaemonScope::class)
class UidPolicy(private val appUidLookup: AppUidLookup) {
    @Volatile
    private var appUid = -1

    fun isAllowed(uid: Int): Boolean =
        uid == ThorIpc.UID_ROOT || uid == ThorIpc.UID_SYSTEM || uid == ThorIpc.UID_SHELL || isApp(uid)

    fun isApp(uid: Int): Boolean {
        if (uid == appUid) return true
        appUid = appUidLookup.appUid()
        return uid == appUid
    }
}
