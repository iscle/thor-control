package com.thorctl.daemon.system

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/** [PackageService] over IActivityManager and IPackageManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiPackageService : PackageService {
    private val activityManager = SystemServiceProxy("activity", "android.app.IActivityManager")
    private val packageManager = SystemServiceProxy("package", "android.content.pm.IPackageManager")

    override fun runningApps(): List<AppProcess> {
        val list = activityManager.call("getRunningAppProcesses") as? List<*> ?: return emptyList()
        return list.filterIsInstance<ActivityManager.RunningAppProcessInfo>().map { info ->
            AppProcess(info.pid, info.uid, info.processName ?: "", info.pkgList?.toList() ?: emptyList(), info.importance)
        }
    }

    override fun forceStop(packageName: String) {
        activityManager.call("forceStopPackage", packageName, USER_SYSTEM)
    }

    override fun killBackground(packageName: String) {
        activityManager.call("killBackgroundProcesses", packageName, USER_SYSTEM)
    }

    override fun appFlags(packageName: String): Int? {
        val service = packageManager.get() ?: return null
        val getInfo = service.javaClass.methods.firstOrNull { it.name == "getApplicationInfo" && it.parameterTypes.size == 3 } ?: return null
        // The flags argument became a long in Android 13.
        val flags: Any = if (getInfo.parameterTypes[1] == Long::class.javaPrimitiveType) 0L else 0
        return (getInfo.invoke(service, packageName, flags, USER_SYSTEM) as? ApplicationInfo)?.flags
    }

    override fun homePackage(): String? = try {
        (packageManager.call("getHomeActivities", ArrayList<ResolveInfo>()) as? ComponentName)?.packageName
    } catch (e: Throwable) {
        Timber.w(e, "getHomeActivities failed")
        null
    }

    private companion object {
        const val USER_SYSTEM = 0
    }
}
