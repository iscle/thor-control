package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.FakeSys
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BluetoothIdentityTest {
    /** Runs every change on the calling thread. */
    private class DirectExecutor : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() {}
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private var bluetoothOn = "1"
    private val sys = FakeSys().apply {
        properties["bluetooth.device_id.vendor_id"] = "0x001D"
        shell = { command -> if (command == "settings get global bluetooth_on") bluetoothOn else null }
    }
    private val identity = BluetoothIdentity(sys, DirectExecutor())

    @Test
    fun gamepadModeAnnouncesTheStadiaIdsAndRestartsBluetooth() {
        identity.useGamepadIds()
        assertEquals(BluetoothIdentity.STADIA, sys.properties.filterKeys { it.startsWith("bluetooth.device_id.") })
        assertEquals(
            listOf("cmd bluetooth_manager disable", "cmd bluetooth_manager wait-for-state:STATE_OFF", "cmd bluetooth_manager enable"),
            sys.commands.filter { it.startsWith("cmd bluetooth_manager") },
        )
    }

    @Test
    fun theDevicesOwnIdsComeBack() {
        identity.useGamepadIds()
        identity.useGamepadIds() // switching transports: the originals are saved once
        sys.commands.clear()
        identity.useOwnIds()
        assertEquals("0x001D", sys.properties["bluetooth.device_id.vendor_id"])
        assertEquals("", sys.properties["bluetooth.device_id.product_id"])
        assertNull(sys[BluetoothIdentity.ORIGINALS])
        assertEquals("cmd bluetooth_manager enable", sys.commands.last())
    }

    @Test
    fun nothingHappensWhenTheIdsWereNeverChanged() {
        identity.useOwnIds()
        assertFalse(sys.commands.any { it.startsWith("cmd bluetooth_manager") })
    }

    @Test
    fun bluetoothOffIsJustSwitchedOn() {
        bluetoothOn = "0"
        identity.useGamepadIds()
        assertEquals(listOf("cmd bluetooth_manager enable"), sys.commands.filter { it.startsWith("cmd bluetooth_manager") })
    }
}
