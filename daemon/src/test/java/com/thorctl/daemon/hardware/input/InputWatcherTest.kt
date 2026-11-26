package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_NORTH
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_F24
import com.thorctl.daemon.hardware.input.TestInputDevices.AYN_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.GPIO_KEYS
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputWatcherTest {
    private val sys = FakeSys().apply { TestInputDevices.install(this, GPIO_KEYS, CONTROLLER) }
    private val evdev = FakeEvdev()
    private val worker = FakeInputWorker()
    private val listener = RecordingListener()
    private val watcher = run {
        val nodes = InputNodes(sys)
        val controller = ControllerInput(sys, nodes, evdev, worker)
        val lidPowerKey = LidPowerKey(nodes, evdev, worker)
        InputWatcher(controller, AynButton(nodes, evdev, controller, lidPowerKey, worker), lidPowerKey)
    }
    private val events = CopyOnWriteArrayList<Pair<Int, Int>>()

    private fun started(): FakeEvdevDevice {
        watcher.start(listener)
        evdev.awaitOpened(CONTROLLER_NODE)
        return evdev.awaitOpened(AYN_NODE)
    }

    @Test
    fun gamepadEventsGoToCombosWhileAynIsHeld() {
        val button = started()
        assertTrue(watcher.hasController)
        assertTrue(watcher.grabForGamepad { _, code, value -> events += code to value })
        assertTrue(watcher.gamepadGrabbed())
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)

        grab.send(EV_KEY, BTN_EAST, 1)
        grab.send(EV_KEY, BTN_EAST, 0)
        button.send(EV_KEY, KEY_F24, 1)
        grab.send(EV_KEY, BTN_EAST, 1)
        grab.send(EV_KEY, BTN_EAST, 0)
        button.send(EV_KEY, KEY_F24, 0)
        grab.send(EV_KEY, BTN_NORTH, 1)

        assertEquals(listOf(BTN_EAST to 1, BTN_EAST to 0, BTN_NORTH to 1), events)
        assertEquals(listOf("combo:B"), listener.calls)
        assertTrue(watcher.gamepadGrabbed())
        assertTrue(watcher.isDown(BTN_NORTH))
        assertEquals(setOf(BTN_NORTH), watcher.controllerSnapshot().first)

        watcher.releaseGamepad()
        assertFalse(watcher.gamepadGrabbed())
        assertTrue(grab.closed)
    }

    @Test
    fun aynTapKeepsTheRecentsGrab() {
        val button = started()
        assertTrue(watcher.grabController { _, code, value -> events += code to value })
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        button.send(EV_KEY, KEY_F24, 1)
        grab.send(EV_KEY, BTN_EAST, 1)
        button.send(EV_KEY, KEY_F24, 0)
        assertEquals(listOf("tap"), listener.calls)
        assertEquals(listOf(BTN_EAST to 1), events)
        assertFalse(grab.closed)
        watcher.releaseController()
        assertTrue(grab.closed)
    }
}
