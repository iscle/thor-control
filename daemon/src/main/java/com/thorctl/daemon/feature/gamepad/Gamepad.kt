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
import kotlin.concurrent.thread

/**
 * Gamepad mode: the built-in controller drives another device as a HID gamepad that hosts know
 * as a Stadia Controller ([GamepadHid] explains why).
 *
 * The controller is grabbed exclusively (nothing reaches Android) and every evdev frame becomes a
 * HID report ([HidReportEncoder]) the moment its SYN_REPORT arrives:
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
    private val sys: Sys,
    private val clock: Clock,
) {
    @Volatile
    var state = GamepadState()
        private set

    private var grabbedAt = 0L

    private val encoder = HidReportEncoder()

    @Volatile
    private var lastSubmitted = ByteArray(0)

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
    }

    // ---- Control ------------------------------------------------------------------------------

    @Synchronized
    fun setOn(on: Boolean) {
        if (on == state.on) return
        if (on) start() else stop()
        notifier.stateChanged()
    }

    @Synchronized
    fun setTransport(transport: GamepadTransport) {
        if (transport == state.transport) return
        val wasOn = state.on
        if (wasOn) stop()
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

    /** Daemon exiting (module disabled): give USB and the controller back right now. */
    @Synchronized
    fun shutdown() {
        if (!state.on) return
        input.setPowerKeyHandler(null)
        if (state.dark) displays.setGamepadDark(false)
        input.releaseGamepad()
        usb.stop(wakeWriter = false)
        if (state.transport == GamepadTransport.USB) usbTeardown()
        state = state.copy(on = false, connected = false, host = "")
        awake.release()
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
                        if (setupError == null) usb.start { lastSubmitted }
                        notifier.stateChanged()
                    }
                }
            }
        }
        if (state.transport == GamepadTransport.BLUETOOTH) {
            // The app registers the HID device once the adapter is on.
            thread(name = "gamepad-bt-on", isDaemon = true) { sys.sh("cmd bluetooth_manager enable") }
        }
        encoder.reset()
        grab()
        Timber.i("gamepad mode on (${state.transport.wire})")
    }

    private fun stop() {
        val transport = state.transport
        input.releaseGamepad()
        // Let the host see everything released before it goes away.
        submit(GamepadHid.neutral())
        if (transport == GamepadTransport.USB) {
            usb.stop(wakeWriter = true)
            thread(name = "gamepad-usb-teardown", isDaemon = true) { usbTeardown() }
        }
        input.setPowerKeyHandler(null)
        if (state.dark) displays.setGamepadDark(false)
        state = state.copy(on = false, connected = false, host = "", dark = false, error = "")
        awake.release()
        Timber.i("gamepad mode off")
    }

    /** Before the function goes away, the I/O threads must have closed /dev/hidg0. */
    private fun usbTeardown() {
        usb.join()
        gadget.removeHidFunction()
    }

    private fun grab() {
        if (!input.grabForGamepad(::onEvent)) Timber.w("gamepad: could not grab the controller")
        grabbedAt = clock.elapsedRealtime()
        // Start from what is held right now.
        val (keys, axisValues) = input.controllerSnapshot()
        encoder.load(keys, axisValues)
        submit(encoder.build())
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
        thread(name = "gamepad-pwr-menu", isDaemon = true) { sys.sh("input keyevent --longpress KEYCODE_POWER") }
    }

    // ---- Reports ------------------------------------------------------------------------------

    /** Grab thread: one raw evdev event. */
    private fun onEvent(type: Int, code: Int, value: Int) {
        val report = encoder.onEvent(type, code, value) ?: return
        if (!report.contentEquals(lastSubmitted)) submit(report)
    }

    private fun submit(report: ByteArray) {
        lastSubmitted = report
        when (state.transport) {
            GamepadTransport.USB -> usb.submit(report)
            else -> bluetooth.submit(report)
        }
    }

    companion object {
        /** A grab lost for this long (and a controller there) is taken again. */
        const val REGRAB_AFTER_MS = 3000L
    }
}
