package com.thorctl.daemon.ipc

fun interface AppUidLookup {
    /** The app's uid, or -1 if it isn't installed. */
    fun appUid(): Int
}
