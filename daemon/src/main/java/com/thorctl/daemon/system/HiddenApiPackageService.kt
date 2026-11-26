package com.thorctl.daemon.system

import android.app.IActivityManager
import android.content.Context
import android.content.pm.IPackageManager
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
    private val activityManager = HiddenService(Context.ACTIVITY_SERVICE, IActivityManager.Stub::asInterface)
    private val packageManager = HiddenService(HiddenServiceNames.PACKAGE_SERVICE, IPackageManager.Stub::asInterface)

    override fun runningApps(): List<AppProcess> = activityManager.call {
        getRunningAppProcesses().orEmpty().map { info ->
            AppProcess(info.pid, info.uid, info.processName.orEmpty(), info.pkgList?.toList().orEmpty(), info.importance)
        }
    }

    override fun forceStop(packageName: String) = activityManager.call { forceStopPackage(packageName, USER_SYSTEM) }

    override fun killBackground(packageName: String) = activityManager.call { killBackgroundProcesses(packageName, USER_SYSTEM) }

    override fun appFlags(packageName: String): Int? = packageManager.call { getApplicationInfo(packageName, 0L, USER_SYSTEM)?.flags }

    override fun homePackage(): String? = try {
        packageManager.call { getHomeActivities(ArrayList())?.packageName }
    } catch (e: SystemServiceException) {
        Timber.w(e, "getHomeActivities failed")
        null
    }

    private companion object {
        const val USER_SYSTEM = 0
    }
}
