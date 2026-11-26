package com.thorctl.daemon.system

import android.os.Bundle

/** Calls the app's ContentProvider, as `content call` would: how the daemon hands the app its binder. */
interface AppProviderClient {
    /** Calls [method] with [extras], starting the app's process if needed. Returns the reply. */
    fun call(method: String, extras: Bundle): Bundle?
}
