package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** Keeps gamepad mode's link to the host up while it is on. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class GamepadTask(private val gamepad: Gamepad) : PeriodicTask {
    override val intervalMs = 2000L

    override fun run() {
        gamepad.tick()
    }
}
