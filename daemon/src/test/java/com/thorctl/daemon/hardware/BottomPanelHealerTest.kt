package com.thorctl.daemon.hardware

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.input.FakeTouchscreens
import com.thorctl.daemon.system.DisplayService
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.SystemServiceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomPanelHealerTest {
    private val sys = FakeSys().apply {
        this["$DRM/card0-DSI-1/enabled"] = "enabled"
        this["$DRM/card0-DSI-2/enabled"] = "disabled" // the bottom panel: stuck dark
        this["$DRM/card0-DP-1/enabled"] = "disabled"
    }
    private val clock = FakeClock()
    private val scheduler = FakeScheduledExecutor(clock)
    private val displayService = FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM))
    private val healer by lazy { BottomPanelHealer(sys, clock, displayService, scheduler) }
    private val bottom = ThorDisplays.BOTTOM.id

    private val powerCycles get() = displayService.powerRequests.count { it == bottom to DisplayController.STATE_OFF }

    @Test
    fun powerCyclesAPanelStuckDarkOnTheSecondCheck() {
        healer.heal(bottom, bottomForcedOff = false)
        assertEquals(0, powerCycles)
        healer.heal(bottom, bottomForcedOff = false)
        assertEquals(listOf(bottom to DisplayController.STATE_OFF), displayService.powerRequests)

        // The panel's power is handed back half a second later, without blocking the caller.
        scheduler.advanceBy(499)
        assertEquals(1, displayService.powerRequests.size)
        scheduler.advanceBy(1)
        assertEquals(
            listOf(bottom to DisplayController.STATE_OFF, bottom to DisplayController.STATE_UNKNOWN),
            displayService.powerRequests,
        )
    }

    @Test
    fun noHandBackWhenTurningThePanelOffFails() {
        val failing = object : DisplayService by displayService {
            override fun requestDisplayPower(displayId: Int, state: Int): Boolean = throw SystemServiceException("binder died")
        }
        val healer = BottomPanelHealer(sys, clock, failing, scheduler)
        repeat(2) { healer.heal(bottom, bottomForcedOff = false) }
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun leavesAWorkingPanelAlone() {
        sys["$DRM/card0-DSI-2/enabled"] = "enabled"
        repeat(4) { healer.heal(bottom, bottomForcedOff = false) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun leavesAPanelTheFrameworkTurnedOffAlone() {
        displayService.displays = listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM.copy(state = DisplayController.STATE_OFF))
        repeat(4) { healer.heal(bottom, bottomForcedOff = false) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun leavesAPanelWeHoldOffOrDoNotKnowAlone() {
        repeat(4) { healer.heal(bottom, bottomForcedOff = true) }
        repeat(4) { healer.heal(-1, bottomForcedOff = false) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun aGoodCheckStartsTheCountOver() {
        healer.heal(bottom, bottomForcedOff = false)
        healer.heal(bottom, bottomForcedOff = true)
        healer.heal(bottom, bottomForcedOff = false)
        assertEquals(0, powerCycles)
    }

    @Test
    fun healsAtMostEvery15Seconds() {
        repeat(2) { healer.heal(bottom, bottomForcedOff = false) }
        clock.now += 14_999
        repeat(2) { healer.heal(bottom, bottomForcedOff = false) }
        assertEquals(1, powerCycles)
        clock.now += 1
        healer.heal(bottom, bottomForcedOff = false)
        assertEquals(2, powerCycles)
    }

    @Test
    fun healsAtMostThreeTimesPerWake() {
        repeat(5) {
            clock.now += 15_000
            repeat(2) { healer.heal(bottom, bottomForcedOff = false) }
        }
        assertEquals(3, powerCycles)

        healer.onWake()
        clock.now += 15_000
        repeat(2) { healer.heal(bottom, bottomForcedOff = false) }
        assertEquals(4, powerCycles)
    }

    @Test
    fun doesNothingWithoutTwoDsiPanels() {
        sys.files.remove("$DRM/card0-DSI-1/enabled")
        repeat(4) { healer.heal(bottom, bottomForcedOff = false) }
        assertTrue(displayService.powerRequests.isEmpty())
    }

    @Test
    fun healsThroughTheDisplayController() {
        val displays = DisplayController(displayService, FakeTouchscreens(), healer).apply { refresh() }
        repeat(2) { displays.healBottomPanel() }
        assertEquals(1, powerCycles)
    }

    private companion object {
        const val DRM = "/sys/class/drm"
    }
}
