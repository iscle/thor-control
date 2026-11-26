package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.EvdevCodes.ABS_RZ
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_EAST
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_SOUTH
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TL
import com.thorctl.daemon.hardware.input.EvdevCodes.BTN_TR
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_ABS
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_SYN
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_HOME
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER_NODE
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerInputTest {
    private val sys = FakeSys().apply {
        this[ControllerInput.CONTROLLER_KEY_LAYOUT] = "key 304   BUTTON_A\nkey 158   BACK\n"
        TestInputDevices.install(this, CONTROLLER)
    }
    private val evdev = FakeEvdev()
    private val worker = FakeInputWorker()
    private val listener = RecordingListener()
    private val events = CopyOnWriteArrayList<EvdevEvent>()
    private val handler: EventHandler = { type, code, value -> events += EvdevEvent(type, code, value) }

    private fun controller() = ControllerInput(sys, InputNodes(sys), evdev, worker)

    /** A started controller whose reader has opened the node. */
    private fun started(): Pair<ControllerInput, FakeEvdevDevice> {
        val controller = controller()
        controller.start(listener)
        return controller to evdev.awaitOpened(CONTROLLER_NODE)
    }

    @Test
    fun homeIsRemappedUnlessTheKeyLayoutStillMapsIt() {
        assertTrue(controller().homeRemapped)
        sys[ControllerInput.CONTROLLER_KEY_LAYOUT] = "key 304   BUTTON_A\nkey 102   HOME\n"
        assertFalse(controller().homeRemapped)
        sys.files.remove(ControllerInput.CONTROLLER_KEY_LAYOUT)
        assertFalse(controller().homeRemapped)
    }

    @Test
    fun noGrabWithoutTheController() {
        val controller = controller()
        assertEquals(null, controller.node)
        assertFalse(controller.beginGrab(GrabOwner.RECENTS, handler))
    }

    @Test
    fun readerTracksKeysAndReportsHomeAndShoulders() {
        val (controller, device) = started()
        assertEquals(CONTROLLER_NODE, controller.node)
        device.send(EV_KEY, BTN_SOUTH, 1)
        assertTrue(controller.isDown(BTN_SOUTH))
        device.send(EV_KEY, KEY_HOME, 1)
        device.send(EV_KEY, KEY_HOME, 0)
        device.send(EV_KEY, BTN_TL, 1)
        device.send(EV_KEY, BTN_TL, 0)
        device.send(EV_KEY, BTN_TR, 1)
        assertEquals(listOf("homeDown", "homeSingle", "shoulder", "shoulder"), listener.calls)
    }

    @Test
    fun oneOwnerAtATime() {
        val (controller, _) = started()
        assertTrue(controller.beginGrab(GrabOwner.RECENTS, handler))
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        assertTrue(grab.grabbed)
        assertFalse(controller.beginGrab(GrabOwner.GAMEPAD, handler))
        assertTrue(controller.holds(GrabOwner.RECENTS))
        assertFalse(controller.holds(GrabOwner.GAMEPAD))

        controller.endGrab(GrabOwner.GAMEPAD)
        assertTrue(controller.holds(GrabOwner.RECENTS))
        assertFalse(grab.closed)

        controller.endGrab(GrabOwner.RECENTS)
        assertTrue(grab.closed)
        assertFalse(controller.holds(GrabOwner.RECENTS))
        assertTrue(controller.beginGrab(GrabOwner.GAMEPAD, handler))
    }

    @Test
    fun grabbedEventsGoToTheHandlerAndAndroidIsResyncedAfter() {
        val (controller, device) = started()
        device.send(EV_KEY, BTN_SOUTH, 1)
        controller.beginGrab(GrabOwner.GAMEPAD, handler)
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        grab.send(EV_KEY, BTN_SOUTH, 0)
        grab.send(EV_KEY, BTN_EAST, 1)
        grab.send(EV_ABS, ABS_RZ, 200)
        assertEquals(
            listOf(EvdevEvent(EV_KEY, BTN_SOUTH, 0), EvdevEvent(EV_KEY, BTN_EAST, 1), EvdevEvent(EV_ABS, ABS_RZ, 200)),
            events,
        )
        assertTrue(controller.isDown(BTN_EAST))
        assertTrue(evdev.writes.isEmpty())

        controller.endGrab(GrabOwner.GAMEPAD)
        assertEquals(
            listOf(
                CONTROLLER_NODE to listOf(
                    EvdevEvent(EV_KEY, BTN_SOUTH, 0),
                    EvdevEvent(EV_ABS, ABS_RZ, 200),
                    EvdevEvent(EV_SYN, 0, 0),
                ),
            ),
            evdev.writes,
        )
    }

    @Test
    fun nothingIsWrittenWhenAndroidMissedNothing() {
        val (controller, _) = started()
        controller.beginGrab(GrabOwner.RECENTS, handler)
        evdev.awaitOpened(CONTROLLER_NODE, 2).send(EV_KEY, BTN_EAST, 1)
        controller.endGrab(GrabOwner.RECENTS)
        assertTrue(evdev.writes.isEmpty())
    }

    @Test
    fun aTimedGrabIsReleasedAfterItsTimeout() {
        val (controller, _) = started()
        var timedOut = false
        controller.beginGrab(GrabOwner.AYN, handler, 8000, onTimeout = { timedOut = true })
        val grab = evdev.awaitOpened(CONTROLLER_NODE, 2)
        worker.advance(7999)
        assertTrue(controller.holds(GrabOwner.AYN))
        worker.advance(1)
        assertTrue(timedOut)
        assertTrue(grab.closed)
        assertFalse(controller.holds(GrabOwner.AYN))
    }

    @Test
    fun aFailedGrabClosesTheNode() {
        val (controller, _) = started()
        evdev.failGrab = true
        assertFalse(controller.beginGrab(GrabOwner.RECENTS, handler))
        assertTrue(evdev.awaitOpened(CONTROLLER_NODE, 2).closed)
        evdev.failGrab = false
        assertTrue(controller.beginGrab(GrabOwner.RECENTS, handler))
    }

    @Test
    fun aGrabIsNoLongerHeldOnceTheNodeGoesAway() {
        val (controller, _) = started()
        controller.beginGrab(GrabOwner.GAMEPAD, handler)
        evdev.awaitOpened(CONTROLLER_NODE, 2).disconnect()
        val deadline = System.currentTimeMillis() + 2000
        while (controller.holds(GrabOwner.GAMEPAD) && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertFalse(controller.holds(GrabOwner.GAMEPAD))
    }

    @Test
    fun homeReleaseMissedDuringAGrabIsForgotten() {
        val (controller, device) = started()
        device.send(EV_KEY, KEY_HOME, 1)
        controller.beginGrab(GrabOwner.RECENTS, handler)
        evdev.awaitOpened(CONTROLLER_NODE, 2).send(EV_KEY, KEY_HOME, 0)
        controller.endGrab(GrabOwner.RECENTS)
        worker.advance(HomeButton.LONG_HOME_MS)
        device.send(EV_KEY, KEY_HOME, 0)
        assertEquals(listOf("homeDown"), listener.calls)
    }
}
