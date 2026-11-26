package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_SW
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_F24
import com.thorctl.daemon.hardware.input.EvdevCodes.SW_LID
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.IOException
import kotlin.concurrent.thread

/**
 * The AYN button (KEY_F24 on gpio-keys), a modifier. While it is held the built-in controller is
 * grabbed exclusively, so combo presses like AYN+B never reach the game. Releasing AYN without
 * using a combo is a "tap" (opens/closes the menu). Its node also carries the lid switch, which
 * goes to [LidPowerKey].
 */
@Inject
@SingleIn(DaemonScope::class)
class AynButton(
    private val nodes: InputNodes,
    private val evdev: Evdev,
    private val controller: ControllerInput,
    private val lidPowerKey: LidPowerKey,
    private val worker: InputWorker,
) {
    /** AYN is held right now. */
    @Volatile
    var isDown = false
        private set

    @Volatile
    private var comboUsed = false

    private lateinit var listener: InputWatcher.Listener

    /** Starts the reader thread; taps and combos go to [newListener]. */
    fun start(newListener: InputWatcher.Listener) {
        listener = newListener
        thread(name = "ayn", isDaemon = true) { loop() }
    }

    private fun loop() {
        while (true) {
            val node = nodes.aynButton()
            if (node == null) {
                Timber.w("AYN button input node not found, retrying")
                Thread.sleep(5000)
                continue
            }
            Timber.i("watching AYN button on $node")
            try {
                evdev.open(node).use { device ->
                    // The lid's hall sensor is a switch on the same node.
                    lidPowerKey.readLid(device)
                    device.readEvents { type, code, value ->
                        if (type == EV_KEY && code == KEY_F24) onAyn(value)
                        if (type == EV_SW && code == SW_LID) lidPowerKey.setLidClosed(value != 0)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "AYN read failed")
            } catch (e: RuntimeException) {
                // Thread boundary: an exception escaping would end this reader (Main's handler only
                // logs it) and the AYN button with it; log it and reopen the node like after a
                // read failure.
                Timber.e(e, "AYN event handling failed")
            }
            Thread.sleep(1000)
        }
    }

    private fun onAyn(value: Int) {
        when (value) {
            1 -> if (!isDown) {
                isDown = true
                comboUsed = false
                // Already grabbed for the recents screen: AYN then only closes it (on release).
                // Grabbed for gamepad mode: that grab's events go to combo detection meanwhile.
                val grabbed = controller.beginGrab(GrabOwner.AYN, ::detect, MAX_GRAB_MS, onTimeout = { comboUsed = true })
                if (!grabbed) controller.armCombos()
            }
            0 -> if (isDown) {
                isDown = false
                controller.endGrab(GrabOwner.AYN)
                if (!comboUsed) worker.post { listener.onAynTap() }
            }
        }
    }

    /** A controller event seen while AYN is held: a combo press goes to the listener. */
    fun detect(type: Int, code: Int, value: Int) {
        val combo = controller.detectCombo(type, code, value) ?: return
        comboUsed = true
        worker.post { listener.onCombo(combo) }
    }

    companion object {
        const val MAX_GRAB_MS = 8000L
    }
}
