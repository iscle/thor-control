package com.thorctl.daemon.core

import android.util.Log
import timber.log.Timber

/**
 * The daemon logs to logcat under one tag ([TAG], `logcat -s thorctld`), whichever class writes,
 * like before Timber. Its stdout / stderr (crashes) go to /data/adb/thorctl/daemon.log through
 * service.sh.
 */
class DaemonLogTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Log.println(priority, TAG, message)
    }

    companion object {
        const val TAG = "thorctld"

        fun plant() {
            if (Timber.treeCount == 0) Timber.plant(DaemonLogTree())
        }
    }
}
