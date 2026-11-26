package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.EvdevCodes.EV_KEY
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_POWER
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_UP
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_KEY
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.entry
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidPowerKeyTest {
    private val sys = FakeSys().apply { TestInputDevices.install(this, POWER_KEY) }
    private val evdev = FakeEvdev()
    private val lidPowerKey = LidPowerKey(InputNodes(sys), evdev, FakeInputWorker())
    private val presses = CopyOnWriteArrayList<Boolean>()

    @Test
    fun closedLidGrabsThePowerKeyAndIgnoresPresses() {
        lidPowerKey.setLidClosed(true)
        assertTrue(lidPowerKey.lidClosed)
        val device = evdev.awaitOpened(POWER_NODE)
        assertTrue(device.grabbed)
        lidPowerKey.setPowerKeyHandler { presses += it }
        device.send(EV_KEY, KEY_POWER, 1)
        device.send(EV_KEY, KEY_POWER, 0)
        assertTrue(presses.isEmpty())
        assertEquals(1, evdev.openedCount(POWER_NODE))

        lidPowerKey.setPowerKeyHandler(null)
        lidPowerKey.setLidClosed(false)
        assertFalse(lidPowerKey.lidClosed)
        assertTrue(device.closed)
    }

    @Test
    fun handlerGetsPressesWhileSet() {
        lidPowerKey.setPowerKeyHandler { presses += it }
        val device = evdev.awaitOpened(POWER_NODE)
        device.send(EV_KEY, KEY_POWER, 1)
        device.send(EV_KEY, KEY_POWER, 2)
        device.send(EV_KEY, KEY_UP, 1)
        device.send(EV_KEY, KEY_POWER, 0)
        assertEquals(listOf(true, false), presses)
        lidPowerKey.setPowerKeyHandler(null)
        assertTrue(device.closed)
    }

    @Test
    fun fallsBackToAnotherPwrkeyNode() {
        TestInputDevices.install(sys, entry("pm8550_pwrkey", "event8", "10000000000000 0"))
        lidPowerKey.setLidClosed(true)
        assertTrue(evdev.awaitOpened("/dev/input/event8").grabbed)
    }

    @Test
    fun lidIsTrackedEvenWithoutAPowerKeyNode() {
        TestInputDevices.install(sys)
        lidPowerKey.setLidClosed(true)
        assertTrue(lidPowerKey.lidClosed)
        assertTrue(evdev.opened.isEmpty())
    }

    @Test
    fun readsTheLidSwitch() {
        lidPowerKey.readLid(FakeEvdevDevice("/dev/input/event2", switchBits = 1))
        assertTrue(lidPowerKey.lidClosed)
        lidPowerKey.readLid(FakeEvdevDevice("/dev/input/event2", switchBits = null))
        assertTrue(lidPowerKey.lidClosed)
        lidPowerKey.readLid(FakeEvdevDevice("/dev/input/event2", switchBits = 2))
        assertFalse(lidPowerKey.lidClosed)
    }
}
