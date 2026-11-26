package com.thorctl.daemon

import com.thorctl.daemon.core.Clock

class FakeClock(var now: Long = 100_000L) : Clock {
    override fun elapsedRealtime() = now

    override fun uptimeMillis() = now
}
