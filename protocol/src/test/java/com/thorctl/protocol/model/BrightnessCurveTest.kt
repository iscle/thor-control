package com.thorctl.protocol.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BrightnessCurveTest {
    @Test
    fun positionAndLinearAreInverses() {
        // Below 15 % the linear value is clamped to the panel's minimum.
        for (step in 3..20) {
            val position = step / 20f
            assertEquals(position, BrightnessCurve.toPosition(BrightnessCurve.toLinear(position)), 1e-3f)
        }
    }

    @Test
    fun neverFullyDark() {
        assertEquals(0.004f, BrightnessCurve.toLinear(0f))
        assertEquals(1f, BrightnessCurve.toLinear(1f), 1e-4f)
    }

    @Test
    fun stepsAreTenPercentSnappedToFive() {
        val at = BrightnessCurve.toLinear(0.43f)
        assertEquals(0.55f, BrightnessCurve.step(at, 1), 1e-6f)
        assertEquals(0.35f, BrightnessCurve.step(at, -1), 1e-6f)
    }

    @Test
    fun stepsStopAtTheEnds() {
        assertEquals(1f, BrightnessCurve.step(1f, 1))
        assertEquals(0f, BrightnessCurve.step(0.004f, -1))
    }
}
