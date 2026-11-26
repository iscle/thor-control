package com.thorctl.daemon.control

import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FanProfilesTest {
    private fun profile(vararg pts: Pair<Float, Float>, id: String = "custom-1", name: String = "Mine") =
        FanProfile(id, name, pts.map { FanPoint(it.first, it.second) })

    @Test
    fun clampsAndSortsPoints() {
        val p = FanProfiles.sanitize(profile(120f to 150f, 10f to -5f, 50f to 40f))!!
        assertEquals(listOf(FanPoint(20f, 0f), FanPoint(50f, 40f), FanPoint(100f, 100f)), p.points)
    }

    @Test
    fun rejectsUnusableCurves() {
        assertNull(FanProfiles.sanitize(profile(50f to 50f)))
        assertNull(FanProfiles.sanitize(profile(*Array(9) { (30f + it * 5) to 50f })))
        assertNull(FanProfiles.sanitize(profile(30f to 0f, 60f to 50f, id = " ")))
    }

    @Test
    fun namesAreShortAndNeverBlank() {
        assertEquals("Custom", FanProfiles.sanitize(profile(30f to 0f, 60f to 50f, name = "  "))!!.name)
        assertEquals(FanProfiles.MAX_NAME, FanProfiles.sanitize(profile(30f to 0f, 60f to 50f, name = "x".repeat(40)))!!.name.length)
    }
}
