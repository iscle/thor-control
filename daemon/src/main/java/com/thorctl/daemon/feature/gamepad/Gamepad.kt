package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Clock
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.input.InputWatcher
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * Gamepad mode: the built-in controller drives another device as a HID gamepad that hosts know
 * as a Stadia Controller ([GamepadHid] explains why).
 *
 * The controller is grabbed exclusively (nothing reaches Android) and every evdev frame becomes a
 * HID report ([ControllerReports]) the moment its SYN_REPORT arrives:
 *
 *  - USB: an f_hid function is added next to adb in the USB gadget ([UsbGadget]), and the gadget
 *    takes the gamepad's vendor / product ID while it is there, so a computer sees a composite
 *    device with a gamepad. Reports go out through /dev/hidg0 ([UsbHidEndpoint]).
 *  - Bluetooth: the app owns the Bluetooth HID device registration (it needs a Context). Reports
 *    go to it over Binder, throttled ([BluetoothReportSender]).
 *
 * While gamepad mode is on, Android must stay awake: the controller's driver cuts the
 * controller's power when both panels are off, and Android's sleep turns both off. So user
 * activity is reported on every tick (no screen timeout, [AwakeKeeper]), the power button is taken
 * over (a press darkens the screens or brings them back, a long press opens the power menu,
 * [PowerButton]), and "screens off" means dark ([DisplayController.setGamepadDark]): top panel
 * off, bottom panel black. A kernel wakelock keeps the SoC out of suspend besides.
 *
 * This class is the coordinator: it owns [state] and decides when each part starts and stops.
 */
@Inject
@SingleIn(DaemonScope::class)
class Gamepad(
    private val input: InputWatcher,
    private val notifier: DaemonNotifier,
    private val displays: DisplayController,
    private val awake: AwakeKeeper,
    private val gadget: UsbGadget,
    private val usb: UsbHidEndpoint,
    private val bluetooth: BluetoothReportSender,
    private val bluetoothIds: BluetoothIdentity,
    private val sys: Sys,
    private val clock: Clock,
) {
    @Volatile
    var state = GamepadState()
        private set

    private var grabbedAt = 0L

    private val reports = ControllerReports(::submit)

    private val powerTimer = Executors.newSingleThreadScheduledExecutor { Thread(it, "gamepad-pwr").apply { isDaemon = true } }

    private val powerButton = PowerButton(
        schedule = { delayMs, action -> powerTimer.schedule({ synchronized(this) { action() } }, delayMs, TimeUnit.MILLISECONDS) },
        onPress = ::onPowerPress,
        onHold = ::onPowerHold,
    )

    fun init(transport: GamepadTransport, screensOff: Boolean) {
        state = state.copy(transport = transport, screensOff = screensOff)
        // A crash in gamepad mode can leave the USB function linked or the IDs changed: undo it.
        if (gadget.hasLeftovers()) thread(name = "gamepad-usb-cleanup", isDaemon = true) { usbTeardown() }
        bluetoothIds.useOwnIds()
    }

    // ---- Control ------------------------------------------------------------------------------

    @Synchronized
    fun setOn(on: Boolean) {
        if (on == state.on) return
        if (on) start() else stop(usbTeardownNow = false)
        notifier.stateChanged()
    }

    @Synchronized
    fun setTransport(transport: GamepadTransport) {
        if (transport == state.transport) return
        val wasOn = state.on
        if (wasOn) stop(usbTeardownNow = false)
        state = state.copy(transport = transport)
        if (wasOn) start()
        notifier.stateChanged()
    }

    @Synchronized
    fun setScreensOff(on: Boolean) {
        state = state.copy(screensOff = on)
        if (state.on && state.connected) darken(on)
        notifier.stateChanged()
    }

    /** From the app: the Bluetooth host connected or disconnected. */
    @Synchronized
    fun onBluetooth(connected: Boolean, host: String) {
        if (!state.on || state.transport != GamepadTransport.BLUETOOTH) return
        setConnected(connected, if (connected) host else "")
    }

    /** AYN tapped while dark: the screens come back (until the next connect or power press). */
    @Synchronized
    fun lightUp(): Boolean {
        if (!state.dark) return false
        darken(false)
        return true
    }

    /**
     * Daemon exiting (module disabled): give USB and the controller back right now. Like turning
     * gamepad mode off, except that the USB function is gone when this returns.
     */
    @Synchronized
    fun shutdown() {
        if (!state.on) return
        stop(usbTeardownNow = true)
        awaitOwnBluetoothIds()
    }

    /** The device's own Bluetooth IDs are back before the daemon exits (restarting Bluetooth takes seconds). */
    private fun awaitOwnBluetoothIds() {
        try {
            bluetoothIds.useOwnIds().get(BLUETOOTH_IDS_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Timber.w("gamepad: Bluetooth IDs not restored in time (restore-kernel.sh will)")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Every couple of seconds from the daemon's housekeeping. */
    @Synchronized
    fun tick() {
        if (!state.on) return
        awake.keepAwake()
        // Asleep anyway (an app, a timeout we missed): the controller is off now, wake up. A
        // closed lid is the user putting the Thor away; that wins.
        if (!input.lidClosed && !awake.isInteractive()) {
            Timber.i("gamepad: Android went to sleep, waking it (the controller needs a panel on)")
            awake.wakeUp()
        }
        // The controller node can come back as a new device (after a controller MCU reset).
        if (!input.gamepadGrabbed() && clock.elapsedRealtime() - grabbedAt > REGRAB_AFTER_MS && input.hasController) {
            input.releaseGamepad()
            grab()
        }
        if (state.transport == GamepadTransport.USB && state.error.isEmpty()) {
            if (!gadget.isLinked()) {
                // Android rebuilt the USB configuration (a mode change in Settings): add ours back.
                Timber.i("gamepad: USB function was removed, adding it again")
                thread(name = "gamepad-usb-setup", isDaemon = true) { gadget.addHidFunction() }
            }
            setConnected(gadget.isConfigured(), "")
        }
    }

    private fun start() {
        state = state.copy(on = true, connected = false, host = "", dark = false, error = "")
        awake.keepAwake()
        input.setPowerKeyHandler(::onPowerKey)
        if (state.transport == GamepadTransport.USB) {
            thread(name = "gamepad-usb-setup", isDaemon = true) {
                val setupError = gadget.addHidFunction()
                synchronized(this) {
                    if (state.on && state.transport == GamepadTransport.USB) {
                        if (setupError != null) state = state.copy(error = setupError)
                        if (setupError == null) usb.start { reports.last }
                        notifier.stateChanged()
                    }
                }
            }
        }
        if (state.transport == GamepadTransport.BLUETOOTH) {
            // Bluetooth comes on announcing a gamepad; the app registers the HID device once it is.
            bluetoothIds.useGamepadIds()
        }
        grab()
        Timber.i("gamepad mode on (${state.transport.wire})")
    }

    /**
     * Gamepad mode off: everything back as it was before [start]. The USB function goes away on a
     * thread of its own, or before this returns with [usbTeardownNow] (the daemon is exiting).
     */
    private fun stop(usbTeardownNow: Boolean) {
        val transport = state.transport
        // A hold timer still pending would open the power menu after gamepad mode is gone.
        powerButton.cancel()
        input.releaseGamepad()
        // Let the host see everything released before it goes away.
        reports.send(GamepadHid.neutral())
        if (transport == GamepadTransport.BLUETOOTH) bluetoothIds.useOwnIds()
        if (transport == GamepadTransport.USB) {
            usb.stop()
            if (usbTeardownNow) usbTeardown() else thread(name = "gamepad-usb-teardown", isDaemon = true) { usbTeardown() }
        }
        input.setPowerKeyHandler(null)
        if (state.dark) displays.setGamepadDark(false)
        state = state.copy(on = false, connected = false, host = "", dark = false, error = "")
        awake.release()
        Timber.i("gamepad mode off")
    }

    /** The I/O threads close /dev/hidg0 before the function goes away. */
    private fun usbTeardown() {
        if (!usb.join()) Timber.w("gamepad: /dev/hidg0 still open, removing the HID function anyway")
        gadget.removeHidFunction()
    }

    private fun grab() {
        if (!input.grabForGamepad(reports::onEvent)) Timber.w("gamepad: could not grab the controller")
        grabbedAt = clock.elapsedRealtime()
        // Start from what is held right now.
        val (keys, axisValues) = input.controllerSnapshot()
        reports.restart(keys, axisValues)
    }

    private fun setConnected(connected: Boolean, host: String) {
        if (connected == state.connected && host == state.host) return
        state = state.copy(connected = connected, host = host)
        Timber.i("gamepad host ${if (connected) "connected ($host)" else "disconnected"}")
        // Dark while a host plays (if wanted); back on when it goes, so the Thor isn't left dark.
        darken(connected && state.screensOff)
        notifier.stateChanged()
    }

    /** Screens dark (true) or back to normal (false). */
    private fun darken(dark: Boolean) {
        if (dark == state.dark) return
        state = state.copy(dark = dark)
        displays.setGamepadDark(dark)
        notifier.stateChanged()
    }

    // ---- Power button (taken over while gamepad mode is on) ------------------------------------

    @Synchronized
    private fun onPowerKey(down: Boolean) {
        if (!state.on) return
        powerButton.onKey(down)
    }

    /** A press: dark <-> on (and wake Android if it slept after all). */
    private fun onPowerPress() {
        if (!awake.isInteractive()) awake.wakeUp()
        darken(!state.dark)
    }

    /** Held: the power menu, on lit screens. */
    private fun onPowerHold() {
        darken(false)
        thread(name = "gamepad-pwr-menu", isDaemon = true) { sys.sh(POWER_MENU_COMMAND) }
    }

    // ---- Reports ------------------------------------------------------------------------------

    /** Under [reports]' lock: one report to the current transport. */
    private fun submit(report: ByteArray) {
        when (state.transport) {
            GamepadTransport.USB -> usb.submit(report)
            else -> bluetooth.submit(report)
        }
    }

    companion object {
        /** How long shutdown waits for the device's own Bluetooth IDs (a Bluetooth restart). */
        const val BLUETOOTH_IDS_TIMEOUT_S = 15L

        /** A grab lost for this long (and a controller there) is taken again. */
        const val REGRAB_AFTER_MS = 3000L

        const val POWER_MENU_COMMAND = "input keyevent --longpress KEYCODE_POWER"
    }
}
