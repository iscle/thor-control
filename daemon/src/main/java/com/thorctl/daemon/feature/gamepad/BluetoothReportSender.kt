package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.protocol.model.DaemonEvent
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.thread

/**
 * Gamepad reports for Bluetooth. The app owns the Bluetooth HID device registration (it needs a
 * Context), so reports go to it over Binder, newest-wins and at most one per
 * [BLUETOOTH_INTERVAL_MS], so the radio never builds a backlog.
 */
@Inject
@SingleIn(DaemonScope::class)
class BluetoothReportSender(private val link: ClientLink, clock: Clock) {
    private val throttle = ReportThrottle(
        clock,
        BLUETOOTH_INTERVAL_MS,
        schedule = { delayMs, flush ->
            thread(name = "gamepad-bt-tx", isDaemon = true) {
                Thread.sleep(delayMs)
                flush()
            }
        },
        send = { report -> link.broadcast(DaemonEvent.GamepadReport(report)) },
    )

    fun submit(report: ByteArray) = throttle.submit(report)

    companion object {
        /** 125 reports a second at most over Bluetooth, the newest always wins. */
        const val BLUETOOTH_INTERVAL_MS = 8L
    }
}
