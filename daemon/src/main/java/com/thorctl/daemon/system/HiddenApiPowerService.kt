package com.thorctl.daemon.system

import android.content.Context
import android.os.SystemClock
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [PowerService] over IPowerManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiPowerService : PowerService {
    private val service = SystemServiceProxy(Context.POWER_SERVICE, "android.os.IPowerManager")

    override fun isInteractive(): Boolean = try {
        service.isInteractive()
    } catch (_: ReflectiveOperationException) {
        true
    }

    override fun userActivity(displayId: Int) {
        service.userActivity(displayId, SystemClock.uptimeMillis(), USER_ACTIVITY_EVENT_OTHER, flags = 0)
    }

    private companion object {
        const val USER_ACTIVITY_EVENT_OTHER = 0
    }
}
