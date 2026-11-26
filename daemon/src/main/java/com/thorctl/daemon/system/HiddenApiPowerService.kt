package com.thorctl.daemon.system

import android.content.Context
import android.os.IPowerManager
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
    private val service = HiddenService(Context.POWER_SERVICE, IPowerManager.Stub::asInterface)

    override fun isInteractive(): Boolean = try {
        service.call { isInteractive() }
    } catch (_: SystemServiceException) {
        true
    }

    override fun userActivity(displayId: Int) =
        service.call { userActivity(displayId, SystemClock.uptimeMillis(), USER_ACTIVITY_EVENT_OTHER, 0) }

    private companion object {
        const val USER_ACTIVITY_EVENT_OTHER = 0
    }
}
