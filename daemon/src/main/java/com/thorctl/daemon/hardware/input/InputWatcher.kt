package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Raw evdev input handling, below the Android input pipeline; the one entry point the rest of
 * the daemon uses:
 *
 *  - AYN button ([AynButton]) acts as a modifier: combos while held, a tap on release.
 *  - Physical Home on the controller ([HomeButton]): short press = Home, long press = app switcher.
 *  - While Thor Control's recent apps screen is open, the controller is grabbed the same way and
 *    its buttons drive that screen ([grabController]); the app underneath sees nothing.
 *  - Gamepad mode grabs the controller for as long as it lasts ([grabForGamepad]).
 *  - The lid switch and the power button ([LidPowerKey]).
 */
@Inject
@SingleIn(DaemonScope::class)
class InputWatcher(
    private val controller: ControllerInput,
    private val ayn: AynButton,
    private val lidPowerKey: LidPowerKey,
) {
    interface Listener {
        fun onAynTap()
        fun onCombo(combo: AynCombo)
        fun onHomeDown()
        fun onHomeSingle()
        fun onShoulder()
        fun onHomeLong()
    }

    /** True when Android no longer maps the controller's KEY_HOME (module key layout applied). */
    val homeRemapped: Boolean get() = controller.homeRemapped

    /** Exclusive controller grab; every raw event goes to [handler] until [releaseController]. */
    fun grabController(handler: EventHandler): Boolean = controller.beginGrab(GrabOwner.RECENTS, handler)

    fun releaseController() = controller.endGrab(GrabOwner.RECENTS)

    /**
     * Gamepad mode: the controller is grabbed for as long as it lasts and every raw event goes
     * to [handler]. While AYN is held, events go to combo detection instead, so AYN shortcuts
     * keep working (and never reach the other device).
     */
    fun grabForGamepad(handler: EventHandler): Boolean =
        controller.beginGrab(GrabOwner.GAMEPAD, { type, code, value -> if (ayn.isDown) ayn.detect(type, code, value) else handler(type, code, value) })

    fun releaseGamepad() = controller.endGrab(GrabOwner.GAMEPAD)

    /** Gamepad mode still holds a live grab (false if the controller node went away). */
    fun gamepadGrabbed(): Boolean = controller.holds(GrabOwner.GAMEPAD)

    /** Keys held and axis values right now (as last seen by us). */
    fun controllerSnapshot(): Pair<Set<Int>, IntArray> = controller.snapshot().let { it.keys to it.axisValues }

    /** The controller is present (its input node was found). */
    val hasController: Boolean get() = controller.node != null

    /** Whether a controller key is held right now (as last seen by us). */
    fun isDown(code: Int): Boolean = controller.isDown(code)

    /** The lid is closed (the power button is ignored meanwhile). */
    val lidClosed: Boolean get() = lidPowerKey.lidClosed

    /** Power button presses (down / up) go to [handler] instead of Android; null gives them back. */
    fun setPowerKeyHandler(handler: ((down: Boolean) -> Unit)?) = lidPowerKey.setPowerKeyHandler(handler)

    /** Starts reading the AYN button, the controller, Home, the lid and the power key; [newListener] gets the presses. */
    fun start(newListener: Listener) {
        Timber.i("input: home remapped=$homeRemapped")
        ayn.start(newListener)
        controller.start(newListener)
    }
}
