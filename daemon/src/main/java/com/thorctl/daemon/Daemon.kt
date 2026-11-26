package com.thorctl.daemon

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.control.ButtonHandler
import com.thorctl.daemon.control.DaemonActions
import com.thorctl.daemon.control.DaemonLock
import com.thorctl.daemon.control.KeyboardPresence
import com.thorctl.daemon.control.SystemUpkeep
import com.thorctl.daemon.feature.GameMode
import com.thorctl.daemon.feature.Gamepad
import com.thorctl.daemon.feature.Recents
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputRouter
import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.daemon.hardware.LedController
import com.thorctl.daemon.hardware.StatsCollector
import com.thorctl.daemon.ipc.BinderPublisher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.SystemServices
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** The daemon's life: start everything, then the periodic work. Calls arrive on binder threads. */
@Inject
@SingleIn(DaemonScope::class)
class Daemon(
    private val config: ConfigStore,
    private val displays: DisplayController,
    private val leds: LedController,
    private val router: InputRouter,
    private val stats: StatsCollector,
    private val input: InputWatcher,
    private val game: GameMode,
    private val gamepad: Gamepad,
    private val recents: Recents,
    private val actions: DaemonActions,
    private val buttons: ButtonHandler,
    private val keyboard: KeyboardPresence,
    private val upkeep: SystemUpkeep,
    private val publisher: BinderPublisher,
    private val link: ClientLink,
    private val notifier: DaemonNotifier,
    private val system: SystemServices,
    private val lock: DaemonLock,
    private val scheduler: ScheduledExecutorService,
) {
    private var lastInteractive = true
    private var tickCount = 0L

    /** Never returns. */
    fun run() {
        while (!system.servicesReady()) {
            Timber.i("waiting for system services")
            Thread.sleep(1000)
        }
        synchronized(lock) {
            config.load()
            displays.refresh()
            game.init(config.gameOptions)
            gamepad.init(config.gamepadTransport, config.gamepadScreensOff)
            displays.setBottomTouch(true) // never leave touch disabled from a previous crash

            leds.apply(config.leds)
            leds.start()
            router.apply(config.controller)
        }

        every(1000) { actions.fanTick() }
        every(1000) { statsTick() }
        every(2000) { housekeeping() }
        // AynParts restores its own light/focus prefs on BOOT_COMPLETED; win that race.
        scheduler.schedule({ reapply() }, 20, TimeUnit.SECONDS)

        input.start(buttons)
        publisher.offer()
        Timber.i("daemon ready (leds=${leds.available} top=${displays.topId} bottom=${displays.bottomId})")
        // Everything else happens on binder threads, the input threads and the scheduler.
        CountDownLatch(1).await()
    }

    private fun every(intervalMs: Long, block: () -> Unit) {
        scheduler.scheduleWithFixedDelay({
            try {
                block()
            } catch (error: Throwable) {
                Timber.w(error, "periodic task failed")
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun statsTick() {
        if (link.hasStatsSubscribers) link.broadcastStats(stats.collect())
    }

    private fun housekeeping() {
        tickCount++
        val interactive = system.isInteractive()
        if (interactive != lastInteractive) {
            lastInteractive = interactive
            leds.setAwake(interactive)
            if (interactive) displays.onWake()
            if (interactive && (displays.bottomForcedOff || displays.gamepadDark)) {
                // Waking powers every display back on; hold ours off again.
                for (delay in longArrayOf(150, 600, 1500)) scheduler.schedule({ displays.reassertOverrides() }, delay, TimeUnit.MILLISECONDS)
            }
        }
        publisher.offerIfNeeded()
        if (interactive) displays.healBottomPanel()
        if (recents.isOpen && (!interactive || !link.appConnected)) recents.close()
        if (!link.appConnected) keyboard.visible = false
        game.tick(interactive)
        gamepad.tick()
        if (tickCount % 5 == 0L) {
            val before = displays.bottomId
            displays.refresh()
            if (before != displays.bottomId) notifier.stateChanged()
        }
        if (tickCount % 15 == 1L && config.keepAccessibility) upkeep.ensureAccessibility()
        if (tickCount % 15 == 2L) upkeep.keepServicesStopped()
        if (tickCount % 10 == 4L && !recents.isOpen) recents.maybeCaptureHome(interactive)
        if (tickCount % 5 == 3L && upkeep.moduleTurnedOff()) {
            Timber.i("module disabled or removed, exiting")
            game.setEnabled(false)
            gamepad.shutdown()
            exitProcess(0)
        }
    }

    private fun reapply() {
        try {
            leds.invalidate()
            router.apply(config.controller)
        } catch (error: Throwable) {
            Timber.w(error, "reapply failed")
        }
    }
}
