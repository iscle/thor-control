package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_SYN
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_HOME
import com.thorctl.daemon.hardware.input.EvdevCodes.SYN_REPORT
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * The built-in controller's event node. Reads it alongside Android to track what is held and to
 * catch Home and shoulder presses, and lends an exclusive grab (EVIOCGRAB) to one [GrabOwner] at
 * a time: AYN combos, the recent apps screen or gamepad mode. While grabbed, Android sees nothing;
 * when the grab ends, Android is brought back in sync.
 */
@Inject
@SingleIn(DaemonScope::class)
class ControllerInput(
    sys: Sys,
    private val nodes: InputNodes,
    private val evdev: Evdev,
    private val worker: InputWorker,
) {
    /** True when Android no longer maps the controller's KEY_HOME (module key layout applied). */
    val homeRemapped: Boolean = run {
        val keyLayout = sys.read(CONTROLLER_KEY_LAYOUT) ?: return@run false
        !Regex("^key\\s+$KEY_HOME\\s+HOME\\b", RegexOption.MULTILINE).containsMatchIn(keyLayout)
    }

    /** The controller's event node, once found. */
    @Volatile
    var node: String? = null
        private set

    private val lock = Any()
    private val state = ControllerState()
    private val combos = ComboDetector()

    private var grab: EvdevDevice? = null
    private var owner = GrabOwner.NONE
    private var grabThread: Thread? = null
    private var grabTimeout: Cancellable? = null
    private var grabStart = state.snapshot()

    @Volatile
    private var home: HomeButton? = null

    /** Starts the reader thread; Home and shoulder presses go to [listener]. */
    fun start(listener: InputWatcher.Listener) {
        val homeButton = HomeButton(homeRemapped, worker, listener)
        home = homeButton
        thread(name = "pad", isDaemon = true) { loop(homeButton, listener) }
    }

    private fun loop(homeButton: HomeButton, listener: InputWatcher.Listener) {
        while (true) {
            val controllerNode = nodes.controller()
            if (controllerNode == null) {
                Thread.sleep(3000)
                continue
            }
            node = controllerNode
            Timber.i("watching controller on $controllerNode")
            try {
                evdev.open(controllerNode).use { device ->
                    device.readEvents { type, code, value ->
                        track(type, code, value)
                        if (type == EV_KEY && code == KEY_HOME) homeButton.onKey(value)
                        if (type == EV_KEY && value == 1 && (code == BTN_TL || code == BTN_TR)) worker.post { listener.onShoulder() }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "controller read failed")
            }
            Thread.sleep(1000)
        }
    }

    private fun track(type: Int, code: Int, value: Int) {
        synchronized(lock) { state.track(type, code, value) }
    }

    fun isDown(code: Int): Boolean = synchronized(lock) { state.isDown(code) }

    fun snapshot(): ControllerSnapshot = synchronized(lock) { state.snapshot() }

    /** [who] holds the grab and its reader is still running (false once the node went away). */
    fun holds(who: GrabOwner): Boolean = synchronized(lock) { owner == who && grabThread?.isAlive == true }

    /** Combo detection starts from what is held now: nothing already down counts as a press. */
    fun armCombos() {
        synchronized(lock) { combos.arm(state.snapshot()) }
    }

    /** The AYN combo this controller event presses, if any (see [ComboDetector]). */
    fun detectCombo(type: Int, code: Int, value: Int): AynCombo? = combos.detect(type, code, value)

    /**
     * Grabs the controller exclusively for [who]; every event goes to [handler] until [endGrab].
     * False if it is already grabbed or absent. With [timeoutMs] > 0 the grab is released after
     * that long, calling [onTimeout] first.
     */
    fun beginGrab(who: GrabOwner, handler: EventHandler, timeoutMs: Long = 0, onTimeout: () -> Unit = {}): Boolean {
        val controllerNode = node ?: return false
        synchronized(lock) { if (grab != null) return false }
        val device = try {
            evdev.open(controllerNode)
        } catch (e: Exception) {
            return false
        }
        try {
            device.grab()
        } catch (e: Throwable) {
            Timber.w(e, "EVIOCGRAB failed; combos will also reach apps")
            device.close()
            return false
        }
        synchronized(lock) {
            grab = device
            owner = who
            grabStart = state.snapshot()
            combos.arm(grabStart)
        }
        grabThread = thread(name = "pad-grab", isDaemon = true) {
            try {
                device.readEvents { type, code, value ->
                    track(type, code, value)
                    handler(type, code, value)
                }
            } catch (_: Exception) {
                // closed by endGrab()
            }
        }
        if (timeoutMs > 0) {
            grabTimeout = worker.schedule(timeoutMs) {
                Timber.w("$who held the controller for ${timeoutMs}ms, releasing it")
                onTimeout()
                endGrab(who)
            }
        }
        return true
    }

    /** Releases the grab if [who] holds it, then gives Android back what it missed. */
    fun endGrab(who: GrabOwner) {
        val device: EvdevDevice
        val reader: Thread?
        synchronized(lock) {
            if (owner != who) return
            device = grab ?: return
            grab = null
            owner = GrabOwner.NONE
            reader = grabThread
            grabThread = null
        }
        grabTimeout?.cancel()
        try {
            device.close() // wakes the blocked reader; the grab is released with the last file reference
        } catch (_: Exception) {
        }
        reader?.join(300)
        resync()
        home?.resetIfReleased { isDown(KEY_HOME) }
    }

    private fun resync() {
        val events = synchronized(lock) { state.resyncEvents(grabStart) }
        val controllerNode = node ?: return
        if (events.isEmpty()) return
        try {
            evdev.write(controllerNode, events + EvdevEvent(EV_SYN, SYN_REPORT, 0))
        } catch (e: Exception) {
            Timber.w(e, "controller resync failed")
        }
    }

    companion object {
        const val CONTROLLER_KEY_LAYOUT = "/vendor/usr/keylayout/Vendor_045e_Product_028e_Version_1234.kl"
    }
}
