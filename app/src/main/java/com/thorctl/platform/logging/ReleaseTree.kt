package com.thorctl.platform.logging

import android.util.Log
import timber.log.Timber

/**
 * Release builds log info and up under one tag ([TAG]): class names are obfuscated there, so
 * Timber's DebugTree tags would mean nothing. Debug builds tag each line with its class.
 */
class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Log.println(priority, TAG, message)
    }

    companion object {
        const val TAG = "ThorControl"
    }
}
