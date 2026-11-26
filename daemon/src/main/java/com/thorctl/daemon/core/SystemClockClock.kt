package com.thorctl.daemon.core

import android.os.SystemClock
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(DaemonScope::class)
class SystemClockClock : Clock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}
