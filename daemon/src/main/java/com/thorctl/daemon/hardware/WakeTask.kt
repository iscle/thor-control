package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.system.PowerService
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Follows the device going to sleep and waking up: the LEDs dim, and displays we hold off stay off. */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class WakeTask(
    private val powerService: PowerService,
    private val leds: LedController,
    private val displays: DisplayController,
    private val scheduler: ScheduledExecutorService,
) : PeriodicTask {
    override val intervalMs = 2000L

    private var wasInteractive = true

    override fun run() {
        val interactive = powerService.isInteractive()
        if (interactive == wasInteractive) return
        wasInteractive = interactive
        leds.setAwake(interactive)
        if (!interactive) return
        displays.onWake()
        if (displays.bottomForcedOff || displays.gamepadDark) {
            // Waking powers every display back on; hold ours off again.
            for (delayMs in REASSERT_DELAYS_MS) scheduler.schedule({ displays.reassertOverrides() }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        val REASSERT_DELAYS_MS = longArrayOf(150, 600, 1500)
    }
}
