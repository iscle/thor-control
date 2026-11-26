package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.daemon.hardware.input.EventHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentsNavigatorTest {
    private val clock = FakeClock()
    private val executor = FakeScheduledExecutor(clock)
    private val keys = mutableListOf<String>()
    private val navigator = RecentsNavigator(RecentsThread(executor)) { keys += it }
    private var handler: EventHandler = navigator.start(homeHeld = false)

    /** An event from the grab reader thread, then the recents thread catching up. */
    private fun key(code: Int, value: Int) {
        handler(EvdevCodes.EV_KEY, code, value)
        executor.runDue()
    }

    private fun axis(code: Int, value: Int) {
        handler(EvdevCodes.EV_ABS, code, value)
        executor.runDue()
    }

    private fun tap(code: Int) {
        key(code, 1)
        key(code, 2) // kernel autorepeat
        key(code, 0)
    }

    @Test
    fun buttonsSendTheirKeyOncePerPress() {
        tap(EvdevCodes.BTN_SOUTH)
        tap(RecentsNavigator.BTN_START)
        tap(EvdevCodes.BTN_EAST)
        tap(EvdevCodes.KEY_BACK)
        tap(EvdevCodes.BTN_NORTH)
        tap(EvdevCodes.BTN_WEST)
        assertEquals(listOf("a", "a", "b", "b", "x", "y"), keys)
    }

    @Test
    fun eventsAreHandledOnTheRecentsThread() {
        handler(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        assertEquals(emptyList(), keys)
        executor.runDue()
        assertEquals(listOf("a"), keys)
    }

    @Test
    fun eventsStillOnTheirWayWhenRecentsClosesAreDropped() {
        handler(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        navigator.stop()
        executor.runDue()
        assertEquals(emptyList(), keys)
    }

    @Test
    fun anEndedGrabDoesNotReachTheNextSession() {
        val old = handler
        navigator.stop()
        handler = navigator.start(homeHeld = false)
        old(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        key(EvdevCodes.BTN_WEST, 1)
        assertEquals(listOf("y"), keys)
    }

    @Test
    fun aHeldDirectionRepeatsAfterAPause() {
        key(EvdevCodes.BTN_DPAD_LEFT, 1)
        assertEquals(listOf("left"), keys)
        executor.advanceBy(RecentsNavigator.REPEAT_DELAY_MS - 1)
        assertEquals(1, keys.size)
        executor.advanceBy(1)
        assertEquals(2, keys.size)
        executor.advanceBy(RecentsNavigator.REPEAT_EVERY_MS)
        assertEquals(listOf("left", "left", "left"), keys)

        key(EvdevCodes.BTN_DPAD_LEFT, 0)
        executor.advanceBy(1000)
        assertEquals(3, keys.size)
        assertEquals(0, executor.pending)
    }

    @Test
    fun bumpersAndArrowKeysMoveToo() {
        tap(EvdevCodes.BTN_TL)
        tap(EvdevCodes.BTN_TR)
        tap(EvdevCodes.KEY_LEFT)
        tap(EvdevCodes.KEY_RIGHT)
        tap(EvdevCodes.BTN_DPAD_RIGHT)
        assertEquals(listOf("left", "right", "left", "right", "right"), keys)
    }

    @Test
    fun theHatMovesUntilCentred() {
        axis(EvdevCodes.ABS_HAT0X, 1)
        axis(EvdevCodes.ABS_HAT0X, 1)
        axis(EvdevCodes.ABS_HAT0X, 0)
        axis(EvdevCodes.ABS_HAT0X, -1)
        assertEquals(listOf("right", "left"), keys)
    }

    @Test
    fun theStickNeedsAFirmPushAndLetsGoNearTheMiddle() {
        axis(RecentsNavigator.ABS_X, 15000)
        assertEquals(emptyList(), keys)
        axis(RecentsNavigator.ABS_X, RecentsNavigator.STICK_ON)
        axis(RecentsNavigator.ABS_X, 10000) // still held
        executor.advanceBy(RecentsNavigator.REPEAT_DELAY_MS)
        assertEquals(listOf("right", "right"), keys)

        axis(RecentsNavigator.ABS_X, RecentsNavigator.STICK_OFF - 1)
        executor.advanceBy(1000)
        assertEquals(2, keys.size)

        axis(RecentsNavigator.ABS_X, -RecentsNavigator.STICK_ON)
        assertEquals("left", keys.last())
    }

    @Test
    fun stickDirectionHasHysteresis() {
        assertEquals(1, RecentsNavigator.stickDirection(20000, 0))
        assertEquals(-1, RecentsNavigator.stickDirection(-20000, 0))
        assertEquals(0, RecentsNavigator.stickDirection(19999, 0))
        assertEquals(1, RecentsNavigator.stickDirection(8000, 1))
        assertEquals(-1, RecentsNavigator.stickDirection(-8000, -1))
        assertEquals(0, RecentsNavigator.stickDirection(7999, 1))
        assertEquals(0, RecentsNavigator.stickDirection(-7999, -1))
    }

    @Test
    fun theHomeHoldThatOpenedRecentsDoesNotGoHome() {
        navigator.stop()
        handler = navigator.start(homeHeld = true)
        key(EvdevCodes.KEY_HOME, 0)
        assertEquals(emptyList(), keys)
        key(EvdevCodes.KEY_HOME, 1)
        key(EvdevCodes.KEY_HOME, 0)
        assertEquals(listOf("home"), keys)
    }

    @Test
    fun aHomeReleaseWithoutPressIsIgnored() {
        key(EvdevCodes.KEY_HOME, 0)
        assertEquals(emptyList(), keys)
    }

    @Test
    fun stopEndsTheRepeat() {
        key(EvdevCodes.KEY_RIGHT, 1)
        navigator.stop()
        executor.advanceBy(2000)
        assertEquals(listOf("right"), keys)
        assertEquals(0, executor.pending)
    }
}
