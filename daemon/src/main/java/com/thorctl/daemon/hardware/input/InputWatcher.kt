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

    /** Evdev codes the features read raw controller events with; the same as [EvdevCodes]. */
    companion object {
        const val EV_SYN = EvdevCodes.EV_SYN
        const val EV_KEY = EvdevCodes.EV_KEY
        const val EV_ABS = EvdevCodes.EV_ABS
        const val EV_SW = EvdevCodes.EV_SW
        const val SW_LID = EvdevCodes.SW_LID
        const val KEY_HOME = EvdevCodes.KEY_HOME
        const val KEY_UP = EvdevCodes.KEY_UP
        const val KEY_LEFT = EvdevCodes.KEY_LEFT
        const val KEY_RIGHT = EvdevCodes.KEY_RIGHT
        const val KEY_DOWN = EvdevCodes.KEY_DOWN
        const val KEY_POWER = EvdevCodes.KEY_POWER
        const val KEY_BACK = EvdevCodes.KEY_BACK
        const val KEY_F24 = EvdevCodes.KEY_F24
        const val BTN_SOUTH = EvdevCodes.BTN_SOUTH
        const val BTN_EAST = EvdevCodes.BTN_EAST
        const val BTN_NORTH = EvdevCodes.BTN_NORTH
        const val BTN_WEST = EvdevCodes.BTN_WEST
        const val BTN_TL = EvdevCodes.BTN_TL
        const val BTN_TR = EvdevCodes.BTN_TR
        const val BTN_TL2 = EvdevCodes.BTN_TL2
        const val BTN_TR2 = EvdevCodes.BTN_TR2
        const val BTN_THUMBR = EvdevCodes.BTN_THUMBR
        const val BTN_DPAD_UP = EvdevCodes.BTN_DPAD_UP
        const val BTN_DPAD_DOWN = EvdevCodes.BTN_DPAD_DOWN
        const val BTN_DPAD_LEFT = EvdevCodes.BTN_DPAD_LEFT
        const val BTN_DPAD_RIGHT = EvdevCodes.BTN_DPAD_RIGHT
        const val ABS_Z = EvdevCodes.ABS_Z
        const val ABS_RZ = EvdevCodes.ABS_RZ
        const val ABS_HAT0X = EvdevCodes.ABS_HAT0X
        const val ABS_HAT0Y = EvdevCodes.ABS_HAT0Y
        const val ABS_CNT = EvdevCodes.ABS_CNT
    }
}
