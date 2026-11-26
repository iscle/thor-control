package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.TestInputDevices.AYN_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER
import com.thorctl.daemon.hardware.input.TestInputDevices.CONTROLLER_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.GPIO_KEYS
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_KEY
import com.thorctl.daemon.hardware.input.TestInputDevices.POWER_NODE
import com.thorctl.daemon.hardware.input.TestInputDevices.entry
import com.thorctl.daemon.hardware.input.TestInputDevices.install
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputNodesTest {
    private val sys = FakeSys()
    private val nodes = InputNodes(sys)

    @Test
    fun parsesProcInputDevices() {
        val text = """
            I: Bus=0019 Vendor=0001 Product=0001 Version=0100
            N: Name="gpio-keys"
            H: Handlers=kbd event2
            B: KEY=4000000000000 0

            N: Name="Xbox Wireless Controller"
            H: Handlers=event5 js0
            B: KEY=7fff000000000000 0 100040000000 0 0

            N: Name="no handler"
        """.trimIndent()
        val devices = InputNodes.parse(text)
        assertEquals(listOf("gpio-keys", "Xbox Wireless Controller"), devices.map { it.name })
        assertEquals(listOf("event2", "event5"), devices.map { it.handler })
        assertEquals("/dev/input/event5", devices[1].path)
        assertTrue(devices[0].hasKey(114)) // bit 50 of the first word
        assertTrue(devices[1].hasKey(EvdevCodes.BTN_SOUTH))
        assertTrue(InputNodes.parse(null).isEmpty())
    }

    @Test
    fun findsTheThorNodes() {
        install(sys, POWER_KEY, GPIO_KEYS, CONTROLLER)
        assertEquals(AYN_NODE, nodes.aynButton())
        assertEquals(CONTROLLER_NODE, nodes.controller())
        assertEquals(POWER_NODE, nodes.powerKey())
    }

    @Test
    fun aynButtonPrefersGpioKeysAndNeverTakesAController() {
        val controllerWithF24 = entry("AYN Controller", "event7", "4 0 0 0")
        val otherKeys = entry("other-keys", "event3", "4 0 0 0")
        install(sys, controllerWithF24, otherKeys, GPIO_KEYS)
        assertEquals(AYN_NODE, nodes.aynButton())
        install(sys, controllerWithF24, otherKeys)
        assertEquals("/dev/input/event3", nodes.aynButton())
        install(sys, controllerWithF24, entry("gpio-keys", "event2", "0"))
        assertNull(nodes.aynButton())
    }

    @Test
    fun powerKeyFallsBackToAnyPwrkeyWithKeyPower() {
        install(sys, entry("qpnp_pwrkey", "event9", "0"), entry("pm8550_pwrkey", "event8", "10000000000000 0"))
        assertEquals("/dev/input/event8", nodes.powerKey())
    }

    @Test
    fun nothingFoundWithoutTheDeviceList() {
        assertNull(nodes.aynButton())
        assertNull(nodes.controller())
        assertNull(nodes.powerKey())
    }
}
