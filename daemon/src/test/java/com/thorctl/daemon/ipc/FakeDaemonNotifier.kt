package com.thorctl.daemon.ipc

import com.thorctl.protocol.model.HudEvent
import java.util.concurrent.atomic.AtomicInteger

class FakeDaemonNotifier : DaemonNotifier {
    val stateChanges = AtomicInteger()
    val huds = mutableListOf<Pair<HudEvent, Int>>()

    override fun stateChanged() {
        stateChanges.incrementAndGet()
    }

    override fun hud(event: HudEvent, displayId: Int) {
        huds += event to displayId
    }
}
