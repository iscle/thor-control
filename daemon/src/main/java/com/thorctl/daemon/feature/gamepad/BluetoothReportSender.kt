package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.loggedRunnable
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.protocol.model.DaemonEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Gamepad reports for Bluetooth. The app owns the Bluetooth HID device registration (it needs a
 * Context), so reports go to it over Binder, newest-wins and at most one per
 * [BLUETOOTH_INTERVAL_MS], so the radio never builds a backlog.
 */
@Inject
@SingleIn(DaemonScope::class)
class BluetoothReportSender(private val link: ClientLink, clock: Clock) {
    /** Sends a report that had to wait for the interval; at most one waits at a time. */
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "gamepad-bt-tx").apply { isDaemon = true } }

    private val throttle = ReportThrottle(
        clock,
        BLUETOOTH_INTERVAL_MS,
        schedule = { delayMs, flush -> timer.schedule(loggedRunnable("bluetooth report", flush), delayMs, TimeUnit.MILLISECONDS) },
        send = { report -> link.broadcast(DaemonEvent.GamepadReport(report)) },
    )

    fun submit(report: ByteArray) = throttle.submit(report)

    companion object {
        /** 125 reports a second at most over Bluetooth, the newest always wins. */
        const val BLUETOOTH_INTERVAL_MS = 8L
    }
}
