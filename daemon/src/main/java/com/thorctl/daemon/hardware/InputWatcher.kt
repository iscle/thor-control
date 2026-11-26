package com.thorctl.daemon.hardware

import android.system.Os
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Raw evdev input handling, below the Android input pipeline:
 *
 *  - AYN button (KEY_F24 on gpio-keys) acts as a modifier. While it is held the built-in
 *    controller is grabbed exclusively (EVIOCGRAB), so combo presses like AYN+B never reach the
 *    game. Releasing AYN without using a combo is a "tap" (opens/closes the menu).
 *  - Physical Home on the controller: short press = Home, long press = app switcher.
 *    The module removes HOME from the controller's key layout so Android does not also act on it;
 *    without that remap we only add the app switcher on long press.
 *  - While Thor Control's recent apps screen is open, the controller is grabbed the same way and
 *    its buttons drive that screen ([grabController]); the app underneath sees nothing.
 */
@Inject
@SingleIn(DaemonScope::class)
class InputWatcher(private val sys: Sys) {
    interface Listener {
        fun onAynTap()
        fun onCombo(combo: Combo)
        fun onHomeDown()
        fun onHomeSingle()
        fun onShoulder()
        fun onHomeLong()
    }

    enum class Combo { UP, DOWN, LEFT, RIGHT, B, X, Y, L1, R1, L2, R2 }

    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "input-worker") }
    private val pad = Pad()

    @Volatile
    private var aynDown = false

    @Volatile
    private var comboUsed = false

    /** True when Android no longer maps the controller's KEY_HOME (module key layout applied). */
    val homeRemapped: Boolean = run {
        val keyLayout = sys.read(CONTROLLER_KEY_LAYOUT) ?: return@run false
        !Regex("^key\\s+$KEY_HOME\\s+HOME\\b", RegexOption.MULTILINE).containsMatchIn(keyLayout)
    }

    /** Exclusive controller grab; every raw event goes to [handler] until [releaseController]. */
    fun grabController(handler: (type: Int, code: Int, value: Int) -> Unit): Boolean =
        pad.beginGrab(Owner.RECENTS, handler, 0)

    fun releaseController() = pad.endGrab(Owner.RECENTS)

    /**
     * Gamepad mode: the controller is grabbed for as long as it lasts and every raw event goes
     * to [handler]. While AYN is held, events go to combo detection instead, so AYN shortcuts
     * keep working (and never reach the other device).
     */
    fun grabForGamepad(handler: (type: Int, code: Int, value: Int) -> Unit): Boolean =
        pad.beginGrab(Owner.GAMEPAD, { type, code, value -> if (aynDown) pad.detect(type, code, value) else handler(type, code, value) }, 0)

    fun releaseGamepad() = pad.endGrab(Owner.GAMEPAD)

    /** Gamepad mode still holds a live grab (false if the controller node went away). */
    fun gamepadGrabbed(): Boolean = pad.holds(Owner.GAMEPAD)

    /** Keys held and axis values right now (as last seen by us). */
    fun controllerSnapshot(): Pair<Set<Int>, IntArray> = pad.snapshot()

    /** The controller is present (its input node was found). */
    val hasController: Boolean get() = pad.node != null

    /** Whether a controller key is held right now (as last seen by us). */
    fun isDown(code: Int): Boolean = pad.isDown(code)

    private enum class Owner { NONE, AYN, RECENTS, GAMEPAD }

    private lateinit var listener: Listener

    /** Starts reading the AYN button, the controller, Home, the lid and the power key; [newListener] gets the presses. */
    fun start(newListener: Listener) {
        listener = newListener
        Timber.i("input: home remapped=$homeRemapped")
        thread(name = "ayn", isDaemon = true) { aynLoop() }
        thread(name = "pad", isDaemon = true) { pad.loop() }
    }

    private fun post(block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (e: Throwable) {
                Timber.w(e, "input handler failed")
            }
        }
    }

    // ---- AYN button -------------------------------------------------------------------------------

    private fun aynLoop() {
        while (true) {
            val node = findNode { it.name == "gpio-keys" && it.hasKey(KEY_F24) }
                ?: findNode { it.hasKey(KEY_F24) && !it.name.contains("Controller", true) }
            if (node == null) {
                Timber.w("AYN button input node not found, retrying")
                Thread.sleep(5000)
                continue
            }
            Timber.i("watching AYN button on $node")
            try {
                FileInputStream(node).use { stream ->
                    // The lid's hall sensor is a switch on the same node.
                    lidClosedNow(stream.fd)?.let { setLidClosed(it) }
                    readEvents(stream) { type, code, value ->
                        if (type == EV_KEY && code == KEY_F24) onAyn(value)
                        if (type == EV_SW && code == SW_LID) setLidClosed(value != 0)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "AYN read failed")
            }
            Thread.sleep(1000)
        }
    }

    private fun onAyn(value: Int) {
        when (value) {
            1 -> if (!aynDown) {
                aynDown = true
                comboUsed = false
                // Already grabbed for the recents screen: AYN then only closes it (on release).
                // Grabbed for gamepad mode: that grab's events go to combo detection meanwhile.
                if (!pad.beginGrab(Owner.AYN, pad::detect, MAX_GRAB_MS)) pad.armCombos()
            }
            0 -> if (aynDown) {
                aynDown = false
                pad.endGrab(Owner.AYN)
                if (!comboUsed) post { listener.onAynTap() }
            }
        }
    }

    private fun combo(pressedCombo: Combo) {
        comboUsed = true
        post { listener.onCombo(pressedCombo) }
    }

    // ---- Power button: ignored with the lid closed, handled by gamepad mode -------------------
    // The power button is a PMIC key that wakes the SoC from suspend. With the lid closed we hold
    // an exclusive grab on it, so Android never sees the press and the screens stay off; the
    // device drops back to sleep by itself. Gamepad mode takes it over the same way: Android must
    // not go to sleep then (the controller would lose power), so presses go to its handler. The
    // PMIC's long-press hardware reset still works.

    private val lidLock = Any()
    private var powerGrab: FileInputStream? = null

    @Volatile
    var lidClosed = false
        private set

    @Volatile
    private var powerHandler: ((down: Boolean) -> Unit)? = null

    /** Power button presses (down / up) go to [handler] instead of Android; null gives them back. */
    fun setPowerKeyHandler(handler: ((down: Boolean) -> Unit)?) {
        synchronized(lidLock) {
            powerHandler = handler
            updatePowerGrab()
        }
    }

    /** Current lid switch state through EVIOCGSW, or null if it can't be read. */
    private fun lidClosedNow(fileDescriptor: FileDescriptor): Boolean? = try {
        ((IOCTL_INT.invoke(null, fileDescriptor, EVIOCGSW_4) as Int) and (1 shl SW_LID)) != 0
    } catch (e: Throwable) {
        Timber.w(e, "reading the lid switch failed")
        null
    }

    private fun setLidClosed(closed: Boolean) {
        synchronized(lidLock) {
            if (closed != lidClosed) Timber.i(if (closed) "lid closed: power button ignored" else "lid open")
            lidClosed = closed
            updatePowerGrab()
        }
    }

    private fun updatePowerGrab() {
        val want = lidClosed || powerHandler != null
        if (want == (powerGrab != null)) return
        if (!want) {
            try {
                powerGrab?.close()
            } catch (_: Exception) {
            }
            powerGrab = null
            Timber.i("power button back to normal")
            return
        }
        val node = findNode { it.name == POWER_KEY_NAME } ?: findNode { it.name.contains("pwrkey") && it.hasKey(KEY_POWER) }
        if (node == null) {
            Timber.w("power button input node not found")
            return
        }
        val stream = try {
            FileInputStream(node).also { IOCTL_INT.invoke(null, it.fd, EVIOCGRAB) }
        } catch (e: Throwable) {
            Timber.w(e, "grabbing the power button failed")
            return
        }
        powerGrab = stream
        Timber.i("power button grabbed ($node)")
        thread(name = "pwrkey", isDaemon = true) {
            try {
                readEvents(stream) { type, code, value ->
                    if (type != EV_KEY || code != KEY_POWER || value == 2) return@readEvents
                    when {
                        lidClosed -> if (value == 1) Timber.i("power button pressed with the lid closed: ignored")
                        else -> powerHandler?.let { handler -> post { handler(value == 1) } }
                    }
                }
            } catch (_: Exception) {
                // closed when neither needs it any more
            }
        }
    }

    // ---- Controller -------------------------------------------------------------------------------

    private inner class Pad {
        @Volatile
        var node: String? = null
        private val lock = Any()
        private val keys = HashSet<Int>()
        private val axisValues = IntArray(ABS_CNT)

        private var grab: FileInputStream? = null
        private var owner = Owner.NONE
        private var grabThread: Thread? = null
        private var grabTimeout: ScheduledFuture<*>? = null
        private var startKeys: Set<Int> = emptySet()
        private var startAxisValues = IntArray(ABS_CNT)
        private var hatX = 0
        private var hatY = 0

        private val homeLock = Any()
        private var homeDown = false
        private var homeLongFired = false
        private var homeLong: ScheduledFuture<*>? = null

        fun loop() {
            while (true) {
                val controllerNode = findNode { it.name == CONTROLLER_NAME }
                if (controllerNode == null) {
                    Thread.sleep(3000)
                    continue
                }
                node = controllerNode
                Timber.i("watching controller on $controllerNode")
                try {
                    FileInputStream(controllerNode).use { stream ->
                        readEvents(stream) { type, code, value ->
                            track(type, code, value)
                            if (type == EV_KEY && code == KEY_HOME) onHome(value)
                            if (type == EV_KEY && value == 1 && (code == BTN_TL || code == BTN_TR)) post { listener.onShoulder() }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "controller read failed")
                }
                Thread.sleep(1000)
            }
        }

        private fun track(type: Int, code: Int, value: Int) {
            synchronized(lock) {
                when (type) {
                    EV_KEY -> if (value != 0) keys += code else keys -= code
                    EV_ABS -> if (code in 0 until ABS_CNT) axisValues[code] = value
                }
            }
        }

        fun isDown(code: Int): Boolean = synchronized(lock) { code in keys }

        fun snapshot(): Pair<Set<Int>, IntArray> = synchronized(lock) { HashSet(keys) to axisValues.copyOf() }

        fun holds(who: Owner): Boolean = synchronized(lock) { owner == who && grabThread?.isAlive == true }

        /** Combo detection starts from what is held now: nothing already down counts as a press. */
        fun armCombos() {
            synchronized(lock) {
                hatX = axisValues[ABS_HAT0X]
                hatY = axisValues[ABS_HAT0Y]
                triggerKey[0] = BTN_TL2 in keys
                triggerKey[1] = BTN_TR2 in keys
                triggerAxis[0] = axisValues[ABS_Z] >= TRIGGER_ON
                triggerAxis[1] = axisValues[ABS_RZ] >= TRIGGER_ON
                triggerDown[0] = triggerKey[0] || triggerAxis[0]
                triggerDown[1] = triggerKey[1] || triggerAxis[1]
            }
        }

        /** Grabs the controller exclusively for [who]; false if it is already grabbed or absent. */
        fun beginGrab(who: Owner, handler: (Int, Int, Int) -> Unit, timeoutMs: Long): Boolean {
            val controllerNode = node ?: return false
            synchronized(lock) { if (grab != null) return false }
            val stream = try {
                FileInputStream(controllerNode)
            } catch (e: Exception) {
                return false
            }
            try {
                IOCTL_INT.invoke(null, stream.fd, EVIOCGRAB)
            } catch (e: Throwable) {
                Timber.w(e, "EVIOCGRAB failed; combos will also reach apps")
                stream.close()
                return false
            }
            synchronized(lock) {
                grab = stream
                owner = who
                startKeys = HashSet(keys)
                startAxisValues = axisValues.copyOf()
                hatX = startAxisValues[ABS_HAT0X]
                hatY = startAxisValues[ABS_HAT0Y]
                // A trigger already held when AYN goes down doesn't count as a press.
                triggerKey[0] = BTN_TL2 in startKeys
                triggerKey[1] = BTN_TR2 in startKeys
                triggerAxis[0] = startAxisValues[ABS_Z] >= TRIGGER_ON
                triggerAxis[1] = startAxisValues[ABS_RZ] >= TRIGGER_ON
                triggerDown[0] = triggerKey[0] || triggerAxis[0]
                triggerDown[1] = triggerKey[1] || triggerAxis[1]
            }
            grabThread = thread(name = "pad-grab", isDaemon = true) {
                try {
                    readEvents(stream) { type, code, value ->
                        track(type, code, value)
                        handler(type, code, value)
                    }
                } catch (_: Exception) {
                    // closed by endGrab()
                }
            }
            if (timeoutMs > 0) {
                grabTimeout = worker.schedule({
                    Timber.w("$who held the controller for ${timeoutMs}ms, releasing it")
                    if (who == Owner.AYN) comboUsed = true
                    endGrab(who)
                }, timeoutMs, TimeUnit.MILLISECONDS)
            }
            return true
        }

        // L2 / R2 are analog (ABS_Z / ABS_RZ, 0..255) and also report a button when fully
        // pulled; either one counts, and a pull fires once until the trigger is let go again.
        private val triggerKey = BooleanArray(2)
        private val triggerAxis = BooleanArray(2)
        private val triggerDown = BooleanArray(2)

        private fun trigger(side: Int, key: Boolean? = null, axis: Int? = null) {
            if (key != null) triggerKey[side] = key
            if (axis != null) triggerAxis[side] = if (triggerAxis[side]) axis >= TRIGGER_OFF else axis >= TRIGGER_ON
            val down = triggerKey[side] || triggerAxis[side]
            if (down && !triggerDown[side]) combo(if (side == 0) Combo.L2 else Combo.R2)
            triggerDown[side] = down
        }

        fun detect(type: Int, code: Int, value: Int) {
            when (type) {
                EV_KEY -> when (code) {
                    BTN_TL2 -> trigger(0, key = value != 0)
                    BTN_TR2 -> trigger(1, key = value != 0)
                }
                EV_ABS -> when (code) {
                    ABS_Z -> trigger(0, axis = value)
                    ABS_RZ -> trigger(1, axis = value)
                }
            }
            when (type) {
                EV_KEY -> if (value == 1) when (code) {
                    BTN_TL -> combo(Combo.L1)
                    BTN_TR -> combo(Combo.R1)
                    BTN_EAST -> combo(Combo.B)
                    BTN_NORTH -> combo(Combo.X)
                    BTN_WEST -> combo(Combo.Y)
                    BTN_DPAD_UP, KEY_UP -> combo(Combo.UP)
                    BTN_DPAD_DOWN, KEY_DOWN -> combo(Combo.DOWN)
                    BTN_DPAD_LEFT, KEY_LEFT -> combo(Combo.LEFT)
                    BTN_DPAD_RIGHT, KEY_RIGHT -> combo(Combo.RIGHT)
                }
                EV_ABS -> when (code) {
                    ABS_HAT0Y -> {
                        if (value < 0 && hatY >= 0) combo(Combo.UP)
                        if (value > 0 && hatY <= 0) combo(Combo.DOWN)
                        hatY = value
                    }
                    ABS_HAT0X -> {
                        if (value < 0 && hatX >= 0) combo(Combo.LEFT)
                        if (value > 0 && hatX <= 0) combo(Combo.RIGHT)
                        hatX = value
                    }
                }
            }
        }

        fun endGrab(who: Owner) {
            val stream: FileInputStream
            val reader: Thread?
            synchronized(lock) {
                if (owner != who) return
                stream = grab ?: return
                grab = null
                owner = Owner.NONE
                reader = grabThread
                grabThread = null
            }
            grabTimeout?.cancel(false)
            try {
                stream.close() // wakes the blocked reader; the grab is released with the last file reference
            } catch (_: Exception) {
            }
            reader?.join(300)
            resync()
            // A Home release that went to the grab never reaches our own reader (and the kernel
            // drops a re-injected one, since the key is already up): reset the Home state here.
            synchronized(homeLock) {
                if (homeDown && !isDown(KEY_HOME)) {
                    homeDown = false
                    homeLong?.cancel(false)
                    homeLong = null
                }
            }
        }

        /**
         * Android saw nothing while we held the grab. Release anything it still believes is
         * pressed and bring analog axes up to date, but never replay a press that started during
         * the grab (that would send the combo button to the game after all).
         */
        private fun resync() {
            val events = ArrayList<IntArray>()
            synchronized(lock) {
                for (code in startKeys) if (code !in keys) events += intArrayOf(EV_KEY, code, 0)
                for (axis in ANALOG_AXES) if (axisValues[axis] != startAxisValues[axis]) events += intArrayOf(EV_ABS, axis, axisValues[axis])
                for (hat in intArrayOf(ABS_HAT0X, ABS_HAT0Y)) if (startAxisValues[hat] != 0 && axisValues[hat] == 0) events += intArrayOf(EV_ABS, hat, 0)
            }
            val controllerNode = node ?: return
            if (events.isEmpty()) return
            try {
                val buffer = ByteBuffer.allocate(EVENT_SIZE * (events.size + 1)).order(ByteOrder.LITTLE_ENDIAN)
                for (event in events) putEvent(buffer, event[0], event[1], event[2])
                putEvent(buffer, EV_SYN, 0, 0)
                FileOutputStream(controllerNode).use { it.write(buffer.array()) }
            } catch (e: Exception) {
                Timber.w(e, "controller resync failed")
            }
        }

        /** Short press = Home on release (like stock), long press = app switcher while still held. */
        private fun onHome(value: Int) {
            synchronized(homeLock) {
                when (value) {
                    1 -> {
                        if (homeDown) return
                        homeDown = true
                        homeLongFired = false
                        if (homeRemapped) post { listener.onHomeDown() }
                        homeLong = worker.schedule({
                            val fire = synchronized(homeLock) {
                                if (homeDown) homeLongFired = true
                                homeDown
                            }
                            if (fire) listener.onHomeLong()
                        }, LONG_HOME_MS, TimeUnit.MILLISECONDS)
                    }
                    0 -> {
                        if (!homeDown) return
                        homeDown = false
                        homeLong?.cancel(false)
                        homeLong = null
                        // Without the key layout remap Android already handled the short press.
                        if (!homeLongFired && homeRemapped) post { listener.onHomeSingle() }
                    }
                }
            }
        }
    }

    // ---- evdev helpers ----------------------------------------------------------------------------

    class InputDeviceInfo(val name: String, val handler: String, private val keyBits: List<Long>) {
        fun hasKey(code: Int): Boolean {
            val wordIndex = code / 64
            return wordIndex < keyBits.size && (keyBits[wordIndex] shr (code % 64)) and 1L == 1L
        }
    }

    private fun devices(): List<InputDeviceInfo> = parseDevices(sys.read("/proc/bus/input/devices"))

    private fun findNode(match: (InputDeviceInfo) -> Boolean): String? =
        devices().firstOrNull(match)?.let { "/dev/input/${it.handler}" }

    private inline fun readEvents(input: InputStream, onEvent: (Int, Int, Int) -> Unit) {
        val bytes = ByteArray(EVENT_SIZE * 64)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (true) {
            val bytesRead = input.read(bytes)
            if (bytesRead <= 0) return
            var offset = 0
            while (offset + EVENT_SIZE <= bytesRead) {
                onEvent(buffer.getShort(offset + 16).toInt() and 0xFFFF, buffer.getShort(offset + 18).toInt() and 0xFFFF, buffer.getInt(offset + 20))
                offset += EVENT_SIZE
            }
        }
    }

    private fun putEvent(buffer: ByteBuffer, type: Int, code: Int, value: Int) {
        buffer.putLong(0L).putLong(0L).putShort(type.toShort()).putShort(code.toShort()).putInt(value)
    }

    companion object {
        /** Input devices from /proc/bus/input/devices: name, event node and key bitmap. */
        fun parseDevices(text: String?): List<InputDeviceInfo> {
            if (text == null) return emptyList()
            return text.split(Regex("\n\\s*\n")).mapNotNull { block ->
                val name = Regex("N: Name=\"([^\"]*)\"").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val handler = Regex("H: Handlers=.*?(event\\d+)").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val bits = Regex("B: KEY=([0-9a-f ]+)").find(block)?.groupValues?.get(1)
                    ?.trim()?.split(' ')?.reversed()?.map { it.toULong(16).toLong() }
                    ?: emptyList()
                InputDeviceInfo(name, handler, bits)
            }
        }

        const val EVENT_SIZE = 24 // struct input_event on a 64-bit kernel
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val EV_SW = 5
        const val SW_LID = 0
        const val KEY_POWER = 116
        const val KEY_BACK = 158
        const val BTN_SOUTH = 304 // "A"
        const val BTN_THUMBR = 318
        const val KEY_F24 = 194
        const val KEY_HOME = 102
        const val KEY_UP = 103
        const val KEY_LEFT = 105
        const val KEY_RIGHT = 106
        const val KEY_DOWN = 108
        const val BTN_TL = 310 // L1
        const val BTN_TR = 311 // R1
        const val BTN_TL2 = 312 // L2 (digital)
        const val BTN_TR2 = 313 // R2 (digital)
        const val ABS_Z = 0x02 // L2 (analog, 0..255)
        const val ABS_RZ = 0x05 // R2 (analog, 0..255)
        const val TRIGGER_ON = 180
        const val TRIGGER_OFF = 110
        const val BTN_EAST = 305 // "B" in the controller key layout
        const val BTN_NORTH = 307 // "X"
        const val BTN_WEST = 308 // "Y"
        const val BTN_DPAD_UP = 544
        const val BTN_DPAD_DOWN = 545
        const val BTN_DPAD_LEFT = 546
        const val BTN_DPAD_RIGHT = 547
        const val ABS_HAT0X = 0x10
        const val ABS_HAT0Y = 0x11
        const val ABS_CNT = 0x40
        val ANALOG_AXES = intArrayOf(0, 1, 2, 3, 4, 5, 9, 10)

        /** _IOW('E', 0x90, int) */
        const val EVIOCGRAB = 0x40044590

        /** _IOC(_IOC_READ, 'E', 0x1b, 4): the first 32 switch bits. */
        const val EVIOCGSW_4 = 0x8004451B.toInt()
        const val POWER_KEY_NAME = "pmic_pwrkey"
        const val MAX_GRAB_MS = 8000L
        const val LONG_HOME_MS = 450L

        const val CONTROLLER_NAME = "Xbox Wireless Controller"
        const val CONTROLLER_KEY_LAYOUT = "/vendor/usr/keylayout/Vendor_045e_Product_028e_Version_1234.kl"

        /** android.system.Os.ioctlInt(fd, cmd) passes a non-null int*, which EVIOCGRAB treats as "grab". */
        private val IOCTL_INT by lazy {
            Os::class.java.getMethod("ioctlInt", FileDescriptor::class.java, Int::class.javaPrimitiveType)
        }
    }
}
