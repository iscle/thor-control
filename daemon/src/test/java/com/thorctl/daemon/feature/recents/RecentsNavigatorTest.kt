package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.daemon.hardware.input.EventHandler
import com.thorctl.protocol.model.RecentsAction
import com.thorctl.protocol.model.RecentsAction.BACK
import com.thorctl.protocol.model.RecentsAction.CLOSE
import com.thorctl.protocol.model.RecentsAction.CLOSE_ALL
import com.thorctl.protocol.model.RecentsAction.HOME
import com.thorctl.protocol.model.RecentsAction.NEXT
import com.thorctl.protocol.model.RecentsAction.OPEN
import com.thorctl.protocol.model.RecentsAction.PREVIOUS
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentsNavigatorTest {
    private val clock = FakeClock()
    private val executor = FakeScheduledExecutor(clock)
    private val sent = mutableListOf<RecentsAction>()
    private val navigator = RecentsNavigator(RecentsThread(executor)) { sent += it }
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
        tap(EvdevCodes.BTN_START)
        tap(EvdevCodes.BTN_EAST)
        tap(EvdevCodes.KEY_BACK)
        tap(EvdevCodes.BTN_NORTH)
        tap(EvdevCodes.BTN_WEST)
        assertEquals(listOf(OPEN, OPEN, BACK, BACK, CLOSE, CLOSE_ALL), sent)
    }

    @Test
    fun eventsAreHandledOnTheRecentsThread() {
        handler(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        assertEquals(emptyList(), sent)
        executor.runDue()
        assertEquals(listOf(OPEN), sent)
    }

    @Test
    fun eventsStillOnTheirWayWhenRecentsClosesAreDropped() {
        handler(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        navigator.stop()
        executor.runDue()
        assertEquals(emptyList(), sent)
    }

    @Test
    fun anEndedGrabDoesNotReachTheNextSession() {
        val old = handler
        navigator.stop()
        handler = navigator.start(homeHeld = false)
        old(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        key(EvdevCodes.BTN_WEST, 1)
        assertEquals(listOf(CLOSE_ALL), sent)
    }

    @Test
    fun aHeldDirectionRepeatsAfterAPause() {
        key(EvdevCodes.BTN_DPAD_LEFT, 1)
        assertEquals(listOf(PREVIOUS), sent)
        executor.advanceBy(RecentsNavigator.REPEAT_DELAY_MS - 1)
        assertEquals(1, sent.size)
        executor.advanceBy(1)
        assertEquals(2, sent.size)
        executor.advanceBy(RecentsNavigator.REPEAT_EVERY_MS)
        assertEquals(listOf(PREVIOUS, PREVIOUS, PREVIOUS), sent)

        key(EvdevCodes.BTN_DPAD_LEFT, 0)
        executor.advanceBy(1000)
        assertEquals(3, sent.size)
        assertEquals(0, executor.pending)
    }

    @Test
    fun bumpersAndArrowKeysMoveToo() {
        tap(EvdevCodes.BTN_TL)
        tap(EvdevCodes.BTN_TR)
        tap(EvdevCodes.KEY_LEFT)
        tap(EvdevCodes.KEY_RIGHT)
        tap(EvdevCodes.BTN_DPAD_RIGHT)
        assertEquals(listOf(PREVIOUS, NEXT, PREVIOUS, NEXT, NEXT), sent)
    }

    @Test
    fun theHatMovesUntilCentred() {
        axis(EvdevCodes.ABS_HAT0X, 1)
        axis(EvdevCodes.ABS_HAT0X, 1)
        axis(EvdevCodes.ABS_HAT0X, 0)
        axis(EvdevCodes.ABS_HAT0X, -1)
        assertEquals(listOf(NEXT, PREVIOUS), sent)
    }

    @Test
    fun theStickNeedsAFirmPushAndLetsGoNearTheMiddle() {
        axis(EvdevCodes.ABS_X, 15000)
        assertEquals(emptyList(), sent)
        axis(EvdevCodes.ABS_X, RecentsNavigator.STICK_ON)
        axis(EvdevCodes.ABS_X, 10000) // still held
        executor.advanceBy(RecentsNavigator.REPEAT_DELAY_MS)
        assertEquals(listOf(NEXT, NEXT), sent)

        axis(EvdevCodes.ABS_X, RecentsNavigator.STICK_OFF - 1)
        executor.advanceBy(1000)
        assertEquals(2, sent.size)

        axis(EvdevCodes.ABS_X, -RecentsNavigator.STICK_ON)
        assertEquals(PREVIOUS, sent.last())
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
        assertEquals(emptyList(), sent)
        key(EvdevCodes.KEY_HOME, 1)
        key(EvdevCodes.KEY_HOME, 0)
        assertEquals(listOf(HOME), sent)
    }

    @Test
    fun aHomeReleaseWithoutPressIsIgnored() {
        key(EvdevCodes.KEY_HOME, 0)
        assertEquals(emptyList(), sent)
    }

    @Test
    fun stopEndsTheRepeat() {
        key(EvdevCodes.KEY_RIGHT, 1)
        navigator.stop()
        executor.advanceBy(2000)
        assertEquals(listOf(NEXT), sent)
        assertEquals(0, executor.pending)
    }
}
