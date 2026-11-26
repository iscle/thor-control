package com.thorctl.protocol.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanProfileTest {
    private val curve = FanProfile("c", "Custom", listOf(FanPoint(60f, 40f), FanPoint(40f, 0f), FanPoint(80f, 100f)))

    @Test
    fun interpolatesBetweenPointsInTemperatureOrder() {
        assertEquals(20f, curve.percentAt(50f))
        assertEquals(70f, curve.percentAt(70f))
        assertEquals(40f, curve.percentAt(60f))
    }

    @Test
    fun clampsOutsideTheCurve() {
        assertEquals(0f, curve.percentAt(10f))
        assertEquals(100f, curve.percentAt(95f))
    }

    @Test
    fun autoHasNoCurve() {
        val auto = FanProfile.BUILTINS.first()
        assertTrue(auto.isAuto)
        assertEquals(0f, auto.percentAt(90f))
    }

    @Test
    fun onlyTheShippedProfilesAreBuiltin() {
        assertTrue(FanProfile.BUILTINS.all { it.isBuiltin })
        assertFalse(curve.isBuiltin)
        assertTrue(FanProfile.BUILTINS.any { it.id == FanProfile.SMART_ID })
    }

    @Test
    fun stateFallsBackToAutoForAnUnknownProfile() {
        val state = DaemonState("gone", FanProfile.BUILTINS, 0.5f, 0.5f, ControllerTarget.FREE, LedState(), true, 0, 1, true)
        assertEquals(FanProfile.AUTO_ID, state.activeProfile.id)
        assertEquals(FanProfile.AUTO_ID, state.runningProfile.id)
        assertEquals("max", state.copy(fanOverride = "max").runningProfile.id)
    }
}
