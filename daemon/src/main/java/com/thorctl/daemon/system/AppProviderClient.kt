package com.thorctl.daemon.system

import android.os.Bundle

/** Calls the app's ContentProvider, as `content call` would: how the daemon hands the app its binder. */
interface AppProviderClient {
    /**
     * Calls [method] with [extras], starting the app's process if needed. Returns the reply.
     * Throws [AppProviderUnavailableException] if the app's provider isn't there, and a
     * [SystemServiceException] if the call itself fails.
     */
    fun call(method: String, extras: Bundle): Bundle?
}
