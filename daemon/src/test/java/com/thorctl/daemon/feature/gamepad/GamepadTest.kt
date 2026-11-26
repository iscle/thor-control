package com.thorctl.daemon.feature.gamepad

import android.view.KeyEvent
import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.awaitUntil
import com.thorctl.daemon.hardware.BottomPanelHealer
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.daemon.hardware.input.FakeEvdev
import com.thorctl.daemon.hardware.input.FakeTouchscreens
import com.thorctl.daemon.hardware.input.TestInputDevices
import com.thorctl.daemon.hardware.input.TestInputWatcher
import com.thorctl.daemon.ipc.FakeClientLink
import com.thorctl.daemon.ipc.FakeDaemonNotifier
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import com.thorctl.daemon.system.FakePowerService
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.GamepadTransport
import org.junit.After
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The coordinator over fakes: Bluetooth to the app, USB through a fake gadget and /dev/hidg0. */
class GamepadTest {
    private val sys = FakeSys().apply { TestInputDevices.install(this, TestInputDevices.POWER_KEY) }
    private val clock = FakeClock()
    private val link = FakeClientLink()
    private val notifier = FakeDaemonNotifier()
    private val evdev = FakeEvdev()
    private val inputService = FakeInputService()
    private val powerService = FakePowerService()
    private val displayService = FakeDisplayService()
    private val displays = DisplayController(displayService, FakeTouchscreens(), BottomPanelHealer(sys, clock, displayService, FakeScheduledExecutor(clock)))
    private val gadget = FakeUsbGadget()
    private val hidg = FakeHidgNode()
    private val usb = UsbHidEndpoint(hidg)

    /** Shell commands run, from any thread. */
    private val commands = CopyOnWriteArrayList<String>()

    private val gamepad = Gamepad(
        input = TestInputWatcher.create(sys, evdev),
        notifier = notifier,
        displays = displays,
        awake = AwakeKeeper(displays, powerService, inputService, sys),
        gadget = gadget,
        usb = usb,
        bluetooth = BluetoothReportSender(link, clock),
        bluetoothIds = BluetoothIdentity(sys),
        sys = sys,
        clock = clock,
    )

    init {
        sys.shell = { command ->
            commands += command
            null
        }
    }

    @After
    fun turnOff() {
        gamepad.shutdown()
    }

    private fun reports() = synchronized(link.events) { link.events.filterIsInstance<DaemonEvent.GamepadReport>().map { it.report } }

    private fun onBluetooth(screensOff: Boolean = true) {
        gamepad.init(GamepadTransport.BLUETOOTH, screensOff)
        gamepad.setOn(true)
    }

    private fun onUsb() {
        gamepad.init(GamepadTransport.USB, screensOff = true)
        gamepad.setOn(true)
        awaitUntil("the USB writer") { hidg.written.isNotEmpty() }
    }

    /** The power button, as grabbed for the [session]th time (each gamepad mode start grabs it again). */
    private fun power(down: Boolean, session: Int = 1) =
        evdev.awaitOpened(TestInputDevices.POWER_NODE, session).send(EvdevCodes.EV_KEY, EvdevCodes.KEY_POWER, if (down) 1 else 0)

    /** Long enough for a hold timer that wasn't cancelled to have fired. */
    private fun waitPastTheHold() = Thread.sleep(PowerButton.LONG_PRESS_MS + 300)

    @Test
    fun turningOnOverBluetoothKeepsAwakeEnablesTheAdapterAndSendsTheCurrentState() {
        onBluetooth()
        assertTrue(gamepad.state.on)
        assertEquals(1, notifier.stateChanges.get())
        assertEquals(AwakeKeeper.WAKE_LOCK, sys.writes.first().first)
        assertEquals(listOf(0), powerService.userActivity)
        awaitUntil("the Bluetooth adapter") { "cmd bluetooth_manager enable" in commands }
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
        onBluetooth(screensOff = false)
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
    fun overUsbTheHostGetsReportsAndConnectsThroughTheGadget() {
        onUsb()
        assertContentEquals(byteArrayOf(GamepadHid.REPORT_ID.toByte()) + GamepadHid.neutral(), hidg.written.first())
        gamepad.tick()
        assertFalse(gamepad.state.connected)
        gadget.configured = true
        gamepad.tick()
        assertTrue(gamepad.state.connected)
        assertTrue(gamepad.state.dark)
    }

    @Test
    fun aFailedUsbSetUpShowsItsErrorAndTurningOffTearsDown() {
        gadget.setupError = "No USB controller"
        gamepad.init(GamepadTransport.USB, screensOff = true)
        gamepad.setOn(true)
        awaitUntil("the set-up error") { gamepad.state.error == "No USB controller" }
        gamepad.tick() // an error stops the re-adding
        assertEquals(1, gadget.setups.get())
        gamepad.setOn(false)
        awaitUntil("the teardown") { gadget.teardowns.get() == 1 }
        assertEquals("", gamepad.state.error)
    }

    @Test
    fun shutdownClosesHidgBeforeTheGadgetGoesAndDoesNotLinger() {
        onUsb()
        var openAtTeardown = -1
        gadget.onRemove = { openAtTeardown = hidg.openNow }
        val startedAt = System.currentTimeMillis()
        gamepad.shutdown()
        val took = System.currentTimeMillis() - startedAt
        assertEquals(1, gadget.teardowns.get())
        assertEquals(0, openAtTeardown)
        // A writer left asleep waits up to a second for a report; shutdown wakes it.
        assertTrue(took < 600, "shutdown took ${took}ms")
    }

    @Test
    fun shutdownLeavesNothingStale() {
        onUsb()
        gadget.configured = true
        gamepad.tick()
        assertTrue(gamepad.state.dark)
        gamepad.shutdown()
        val state = gamepad.state
        assertFalse(state.on)
        assertFalse(state.connected)
        assertFalse(state.dark)
        assertEquals("", state.host)
        assertEquals("", state.error)
        assertFalse(displays.gamepadDark)
        assertEquals(AwakeKeeper.WAKE_UNLOCK to AwakeKeeper.WAKELOCK, sys.writes.last())
        assertEquals(GamepadTransport.USB, state.transport)
        assertTrue(state.screensOff)
    }

    @Test
    fun holdingPowerOpensThePowerMenu() {
        onBluetooth()
        power(true)
        awaitUntil("the power menu") { Gamepad.POWER_MENU_COMMAND in commands }
        power(false)
        assertFalse(gamepad.state.dark)
    }

    @Test
    fun aQuickPowerPressTogglesDark() {
        onBluetooth()
        power(true)
        power(false)
        assertTrue(gamepad.state.dark)
        power(true)
        power(false)
        assertFalse(gamepad.state.dark)
    }

    @Test
    fun turningOffMidHoldNeverOpensThePowerMenu() {
        onBluetooth()
        power(true)
        gamepad.setOn(false)
        waitPastTheHold()
        assertFalse(Gamepad.POWER_MENU_COMMAND in commands)
    }

    @Test
    fun switchingTransportMidHoldNeverOpensThePowerMenuAndSpendsThePress() {
        onBluetooth()
        power(true)
        gamepad.setTransport(GamepadTransport.USB)
        waitPastTheHold()
        assertFalse(Gamepad.POWER_MENU_COMMAND in commands)
        // The release belongs to the press before the switch: it doesn't darken the screens.
        power(false, session = 2)
        assertFalse(gamepad.state.dark)
    }

    @Test
    fun initCleansUpWhatACrashLeft() {
        gadget.leftovers = true
        gamepad.init(GamepadTransport.USB, screensOff = true)
        awaitUntil("the cleanup") { gadget.teardowns.get() == 1 }
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
