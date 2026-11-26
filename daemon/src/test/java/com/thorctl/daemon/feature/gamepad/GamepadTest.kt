package com.thorctl.daemon.feature.gamepad

import android.view.KeyEvent
import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.BottomPanelHealer
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.input.TestInputWatcher
import com.thorctl.daemon.ipc.FakeClientLink
import com.thorctl.daemon.ipc.FakeDaemonNotifier
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.FakePowerService
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.GamepadTransport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The coordinator, over Bluetooth (no I/O threads) and with a fake USB gadget. */
class GamepadTest {
    private val sys = FakeSys()
    private val clock = FakeClock()
    private val link = FakeClientLink()
    private val notifier = FakeDaemonNotifier()
    private val inputService = FakeInputService()
    private val powerService = FakePowerService()
    private val displayService = FakeDisplayService()
    private val displays = DisplayController(displayService, inputService, BottomPanelHealer(sys, clock, displayService, FakeScheduledExecutor(clock)))
    private val gadget = FakeUsbGadget()
    private val bluetoothOn = CountDownLatch(1)

    private val gamepad = Gamepad(
        input = TestInputWatcher.create(sys),
        notifier = notifier,
        displays = displays,
        awake = AwakeKeeper(displays, powerService, inputService, sys),
        gadget = gadget,
        usb = UsbHidEndpoint(),
        bluetooth = BluetoothReportSender(link, clock),
        sys = sys,
        clock = clock,
    )

    init {
        sys.shell = { command ->
            if (command == "cmd bluetooth_manager enable") bluetoothOn.countDown()
            null
        }
    }

    private fun reports() = synchronized(link.events) { link.events.filterIsInstance<DaemonEvent.GamepadReport>().map { it.report } }

    private fun onBluetooth() {
        gamepad.init(GamepadTransport.BLUETOOTH, screensOff = true)
        gamepad.setOn(true)
    }

    private fun await(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 3000
        while (!condition()) {
            if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what")
            Thread.sleep(10)
        }
    }

    @Test
    fun turningOnOverBluetoothKeepsAwakeEnablesTheAdapterAndSendsTheCurrentState() {
        onBluetooth()
        assertTrue(gamepad.state.on)
        assertEquals(1, notifier.stateChanges.get())
        assertEquals(AwakeKeeper.WAKE_LOCK, sys.writes.first().first)
        assertEquals(listOf(0), powerService.userActivity)
        assertTrue(bluetoothOn.await(3, TimeUnit.SECONDS))
        assertContentEquals(GamepadHid.neutral(), reports().single())
    }

    @Test
    fun aConnectedHostDarkensTheScreensUntilAynOrDisconnect() {
        onBluetooth()
        gamepad.onBluetooth(true, "MacBook")
        assertTrue(gamepad.state.connected)
        assertEquals("MacBook", gamepad.state.host)
        assertTrue(gamepad.state.dark)
        assertTrue(displays.gamepadDark)
        assertTrue(gamepad.lightUp())
        assertFalse(gamepad.state.dark)
        assertFalse(displays.gamepadDark)
        assertFalse(gamepad.lightUp())
        gamepad.setScreensOff(true) // still connected: dark again
        assertTrue(gamepad.state.dark)
        gamepad.onBluetooth(false, "MacBook")
        assertFalse(gamepad.state.connected)
        assertEquals("", gamepad.state.host)
        assertFalse(gamepad.state.dark)
    }

    @Test
    fun screensStayLitWhenNotWanted() {
        gamepad.init(GamepadTransport.BLUETOOTH, screensOff = false)
        gamepad.setOn(true)
        gamepad.onBluetooth(true, "Pixel")
        assertTrue(gamepad.state.connected)
        assertFalse(gamepad.state.dark)
    }

    @Test
    fun bluetoothNewsIsIgnoredWhenOffOrOnUsb() {
        gamepad.init(GamepadTransport.BLUETOOTH, screensOff = true)
        gamepad.onBluetooth(true, "MacBook")
        assertFalse(gamepad.state.connected)
        gamepad.setTransport(GamepadTransport.USB)
        gamepad.onBluetooth(true, "MacBook")
        assertFalse(gamepad.state.connected)
    }

    @Test
    fun turningOffReleasesEverythingAndTheHostSeesNeutral() {
        onBluetooth()
        gamepad.onBluetooth(true, "MacBook")
        clock.now += 100
        gamepad.setOn(false)
        assertFalse(gamepad.state.on)
        assertFalse(gamepad.state.connected)
        assertFalse(gamepad.state.dark)
        assertFalse(displays.gamepadDark)
        assertEquals(AwakeKeeper.WAKE_UNLOCK to AwakeKeeper.WAKELOCK, sys.writes.last())
        assertEquals(2, reports().size)
        assertContentEquals(GamepadHid.neutral(), reports().last())
    }

    @Test
    fun tickWakesAndroidIfItSleptAnyway() {
        onBluetooth()
        powerService.interactive = false
        gamepad.tick()
        assertEquals(listOf(KeyEvent.KEYCODE_WAKEUP to 0), inputService.injectedKeys)
        assertEquals(listOf(0, 0), powerService.userActivity)
    }

    @Test
    fun tickDoesNothingWhileOff() {
        gamepad.tick()
        assertEquals(emptyList(), sys.writes)
    }

    @Test
    fun aFailedUsbSetUpShowsItsErrorAndTurningOffTearsDown() {
        gadget.setupError = "No USB controller"
        gamepad.init(GamepadTransport.USB, screensOff = true)
        gamepad.setOn(true)
        await("the set-up error") { gamepad.state.error == "No USB controller" }
        gamepad.tick() // an error stops the re-adding
        assertEquals(1, gadget.setups.get())
        gamepad.setOn(false)
        await("the teardown") { gadget.teardowns.get() == 1 }
        assertEquals("", gamepad.state.error)
    }

    @Test
    fun initCleansUpWhatACrashLeft() {
        gadget.leftovers = true
        gamepad.init(GamepadTransport.USB, screensOff = true)
        await("the cleanup") { gadget.teardowns.get() == 1 }
    }

    @Test
    fun initWithNothingLeftDoesNoUsbWork() {
        gamepad.init(GamepadTransport.USB, screensOff = false)
        Thread.sleep(50)
        assertEquals(0, gadget.teardowns.get())
        assertEquals(GamepadTransport.USB, gamepad.state.transport)
        assertFalse(gamepad.state.screensOff)
    }
}
