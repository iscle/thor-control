package com.thorctl.daemon.system

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import java.lang.reflect.Method

/** android.content.pm.IPackageManager (hidden), resolved once. */
private val packageManager: Class<*> by lazy { Class.forName("android.content.pm.IPackageManager") }
private val getApplicationInfoMethod: Method by lazy { packageManager.method("getApplicationInfo", 3) }
private val getHomeActivitiesMethod: Method by lazy { packageManager.method("getHomeActivities", 1) }

/** getApplicationInfo's flags parameter became a long in Android 13. */
private val flagsAreLong: Boolean by lazy { getApplicationInfoMethod.parameterTypes[1] == Long::class.javaPrimitiveType }

internal fun SystemServiceProxy.getApplicationInfo(packageName: String, userId: Int): ApplicationInfo? =
    call(getApplicationInfoMethod, packageName, if (flagsAreLong) 0L else 0, userId) as? ApplicationInfo

/** The default home activity; [candidates] receives every home activity. */
internal fun SystemServiceProxy.getHomeActivities(candidates: MutableList<ResolveInfo> = ArrayList()): ComponentName? =
    call(getHomeActivitiesMethod, candidates) as? ComponentName
