package com.thorctl.daemon.feature

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.view.KeyEvent
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.daemon.ipc.ClientLink
import com.thorctl.daemon.ipc.DaemonNotifier
import com.thorctl.daemon.system.SystemServices
import com.thorctl.protocol.GamepadHid
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.GamepadTransport
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Gamepad mode: the built-in controller drives another device as a HID gamepad that hosts know
 * as a Stadia Controller ([GamepadHid] explains why).
 *
 * The controller is grabbed exclusively (nothing reaches Android) and every evdev frame becomes a
 * HID report the moment its SYN_REPORT arrives:
 *
 *  - USB: an f_hid function is added next to adb in the USB gadget, and the gadget takes the
 *    gamepad's vendor / product ID while it is there, so a computer sees a composite device with a
 *    gamepad. A writer thread always sends the newest report; f_hid only blocks while the previous
 *    one waits for the host's next poll, so nothing queues up. A reader drains what the host
 *    sends (rumble), so the host never stalls on a full queue.
 *  - Bluetooth: the app owns the Bluetooth HID device registration (it needs a Context). Reports
 *    go to it over Binder, newest-wins and at most one per [BLUETOOTH_INTERVAL_MS], so the radio
 *    never builds a backlog.
 *
 * While gamepad mode is on, Android must stay awake: the controller's driver cuts the
 * controller's power when both panels are off, and Android's sleep turns both off. So user
 * activity is reported on every tick (no screen timeout), the power button is taken over (a
 * press darkens the screens or brings them back, a long press opens the power menu), and
 * "screens off" means dark ([DisplayController.setGamepadDark]): top panel off, bottom panel
 * black. A kernel wakelock keeps the SoC out of suspend besides.
 */
@Inject
@SingleIn(DaemonScope::class)
class Gamepad(
    private val input: InputWatcher,
    private val link: ClientLink,
    private val notifier: DaemonNotifier,
    private val displays: DisplayController,
    private val system: SystemServices,
    private val sys: Sys,
) {
    private fun onChanged() = notifier.stateChanged()

    /** Screens dark (true) or back to normal (false). */
    private fun setDark(dark: Boolean) = displays.setGamepadDark(dark)

    /** Keeps Android awake: user activity on every panel. */
    private fun keepAwake() {
        for (id in intArrayOf(displays.topId, displays.bottomId)) {
            if (id < 0) continue
            try {
                system.userActivity(id)
            } catch (e: Throwable) {
                Timber.w(e, "gamepad: user activity on display $id failed")
            }
        }
    }

    /** Android went to sleep anyway (not the lid): wake it. */
    private fun wakeUp() = system.injectKey(KeyEvent.KEYCODE_WAKEUP, displays.topId)

    private fun isInteractive() = system.isInteractive()

    @Volatile
    var state = GamepadState()
        private set

    private var grabbedAt = 0L

    fun init(transport: GamepadTransport, screensOff: Boolean) {
        state = state.copy(transport = transport, screensOff = screensOff)
        // A crash in gamepad mode can leave the USB function linked or the IDs changed: undo it.
        if (usbLinked() || File(USB_IDS).exists()) thread(name = "gamepad-usb-cleanup", isDaemon = true) { usbTeardown() }
    }

    // ---- Control ------------------------------------------------------------------------------

    @Synchronized
    fun setOn(on: Boolean) {
        if (on == state.on) return
        if (on) start() else stop()
        onChanged()
    }

    @Synchronized
    fun setTransport(transport: GamepadTransport) {
        if (transport == state.transport) return
        val wasOn = state.on
        if (wasOn) stop()
        state = state.copy(transport = transport)
        if (wasOn) start()
        onChanged()
    }

    @Synchronized
    fun setScreensOff(on: Boolean) {
        state = state.copy(screensOff = on)
        if (state.on && state.connected) darken(on)
        onChanged()
    }

    /** From the app: the Bluetooth host connected or disconnected. */
    @Synchronized
    fun onBluetooth(connected: Boolean, host: String) {
        if (!state.on || state.transport != GamepadTransport.BLUETOOTH) return
        setConnected(connected, if (connected) host else "")
    }

    private fun start() {
        state = state.copy(on = true, connected = false, host = "", dark = false, error = "")
        wakelock(true)
        keepAwake()
        input.setPowerKeyHandler(::onPowerKey)
        if (state.transport == GamepadTransport.USB) {
            thread(name = "gamepad-usb-setup", isDaemon = true) {
                val setupError = usbSetup()
                synchronized(this) {
                    if (state.on && state.transport == GamepadTransport.USB) {
                        if (setupError != null) state = state.copy(error = setupError)
                        if (setupError == null) startUsbWriter()
                        onChanged()
                    }
                }
            }
        }
        if (state.transport == GamepadTransport.BLUETOOTH) {
            // The app registers the HID device once the adapter is on.
            thread(name = "gamepad-bt-on", isDaemon = true) { sys.sh("cmd bluetooth_manager enable") }
        }
        frame.reset()
        grab()
        Timber.i("gamepad mode on (${state.transport.wire})")
    }

    private fun stop() {
        val transport = state.transport
        input.releaseGamepad()
        // Let the host see everything released before it goes away.
        submit(GamepadHid.neutral())
        if (transport == GamepadTransport.USB) {
            usbWriterOn = false
            synchronized(usbLock) { (usbLock as Object).notifyAll() }
            thread(name = "gamepad-usb-teardown", isDaemon = true) { usbTeardown() }
        }
        input.setPowerKeyHandler(null)
        if (state.dark) setDark(false)
        state = state.copy(on = false, connected = false, host = "", dark = false, error = "")
        wakelock(false)
        Timber.i("gamepad mode off")
    }

    private fun grab() {
        if (!input.grabForGamepad(::onEvent)) Timber.w("gamepad: could not grab the controller")
        grabbedAt = SystemClock.elapsedRealtime()
        // Start from what is held right now.
        val (keys, axisValues) = input.controllerSnapshot()
        frame.load(keys, axisValues)
        submit(frame.build())
    }

    private fun setConnected(connected: Boolean, host: String) {
        if (connected == state.connected && host == state.host) return
        state = state.copy(connected = connected, host = host)
        Timber.i("gamepad host ${if (connected) "connected ($host)" else "disconnected"}")
        // Dark while a host plays (if wanted); back on when it goes, so the Thor isn't left dark.
        darken(connected && state.screensOff)
        onChanged()
    }

    private fun darken(dark: Boolean) {
        if (dark == state.dark) return
        state = state.copy(dark = dark)
        setDark(dark)
        onChanged()
    }

    /** AYN tapped while dark: the screens come back (until the next connect or power press). */
    @Synchronized
    fun lightUp(): Boolean {
        if (!state.dark) return false
        darken(false)
        return true
    }

    // ---- Power button (taken over while gamepad mode is on) ------------------------------------

    private var powerDownAt = 0L
    private var powerLongFired = false
    private val powerTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { Thread(it, "gamepad-pwr").apply { isDaemon = true } }
    private var powerLong: java.util.concurrent.ScheduledFuture<*>? = null

    @Synchronized
    private fun onPowerKey(down: Boolean) {
        if (!state.on) return
        if (down) {
            powerDownAt = SystemClock.uptimeMillis()
            powerLongFired = false
            powerLong?.cancel(false)
            powerLong = powerTimer.schedule({ onPowerLong() }, POWER_LONG_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            return
        }
        powerLong?.cancel(false)
        if (powerLongFired) return
        // A press: dark <-> on (and wake Android if it slept after all).
        if (!isInteractive()) wakeUp()
        darken(!state.dark)
    }

    /** Held: the power menu, on lit screens. */
    @Synchronized
    private fun onPowerLong() {
        powerLongFired = true
        darken(false)
        thread(name = "gamepad-pwr-menu", isDaemon = true) { sys.sh("input keyevent --longpress KEYCODE_POWER") }
    }

    /** Daemon exiting (module disabled): give USB and the controller back right now. */
    @Synchronized
    fun shutdown() {
        if (!state.on) return
        input.setPowerKeyHandler(null)
        if (state.dark) setDark(false)
        input.releaseGamepad()
        usbWriterOn = false
        if (state.transport == GamepadTransport.USB) usbTeardown()
        state = state.copy(on = false, connected = false, host = "")
        wakelock(false)
    }

    /** Every couple of seconds from the daemon's housekeeping. */
    @Synchronized
    fun tick() {
        if (!state.on) return
        wakelock(true)
        keepAwake()
        // Asleep anyway (an app, a timeout we missed): the controller is off now, wake up. A
        // closed lid is the user putting the Thor away; that wins.
        if (!input.lidClosed && !isInteractive()) {
            Timber.i("gamepad: Android went to sleep, waking it (the controller needs a panel on)")
            wakeUp()
        }
        // The controller node can come back as a new device (after a controller MCU reset).
        if (!input.gamepadGrabbed() && SystemClock.elapsedRealtime() - grabbedAt > 3000 && input.hasController) {
            input.releaseGamepad()
            grab()
        }
        if (state.transport == GamepadTransport.USB && state.error.isEmpty()) {
            if (!usbLinked()) {
                // Android rebuilt the USB configuration (a mode change in Settings): add ours back.
                Timber.i("gamepad: USB function was removed, adding it again")
                thread(name = "gamepad-usb-setup", isDaemon = true) { usbSetup() }
            }
            setConnected(sys.read("$UDC_CLASS/${udcName()}/state")?.trim() == "configured", "")
        }
    }

    // ---- Reports ------------------------------------------------------------------------------

    private val frame = Frame()

    /** Grab thread: one raw evdev event. */
    private fun onEvent(type: Int, code: Int, value: Int) {
        when (type) {
            InputWatcher.EV_KEY -> frame.key(code, value != 0)
            InputWatcher.EV_ABS -> frame.abs(code, value)
            InputWatcher.EV_SYN -> if (code == SYN_REPORT) {
                val report = frame.build()
                if (!report.contentEquals(lastSubmitted)) submit(report)
            }
        }
    }

    @Volatile
    private var lastSubmitted = ByteArray(0)

    private fun submit(report: ByteArray) {
        lastSubmitted = report
        when (state.transport) {
            GamepadTransport.USB -> synchronized(usbLock) {
                usbPending = report
                (usbLock as Object).notifyAll()
            }
            else -> bluetoothSubmit(report)
        }
    }

    /** Controller state, folded from evdev events into the report layout of [GamepadHid]. */
    private class Frame {
        private val held = HashSet<Int>()
        private var leftX = 0
        private var leftY = 0
        private var rightX = 0
        private var rightY = 0
        private var leftTrigger = 0
        private var rightTrigger = 0
        private var hatX = 0
        private var hatY = 0

        fun reset() {
            held.clear()
            leftX = 0; leftY = 0; rightX = 0; rightY = 0; leftTrigger = 0; rightTrigger = 0; hatX = 0; hatY = 0
        }

        fun load(keys: Set<Int>, axisValues: IntArray) {
            reset()
            held += keys
            leftX = axisValues[ABS_X]; leftY = axisValues[ABS_Y]; rightX = axisValues[ABS_RX]; rightY = axisValues[ABS_RY]
            leftTrigger = axisValues[InputWatcher.ABS_Z]; rightTrigger = axisValues[InputWatcher.ABS_RZ]
            hatX = axisValues[InputWatcher.ABS_HAT0X]; hatY = axisValues[InputWatcher.ABS_HAT0Y]
        }

        fun key(code: Int, down: Boolean) {
            if (down) held += code else held -= code
        }

        fun abs(code: Int, value: Int) {
            when (code) {
                ABS_X -> leftX = value
                ABS_Y -> leftY = value
                ABS_RX -> rightX = value
                ABS_RY -> rightY = value
                InputWatcher.ABS_Z -> leftTrigger = value
                InputWatcher.ABS_RZ -> rightTrigger = value
                InputWatcher.ABS_HAT0X -> hatX = value
                InputWatcher.ABS_HAT0Y -> hatY = value
            }
        }

        fun build(): ByteArray {
            val leftTriggerLevel = trigger(leftTrigger)
            val rightTriggerLevel = trigger(rightTrigger)
            var buttonByte1 = 0
            var buttonByte2 = 0
            for (code in held) {
                when (code) {
                    InputWatcher.BTN_SOUTH -> buttonByte2 = buttonByte2 or GamepadHid.B2_A
                    InputWatcher.BTN_EAST -> buttonByte2 = buttonByte2 or GamepadHid.B2_B
                    InputWatcher.BTN_NORTH -> buttonByte2 = buttonByte2 or GamepadHid.B2_X
                    InputWatcher.BTN_WEST -> buttonByte2 = buttonByte2 or GamepadHid.B2_Y
                    InputWatcher.BTN_TL -> buttonByte2 = buttonByte2 or GamepadHid.B2_L1
                    InputWatcher.BTN_TR -> buttonByte2 = buttonByte2 or GamepadHid.B2_R1
                    BTN_THUMBL -> buttonByte2 = buttonByte2 or GamepadHid.B2_L3
                    InputWatcher.BTN_THUMBR -> buttonByte1 = buttonByte1 or GamepadHid.B1_R3
                    InputWatcher.BTN_TL2 -> buttonByte1 = buttonByte1 or GamepadHid.B1_L2
                    InputWatcher.BTN_TR2 -> buttonByte1 = buttonByte1 or GamepadHid.B1_R2
                    BTN_SELECT -> buttonByte1 = buttonByte1 or GamepadHid.B1_SELECT
                    BTN_START -> buttonByte1 = buttonByte1 or GamepadHid.B1_START
                    BTN_MODE, InputWatcher.KEY_HOME -> buttonByte1 = buttonByte1 or GamepadHid.B1_HOME
                    InputWatcher.KEY_BACK -> buttonByte1 = buttonByte1 or GamepadHid.B1_CAPTURE
                }
            }
            // The triggers' digital bits, as on the Stadia: pressed past half way.
            if (leftTriggerLevel >= 128) buttonByte1 = buttonByte1 or GamepadHid.B1_L2
            if (rightTriggerLevel >= 128) buttonByte1 = buttonByte1 or GamepadHid.B1_R2
            val hat = when {
                hatY < 0 && hatX == 0 -> 0
                hatY < 0 && hatX > 0 -> 1
                hatY == 0 && hatX > 0 -> 2
                hatY > 0 && hatX > 0 -> 3
                hatY > 0 && hatX == 0 -> 4
                hatY > 0 && hatX < 0 -> 5
                hatY == 0 && hatX < 0 -> 6
                hatY < 0 && hatX < 0 -> 7
                else -> 8
            }
            val report = ByteArray(GamepadHid.PAYLOAD)
            report[0] = hat.toByte()
            report[1] = buttonByte1.toByte()
            report[2] = buttonByte2.toByte()
            report[3] = stick(leftX).toByte()
            report[4] = stick(leftY).toByte()
            report[5] = stick(rightX).toByte()
            report[6] = stick(rightY).toByte()
            report[7] = leftTriggerLevel.toByte()
            report[8] = rightTriggerLevel.toByte()
            return report
        }

        /** -32767..32767 (evdev) to 1..255 with 128 in the middle. */
        private fun stick(value: Int): Int = ((value.coerceIn(-32767, 32767) + 32768) shr 8).coerceIn(1, 255)

        /** The analog triggers rest a few counts above 0; a small dead zone keeps them at 0. */
        private fun trigger(value: Int): Int =
            ((value - TRIGGER_DEAD) * 255 / (255 - TRIGGER_DEAD)).coerceIn(0, 255)
    }

    // ---- USB ----------------------------------------------------------------------------------

    private val usbLock = Any()
    private var usbPending: ByteArray? = null

    @Volatile
    private var usbWriterOn = false

    private val usbIo = ArrayList<Thread>()

    private fun startUsbWriter() {
        if (usbWriterOn) return
        usbWriterOn = true
        synchronized(usbIo) {
            usbIo += thread(name = "gamepad-usb-tx", isDaemon = true) { usbWriteLoop() }
            usbIo += thread(name = "gamepad-usb-rx", isDaemon = true) { usbReadLoop() }
        }
    }

    /** Before the function goes away: the I/O threads have closed /dev/hidg0. */
    private fun joinUsbIo() {
        val threads = synchronized(usbIo) { usbIo.toList().also { usbIo.clear() } }
        threads.forEach { it.join(1000) }
    }

    /**
     * Drains reports from the host (rumble, output report 5). f_hid hands the host a new buffer
     * only once the previous one is read, so unread reports would leave the host waiting.
     */
    private fun usbReadLoop() {
        val buffer = ByteArray(64)
        while (usbWriterOn) {
            val fileDescriptor = try {
                Os.open(HIDG, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
            } catch (_: Exception) {
                Thread.sleep(300) // node not there yet
                continue
            }
            try {
                val pollRequest = StructPollfd().apply {
                    this.fd = fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                while (usbWriterOn) {
                    pollRequest.revents = 0
                    if (Os.poll(arrayOf(pollRequest), 250) <= 0) continue
                    val returnedEvents = pollRequest.revents.toInt()
                    if (returnedEvents and (OsConstants.POLLERR or OsConstants.POLLHUP) != 0) break
                    if (returnedEvents and OsConstants.POLLIN != 0) Os.read(fileDescriptor, buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
                // Host gone or nothing to read after all: open again shortly.
            } finally {
                try {
                    Os.close(fileDescriptor)
                } catch (_: Exception) {
                }
            }
            if (usbWriterOn) Thread.sleep(250)
        }
    }

    private fun usbWriteLoop() {
        val frameBytes = ByteArray(1 + GamepadHid.PAYLOAD)
        frameBytes[0] = GamepadHid.REPORT_ID.toByte()
        while (usbWriterOn) {
            val hidOutput = try {
                FileOutputStream(HIDG)
            } catch (_: Exception) {
                Thread.sleep(300) // node not there yet (the gadget is still binding)
                continue
            }
            hidOutput.use { output ->
                // A fresh host: send the current state first.
                var next: ByteArray? = lastSubmitted.takeIf { it.size == GamepadHid.PAYLOAD }
                while (usbWriterOn) {
                    val report = next ?: synchronized(usbLock) {
                        while (usbPending == null && usbWriterOn) (usbLock as Object).wait(1000)
                        usbPending.also { usbPending = null }
                    } ?: continue
                    next = null
                    System.arraycopy(report, 0, frameBytes, 1, GamepadHid.PAYLOAD)
                    try {
                        // Returns as soon as the report is queued; only blocks while the previous
                        // one is still waiting for the host's poll (newer reports replace usbPending).
                        output.write(frameBytes)
                    } catch (_: Exception) {
                        // Not configured (no host) or the host went away: retry shortly.
                        synchronized(usbLock) { if (usbPending == null) usbPending = report }
                        Thread.sleep(250)
                        break
                    }
                }
            }
        }
    }

    private fun udcName(): String =
        sys.read("$GADGET/UDC")?.trim()?.takeIf { it.isNotEmpty() }
            ?: File(UDC_CLASS).list()?.firstOrNull()
            ?: ""

    private fun configDir(): File? = File("$GADGET/configs").listFiles()?.firstOrNull { it.isDirectory }

    private fun usbLinked(): Boolean {
        val configDirectory = configDir() ?: return false
        return try {
            Os.readlink(File(configDirectory, LINK_NAME).path).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /** Adds the HID function to the gadget and rebinds it. Returns an error message or null. */
    private fun usbSetup(): String? {
        if (!File(GADGET).isDirectory) return "USB gadget not available"
        val configDirectory = configDir() ?: return "USB gadget has no configuration"
        if (usbLinked()) return null
        val functionDirectory = File(FUNCTION)
        try {
            if (!functionDirectory.isDirectory && !functionDirectory.mkdir()) return "This kernel has no USB HID gadget support"
            File(functionDirectory, "protocol").writeText("0")
            File(functionDirectory, "subclass").writeText("0")
            File(functionDirectory, "report_length").writeText((1 + GamepadHid.PAYLOAD).toString())
            FileOutputStream(File(functionDirectory, "report_desc")).use { it.write(GamepadHid.DESCRIPTOR) }
        } catch (e: Exception) {
            Timber.w(e, "gamepad: HID function set-up failed")
            return "USB HID set-up failed"
        }
        val udc = udcName()
        if (udc.isEmpty()) return "No USB controller"
        // The configuration can only change while unbound. Android's USB HAL rebinds the gadget
        // by itself when adb comes back; any bind after the link includes the gamepad.
        unbind()
        setGamepadIds()
        try {
            Os.symlink(functionDirectory.path, File(configDirectory, LINK_NAME).path)
        } catch (e: Exception) {
            Timber.w(e, "gamepad: linking the HID function failed")
            restoreIds()
            bind(udc)
            return "USB HID set-up failed"
        }
        bind(udc)
        Timber.i("gamepad: USB HID function added (${configDirectory.name}, $udc)")
        return null
    }

    private fun usbTeardown() {
        joinUsbIo()
        val configDirectory = configDir() ?: return
        val link = File(configDirectory, LINK_NAME)
        val udc = udcName()
        if (usbLinked() || File(USB_IDS).exists()) {
            unbind()
            if (usbLinked()) {
                try {
                    Os.remove(link.path)
                } catch (e: Exception) {
                    Timber.w(e, "gamepad: unlinking the HID function failed")
                }
            }
            restoreIds()
            if (udc.isNotEmpty()) bind(udc)
        }
        File(FUNCTION).delete() // rmdir; fails harmlessly if something still links it
        Timber.i("gamepad: USB HID function removed")
    }

    /**
     * The whole gadget (adb included) takes the gamepad's IDs; macOS only accepts gamepads it
     * knows by ID. Android's own IDs are kept in [USB_IDS] (also across a daemon crash).
     */
    private fun setGamepadIds() {
        val savedIdsFile = File(USB_IDS)
        val currentIds = USB_ID_ATTRIBUTES.map { sys.read("$GADGET/$it") ?: "" }
        val productId = currentIds[1].removePrefix("0x").toIntOrNull(16)
        if (!savedIdsFile.exists() && productId != null && productId != GamepadHid.PRODUCT_ID) savedIdsFile.writeText(currentIds.joinToString("\n"))
        sys.write("$GADGET/idVendor", hex(GamepadHid.VENDOR_ID))
        sys.write("$GADGET/idProduct", hex(GamepadHid.PRODUCT_ID))
        sys.write("$GADGET/bcdDevice", hex(GamepadHid.VERSION))
    }

    /** Android's IDs back (while unbound). */
    private fun restoreIds() {
        val savedIdsFile = File(USB_IDS)
        val saved = try {
            savedIdsFile.readLines()
        } catch (_: Exception) {
            return
        }
        if (saved.size == USB_ID_ATTRIBUTES.size && saved.all { it.startsWith("0x") }) {
            USB_ID_ATTRIBUTES.forEachIndexed { index, attribute -> sys.write("$GADGET/$attribute", saved[index]) }
        }
        savedIdsFile.delete()
    }

    private fun hex(value: Int) = "0x%04x".format(value)

    private fun unbind() {
        sys.write("$GADGET/UDC", "\n")
        Thread.sleep(100)
    }

    /** Binds the gadget, unless Android's USB HAL already did (it reacts to adb reopening). */
    private fun bind(udc: String) {
        repeat(40) {
            if (!sys.read("$GADGET/UDC")?.trim().isNullOrEmpty()) return
            sys.write("$GADGET/UDC", udc)
            Thread.sleep(100)
        }
        Timber.w("gamepad: USB gadget did not bind")
    }

    // ---- Bluetooth (sent through the app) -----------------------------------------------------

    private val bluetoothLock = Any()
    private var bluetoothPending: ByteArray? = null
    private var bluetoothLastSent = 0L
    private var bluetoothScheduled = false

    private fun bluetoothSubmit(report: ByteArray) {
        synchronized(bluetoothLock) {
            bluetoothPending = report
            val wait = bluetoothLastSent + BLUETOOTH_INTERVAL_MS - SystemClock.uptimeMillis()
            if (wait <= 0) {
                bluetoothFlushLocked()
            } else if (!bluetoothScheduled) {
                bluetoothScheduled = true
                thread(name = "gamepad-bt-tx", isDaemon = true) {
                    Thread.sleep(wait)
                    synchronized(bluetoothLock) {
                        bluetoothScheduled = false
                        bluetoothFlushLocked()
                    }
                }
            }
        }
    }

    private fun bluetoothFlushLocked() {
        val report = bluetoothPending ?: return
        bluetoothPending = null
        bluetoothLastSent = SystemClock.uptimeMillis()
        link.broadcast(DaemonEvent.GamepadReport(report))
    }

    // ---- Wakelock -----------------------------------------------------------------------------

    /** Timed, so it lapses by itself if the daemon dies; refreshed on every [tick]. */
    private fun wakelock(on: Boolean) {
        if (on) sys.write("/sys/power/wake_lock", "$WAKELOCK $WAKELOCK_NS") else sys.write("/sys/power/wake_unlock", WAKELOCK)
    }

    companion object {
        const val GADGET = "/config/usb_gadget/g1"
        const val UDC_CLASS = "/sys/class/udc"
        const val FUNCTION = "$GADGET/functions/hid.thorpad"

        /** Not "functionN": Android's USB HAL unlinks those when it rebuilds the configuration. */
        const val LINK_NAME = "thor_gamepad"
        const val HIDG = "/dev/hidg0"

        /** Android's USB IDs while the gadget has the gamepad's. */
        const val USB_IDS = "/data/adb/thorctl/usb-ids"
        val USB_ID_ATTRIBUTES = listOf("idVendor", "idProduct", "bcdDevice")

        const val SYN_REPORT = 0
        const val ABS_X = 0x00
        const val ABS_Y = 0x01
        const val ABS_RX = 0x03
        const val ABS_RY = 0x04
        const val BTN_SELECT = 314
        const val BTN_START = 315
        const val BTN_MODE = 316
        const val BTN_THUMBL = 317
        const val TRIGGER_DEAD = 10

        /** 125 reports a second at most over Bluetooth, the newest always wins. */
        const val BLUETOOTH_INTERVAL_MS = 8L

        const val WAKELOCK = "thorctl_gamepad"

        /** Holding the power button this long opens the power menu. */
        const val POWER_LONG_MS = 600L
        const val WAKELOCK_NS = 60_000_000_000L
    }
}
