package com.thorctl.daemon

import com.thorctl.daemon.config.ConfigStore
import com.thorctl.daemon.control.ButtonHandler
import com.thorctl.daemon.control.DaemonLock
import com.thorctl.daemon.core.PeriodicTaskRunner
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.feature.gamemode.GameMode
import com.thorctl.daemon.feature.gamepad.Gamepad
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputRouter
import com.thorctl.daemon.hardware.LedController
import com.thorctl.daemon.hardware.input.InputWatcher
import com.thorctl.daemon.ipc.BinderPublisher
import com.thorctl.daemon.system.SystemReadiness
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * The daemon's startup: restore the saved settings, start the periodic work and the input
 * readers, then offer the app our binder. From then on everything happens on binder threads, the
 * input threads and the scheduler.
 */
@Inject
@SingleIn(DaemonScope::class)
class Daemon(
    private val readiness: SystemReadiness,
    private val lock: DaemonLock,
    private val config: ConfigStore,
    private val displays: DisplayController,
    private val leds: LedController,
    private val router: InputRouter,
    private val game: GameMode,
    private val gamepad: Gamepad,
    private val periodicTasks: PeriodicTaskRunner,
    private val input: InputWatcher,
    private val buttons: ButtonHandler,
    private val publisher: BinderPublisher,
    private val scheduler: ScheduledExecutorService,
) {
    /** Never returns. */
    fun run() {
        waitForSystemServices()
        restoreSettings()
        periodicTasks.start()
        // AynParts restores its own light and controller prefs on BOOT_COMPLETED; win that race.
        scheduler.schedule(::reapplyOverVendorDefaults, REAPPLY_DELAY_S, TimeUnit.SECONDS)
        input.start(buttons)
        publisher.offer()
        Timber.i("daemon ready (leds=${leds.available} top=${displays.topId} bottom=${displays.bottomId})")
        CountDownLatch(1).await()
    }

    private fun waitForSystemServices() {
        while (!readiness.servicesReady()) {
            Timber.i("waiting for system services")
            Thread.sleep(1000)
        }
    }

    private fun restoreSettings() = synchronized(lock) {
        config.load()
        displays.refresh()
        game.init(config.gameOptions)
        gamepad.init(config.gamepadTransport, config.gamepadScreensOff)
        leds.apply(config.leds)
        leds.start()
        router.apply(config.controller)
    }

    private fun reapplyOverVendorDefaults() {
        leds.invalidate()
        router.apply(config.controller)
    }

    private companion object {
        const val REAPPLY_DELAY_S = 20L
    }
}
