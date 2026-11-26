package com.thorctl.daemon.system

import android.content.AttributionSource
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
    /**
     * Its own handle on the activity manager: this call can wait seconds for Android to start the
     * app, and must not hold the lock that game mode's process calls use meanwhile.
     */
    private val activityManager = SystemServiceProxy("activity", "android.app.IActivityManager")

    override fun call(method: String, extras: Bundle): Bundle? {
        val token = Binder()
        val holder = activityManager.call("getContentProviderExternal", ThorIpc.AUTHORITY, USER_SYSTEM, token, ThorIpc.AUTHORITY)
            ?: throw IllegalStateException("provider ${ThorIpc.AUTHORITY} not found")
        try {
            val provider = holder.hiddenField("provider") ?: throw IllegalStateException("provider ${ThorIpc.AUTHORITY} has no binder")
            val call = provider.javaClass.methods.first { it.name == "call" && it.parameterTypes.size == 5 }
            val source = AttributionSource.Builder(Process.myUid()).build()
            return call.invoke(provider, source, ThorIpc.AUTHORITY, method, null, extras) as Bundle?
        } finally {
            try {
                activityManager.call("removeContentProviderExternalAsUser", ThorIpc.AUTHORITY, token, USER_SYSTEM)
            } catch (_: Throwable) {
            }
        }
    }

    private companion object {
        const val USER_SYSTEM = 0
    }
}
