package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_POWER
import com.thorctl.daemon.hardware.input.EvdevCodes.SW_LID
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * The lid switch state, and the power button taken away from Android while the lid is closed or
 * gamepad mode wants it.
 *
 * The power button is a PMIC key that wakes the SoC from suspend. With the lid closed we hold
 * an exclusive grab on it, so Android never sees the press and the screens stay off; the
 * device drops back to sleep by itself. Gamepad mode takes it over the same way: Android must
 * not go to sleep then (the controller would lose power), so presses go to its handler. The
 * PMIC's long-press hardware reset still works.
 */
@Inject
@SingleIn(DaemonScope::class)
class LidPowerKey(
    private val nodes: InputNodes,
    private val evdev: Evdev,
    private val worker: InputWorker,
) {
    private val lock = Any()
    private var powerGrab: EvdevDevice? = null

    @Volatile
    var lidClosed = false
        private set

    @Volatile
    private var powerHandler: ((down: Boolean) -> Unit)? = null

    /** Power button presses (down / up) go to [handler] instead of Android; null gives them back. */
    fun setPowerKeyHandler(handler: ((down: Boolean) -> Unit)?) {
        synchronized(lock) {
            powerHandler = handler
            updatePowerGrab()
        }
    }

    /** Applies the lid switch's current state as [device] (the node carrying the switch) reports it; unchanged if it can't be read. */
    fun readLid(device: EvdevDevice) {
        lidClosedNow(device)?.let { setLidClosed(it) }
    }

    fun setLidClosed(closed: Boolean) {
        synchronized(lock) {
            if (closed != lidClosed) Timber.i(if (closed) "lid closed: power button ignored" else "lid open")
            lidClosed = closed
            updatePowerGrab()
        }
    }

    /** An event from the grabbed power button node. */
    fun onPowerEvent(type: Int, code: Int, value: Int) {
        if (type != EV_KEY || code != KEY_POWER || value == 2) return
        when {
            lidClosed -> if (value == 1) Timber.i("power button pressed with the lid closed: ignored")
            else -> powerHandler?.let { handler -> worker.post { handler(value == 1) } }
        }
    }

    /** Current lid switch state through EVIOCGSW, or null if it can't be read. */
    private fun lidClosedNow(device: EvdevDevice): Boolean? = try {
        (device.switches() and (1 shl SW_LID)) != 0
    } catch (e: Throwable) {
        Timber.w(e, "reading the lid switch failed")
        null
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
        val node = nodes.powerKey()
        if (node == null) {
            Timber.w("power button input node not found")
            return
        }
        val device = try {
            // Note: a node opened here whose grab fails is not closed.
            evdev.open(node).also { it.grab() }
        } catch (e: Throwable) {
            Timber.w(e, "grabbing the power button failed")
            return
        }
        powerGrab = device
        Timber.i("power button grabbed ($node)")
        thread(name = "pwrkey", isDaemon = true) {
            try {
                device.readEvents(::onPowerEvent)
            } catch (_: Exception) {
                // closed when neither needs it any more
            }
        }
    }
}
