package com.thorctl.daemon.system

import android.app.ActivityManager
import android.os.IBinder
import java.lang.reflect.Method

/** android.app.IActivityManager (hidden), resolved once. */
private val activityManager: Class<*> by lazy { Class.forName("android.app.IActivityManager") }
private val getRunningAppProcessesMethod: Method by lazy { activityManager.method("getRunningAppProcesses", 0) }
private val forceStopPackageMethod: Method by lazy { activityManager.method("forceStopPackage", 2) }
private val killBackgroundProcessesMethod: Method by lazy { activityManager.method("killBackgroundProcesses", 2) }
private val getContentProviderExternalMethod: Method by lazy { activityManager.method("getContentProviderExternal", 4) }
private val removeContentProviderExternalAsUserMethod: Method by lazy { activityManager.method("removeContentProviderExternalAsUser", 3) }

internal fun SystemServiceProxy.getRunningAppProcesses(): List<ActivityManager.RunningAppProcessInfo> =
    (call(getRunningAppProcessesMethod) as? List<*>)?.filterIsInstance<ActivityManager.RunningAppProcessInfo>() ?: emptyList()

internal fun SystemServiceProxy.forceStopPackage(packageName: String, userId: Int) {
    call(forceStopPackageMethod, packageName, userId)
}

internal fun SystemServiceProxy.killBackgroundProcesses(packageName: String, userId: Int) {
    call(killBackgroundProcessesMethod, packageName, userId)
}

/** A ContentProviderHolder (see ContentProviderHolderKtx.kt), or null if there is no such provider. */
internal fun SystemServiceProxy.getContentProviderExternal(authority: String, userId: Int, token: IBinder, tag: String): Any? =
    call(getContentProviderExternalMethod, authority, userId, token, tag)

internal fun SystemServiceProxy.removeContentProviderExternalAsUser(authority: String, token: IBinder, userId: Int) {
    call(removeContentProviderExternalAsUserMethod, authority, token, userId)
}
