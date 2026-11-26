package com.thorctl.daemon.system

import android.content.Context
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
    private val activityManager = SystemServiceProxy(Context.ACTIVITY_SERVICE, "android.app.IActivityManager")
    private val packageManager = SystemServiceProxy(HiddenServiceNames.PACKAGE_SERVICE, "android.content.pm.IPackageManager")

    override fun runningApps(): List<AppProcess> {
        return activityManager.getRunningAppProcesses().map { info ->
            AppProcess(info.pid, info.uid, info.processName ?: "", info.pkgList?.toList() ?: emptyList(), info.importance)
        }
    }

    override fun forceStop(packageName: String) {
        activityManager.forceStopPackage(packageName, USER_SYSTEM)
    }

    override fun killBackground(packageName: String) {
        activityManager.killBackgroundProcesses(packageName, USER_SYSTEM)
    }

    override fun appFlags(packageName: String): Int? = packageManager.getApplicationInfo(packageName, USER_SYSTEM)?.flags

    override fun homePackage(): String? = try {
        packageManager.getHomeActivities()?.packageName
    } catch (e: Throwable) {
        Timber.w(e, "getHomeActivities failed")
        null
    }

    private companion object {
        const val USER_SYSTEM = 0
    }
}
