package com.thorctl.daemon.system

import android.app.IActivityManager
import android.content.AttributionSource
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.ipc.ThorIpc
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [AppProviderClient] over IActivityManager.getContentProviderExternal + IContentProvider.call,
 * as root, for [ThorIpc.AUTHORITY].
 */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiAppProviderClient : AppProviderClient {
    /** Its own handle on the activity manager: this call can wait seconds for Android to start the app. */
    private val activityManager = HiddenService(Context.ACTIVITY_SERVICE, IActivityManager.Stub::asInterface)

    override fun call(method: String, extras: Bundle): Bundle? {
        val token = Binder()
        val holder = activityManager.call { getContentProviderExternal(ThorIpc.AUTHORITY, USER_SYSTEM, token, ThorIpc.AUTHORITY) }
            ?: throw AppProviderUnavailableException("provider ${ThorIpc.AUTHORITY} not found")
        try {
            val provider = holder.provider ?: throw AppProviderUnavailableException("provider ${ThorIpc.AUTHORITY} has no binder")
            val source = AttributionSource.Builder(Process.myUid()).build()
            return activityManager.call { provider.call(source, ThorIpc.AUTHORITY, method, null, extras) }
        } finally {
            try {
                activityManager.call { removeContentProviderExternalAsUser(ThorIpc.AUTHORITY, token, USER_SYSTEM) }
            } catch (_: SystemServiceException) {
            }
        }
    }

    private companion object {
        const val USER_SYSTEM = 0
    }
}
