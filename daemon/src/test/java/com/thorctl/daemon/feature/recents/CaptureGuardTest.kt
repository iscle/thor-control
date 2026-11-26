package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureGuardTest {
    private val clock = FakeClock()
    private val guard = CaptureGuard(clock)

    @Test
    fun nothingCoversTheScreenAtFirst() {
        assertFalse(guard.overlayShowing)
        assertFalse(guard.hudShowing)
    }

    @Test
    fun anOverlayCoversTheScreenForItsDuration() {
        guard.overlayFor(2500)
        clock.now += 2499
        assertTrue(guard.overlayShowing)
        clock.now += 1
        assertFalse(guard.overlayShowing)
    }

    @Test
    fun theAppEndsTheOverlayEarly() {
        guard.overlayFor(4000)
        guard.overlayDone()
        assertFalse(guard.overlayShowing)
    }

    @Test
    fun theHudCountsForThreeSeconds() {
        guard.noteHud()
        clock.now += CaptureGuard.HUD_MS - 1
        assertTrue(guard.hudShowing)
        clock.now += 1
        assertFalse(guard.hudShowing)
    }
}
