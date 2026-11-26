package com.thorctl.data.daemon

import android.os.IBinder

/** Where the daemon's binder arrives (the app's DaemonBinderProvider) and where the CLI gets it. */
interface DaemonConnection {
    /** The daemon offers its binder (at its start, and until we have registered). */
    fun onDaemonBinder(binder: IBinder)

    /** The daemon's binder while connected, for the debug CLI. */
    fun currentBinder(): IBinder?
}
