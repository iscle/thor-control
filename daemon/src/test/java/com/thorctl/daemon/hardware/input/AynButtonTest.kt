package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_SW
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_F24
import com.thorctl.daemon.hardware.input.EvdevCodes.SW_LID
import com.thorctl.daemon.hardware.input.TestInputDevices.AYN_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.GPIO_KEYS
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_KEY
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_NODE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AynButtonTest {
    private val sys = FakeSys()
    private val evdev = FakeEvdev()
    private val worker = FakeInputWorker()
    private val listener = RecordingListener()
    private val nodes = InputNodes(sys)
    private val controller = ControllerInput(sys, nodes, evdev, worker)
    private val lidPowerKey = LidPowerKey(nodes, evdev, worker)
    private val ayn = AynButton(nodes, evdev, controller, lidPowerKey, worker)

    /** Starts the AYN reader (and the controller's, if it is present) and returns the AYN node. */
    private fun start(vararg devices: String): FakeEvdevDevice {
        TestInputDevices.install(sys, *devices)
        if (CONTROLLER in devices) {
            controller.start(listener)
            evdev.awaitOpened(CONTROLLER_NODE)
        }
        ayn.start(listener)
        return evdev.awaitOpened(AYN_NODE)
    }

    @Test
    fun releaseWithoutAComboIsATap() {
        val button = start(GPIO_KEYS)
        button.send(EV_KEY, KEY_F24, 1)
        assertTrue(ayn.isDown)
        button.send(EV_KEY, KEY_F24, 2)
        button.send(EV_KEY, KEY_F24, 0)
        assertFalse(ayn.isDown)
        assertEquals(listOf("tap"), listener.calls)
    }

    @Test
    fun aComboWhileHeldGoesToTheListenerInsteadOfATap() {
        val button = start(GPIO_KEYS, CONTROLLER)
        button.send(EV_KEY, KEY_F24, 1)
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        assertTrue(controller.holds(GrabOwner.AYN))
        grab.send(EV_KEY, BTN_EAST, 1)
        button.send(EV_KEY, KEY_F24, 0)
        assertEquals(listOf("combo:B"), listener.calls)
        assertTrue(grab.closed)
    }

    @Test
    fun heldTooLongIsNoTap() {
        val button = start(GPIO_KEYS, CONTROLLER)
        button.send(EV_KEY, KEY_F24, 1)
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        worker.advance(AynButton.MAX_GRAB_MS)
        assertTrue(grab.closed)
        button.send(EV_KEY, KEY_F24, 0)
        assertTrue(listener.calls.isEmpty())
    }

    @Test
    fun theLidSwitchOnTheSameNodeGoesToTheLidPowerKey() {
        evdev.switches = 1 shl SW_LID
        val button = start(GPIO_KEYS, POWER_KEY)
        button.send(EV_KEY, KEY_F24, 2) // read once the lid state was applied
        assertTrue(lidPowerKey.lidClosed)
        val power = evdev.awaitOpened(POWER_NODE)
        button.send(EV_SW, SW_LID, 0)
        assertFalse(lidPowerKey.lidClosed)
        assertTrue(power.closed)
    }
}
