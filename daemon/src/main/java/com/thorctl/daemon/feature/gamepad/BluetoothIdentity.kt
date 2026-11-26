package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.core.loggedRunnable
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The Bluetooth Device ID the Thor announces. In Bluetooth gamepad mode it is a Stadia
 * Controller's (USB vendor 18D1, product 9400, version 1.00), the IDs macOS needs to accept a game
 * controller, and the same ones the USB gadget uses (see GamepadHid); otherwise the device's own.
 * The Bluetooth stack reads them when it starts, so each change restarts Bluetooth, and other
 * Bluetooth devices reconnect a few seconds later. The device's own values are kept in
 * [ORIGINALS], so restore-kernel.sh puts them back if the daemon dies meanwhile. Changes run in
 * order on a thread of their own (restarting Bluetooth takes seconds).
 */
@SingleIn(DaemonScope::class)
class BluetoothIdentity(private val sys: Sys, private val worker: ExecutorService) {
    @Inject
    constructor(sys: Sys) : this(sys, Executors.newSingleThreadExecutor { Thread(it, "gamepad-bt-id").apply { isDaemon = true } })

    /** Bluetooth gamepad mode starts: announce the Stadia IDs, with Bluetooth on. */
    fun useGamepadIds(): Future<*> = worker.submit(
        loggedRunnable("Bluetooth gamepad IDs") {
            if (!sys.exists(ORIGINALS)) saveOriginals()
            for ((name, value) in STADIA) sys.setProp(name, value)
            restartBluetooth()
            Timber.i("bluetooth: announcing the gamepad's IDs")
        },
    )

    /** Back to the device's own IDs; nothing to do if they weren't changed. */
    fun useOwnIds(): Future<*> = worker.submit(
        loggedRunnable("Bluetooth own IDs") {
            val saved = sys.read(ORIGINALS) ?: return@loggedRunnable
            for (line in saved.lines()) {
                val name = line.substringBefore('=', "")
                if (name.isNotEmpty()) sys.setProp(name, line.substringAfter('='))
            }
            sys.remove(ORIGINALS)
            if (bluetoothOn()) restartBluetooth()
            Timber.i("bluetooth: announcing the device's own IDs again")
        },
    )

    private fun saveOriginals() {
        sys.mkdirs(ORIGINALS.substringBeforeLast('/'))
        sys.write(ORIGINALS, STADIA.keys.joinToString("\n") { "$it=${sys.prop(it)}" })
    }

    private fun bluetoothOn(): Boolean = sys.sh("settings get global bluetooth_on") == "1"

    /** Off (if on) and on again, so the stack reads the IDs; on in any case. */
    private fun restartBluetooth() {
        if (bluetoothOn()) {
            sys.sh("cmd bluetooth_manager disable")
            sys.sh("cmd bluetooth_manager wait-for-state:STATE_OFF", BLUETOOTH_STATE_TIMEOUT_MS)
        }
        sys.sh("cmd bluetooth_manager enable")
    }

    companion object {
        const val ORIGINALS = "/dev/thorctl/bt-device-id.orig"
        const val BLUETOOTH_STATE_TIMEOUT_MS = 10_000L

        /** The Device ID record's properties: source 2 = a USB vendor ID. */
        val STADIA = linkedMapOf(
            "bluetooth.device_id.vendor_id_source" to "2",
            "bluetooth.device_id.vendor_id" to "6353",
            "bluetooth.device_id.product_id" to "37888",
            "bluetooth.device_id.version" to "256",
        )
    }
}
