package com.thorctl.protocol.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumWireTest {
    @Test
    fun wireNamesRoundTrip() {
        ControllerTarget.entries.forEach { assertEquals(it, ControllerTarget.fromWire(it.wire)) }
        Screen.entries.forEach { assertEquals(it, Screen.fromWire(it.wire)) }
        LedMode.entries.forEach { assertEquals(it, LedMode.fromWire(it.wire)) }
        GamepadTransport.entries.forEach { assertEquals(it, GamepadTransport.fromWire(it.wire)) }
        GameHudState.entries.forEach { assertEquals(it, GameHudState.fromWire(it.wire)) }
    }

    @Test
    fun unknownNamesAreNull() {
        assertNull(ControllerTarget.fromWire("left"))
        assertNull(Screen.fromWire(null))
        assertNull(GamepadTransport.fromWire("serial"))
        assertEquals(GameHudState.STARTED, GameHudState.fromWire("whatever"))
    }

    @Test
    fun controllerTileCyclesThroughEveryTarget() {
        assertEquals(ControllerTarget.TOP, ControllerTarget.FREE.next())
        assertEquals(ControllerTarget.BOTTOM, ControllerTarget.TOP.next())
        assertEquals(ControllerTarget.FREE, ControllerTarget.BOTTOM.next())
    }
}
