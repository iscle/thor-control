package com.thorctl.protocol.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {
    private val stats = Stats(
        cpuLoad = 0.5f, cpuFreqMhz = 2000, clusterFreqsMhz = listOf(1800, 2000), cpuTemp = 50f, gpuLoad = 0.2f,
        gpuFreqMhz = 600, gpuTemp = 45f, ramTotalMb = 8000, ramAvailMb = 2000, batteryLevel = 80,
        batteryStatus = "charging", batteryWatts = 5f, inputWatts = 0.01f, batteryTemp = 30f, fanPwm = 128, fanTemp = 52f,
    )

    @Test
    fun derivedValues() {
        assertTrue(stats.charging)
        assertEquals(false, stats.plugged)
        assertEquals(50, stats.fanPercent)
        assertEquals(6000, stats.ramUsedMb)
        assertEquals(0.75f, stats.ramUsedFraction)
        assertEquals(0f, stats.copy(ramTotalMb = 0).ramUsedFraction)
    }
}
