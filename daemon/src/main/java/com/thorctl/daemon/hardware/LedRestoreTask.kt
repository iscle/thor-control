package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.PeriodicTask
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import timber.log.Timber

/**
 * AynParts restores its own RGB lighting from LOCKED_BOOT_COMPLETED, at a moment the daemon
 * can't observe (its process may start well after ours). For the first minutes after start,
 * notice when that overwrote our lights and put ours back; nothing to check after that.
 */
@Inject
@ContributesIntoSet(DaemonScope::class, binding = binding<PeriodicTask>())
class LedRestoreTask(private val leds: LedController, private val clock: Clock) : PeriodicTask {
    private val checkUntil = clock.elapsedRealtime() + CHECK_WINDOW_MS

    override val intervalMs = 2000L

    override fun run() {
        if (clock.elapsedRealtime() > checkUntil || !leds.overwritten()) return
        Timber.i("LEDs changed by another app, putting ours back")
        leds.invalidate()
    }

    private companion object {
        const val CHECK_WINDOW_MS = 180_000L
    }
}
