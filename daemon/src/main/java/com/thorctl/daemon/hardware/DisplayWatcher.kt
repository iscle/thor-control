package com.thorctl.daemon.hardware

import com.thorctl.daemon.core.WakeListener
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.DisplayChange
import com.thorctl.daemon.system.DisplayChangeListener
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.PowerService
import com.thorctl.daemon.system.SystemServiceException
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Follows the displays through the display manager's events: displays that come and go (the
 * bottom panel can disappear while it is off), a panel we hold off being powered on again
 * (waking powers every panel on), and the device going to sleep or waking up, which every
 * [WakeListener] is told about. Every sleep and wake changes some panel's power state, so the
 * state events are enough to notice them.
 */
@Inject
@SingleIn(DaemonScope::class)
class DisplayWatcher(
    private val displayService: DisplayService,
    private val displays: DisplayController,
    private val powerService: PowerService,
    private val notifier: DaemonNotifier,
    private val wakeListeners: Lazy<Set<WakeListener>>,
) : DisplayChangeListener {
    private var awake = true

    /** Starts listening, or listens again if the display manager restarted since. */
    fun watch() {
        try {
            displayService.watch(this)
        } catch (e: SystemServiceException) {
            Timber.w(e, "no display events")
        }
    }

    @Synchronized
    override fun onDisplayChanged(displayId: Int, change: DisplayChange) {
        when (change) {
            DisplayChange.ADDED, DisplayChange.REMOVED -> {
                val bottomBefore = displays.bottomId
                displays.refresh()
                if (bottomBefore != displays.bottomId) notifier.stateChanged()
            }
            DisplayChange.STATE -> {
                displays.onDisplayStateChanged(displayId)
                followWake()
            }
            DisplayChange.BRIGHTNESS -> displays.onBrightnessChanged(displayId)
        }
    }

    private fun followWake() {
        val interactive = powerService.isInteractive()
        if (interactive == awake) return
        awake = interactive
        Timber.i(if (interactive) "woke up" else "went to sleep")
        wakeListeners.value.forEach { it.onWakeChanged(interactive) }
    }
}
