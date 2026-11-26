package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import com.thorctl.daemon.hardware.InputWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentsNavigatorTest {
    private val clock = FakeClock()
    private val executor = FakeScheduledExecutor(clock)
    private val keys = mutableListOf<String>()
    private val navigator = RecentsNavigator(RecentsThread(executor)) { keys += it }.apply { start(homeHeld = false) }

    private fun key(code: Int, value: Int) = navigator.onEvent(InputWatcher.EV_KEY, code, value)

    private fun axis(code: Int, value: Int) = navigator.onEvent(InputWatcher.EV_ABS, code, value)

    private fun tap(code: Int) {
        key(code, 1)
        key(code, 2) // kernel autorepeat
        key(code, 0)
    }

    @Test
    fun buttonsSendTheirKeyOncePerPress() {
        tap(RecentsNavigator.BTN_SOUTH)
        tap(RecentsNavigator.BTN_START)
        tap(InputWatcher.BTN_EAST)
        tap(RecentsNavigator.KEY_BACK)
        tap(InputWatcher.BTN_NORTH)
        tap(InputWatcher.BTN_WEST)
        assertEquals(listOf("a", "a", "b", "b", "x", "y"), keys)
    }

    @Test
    fun aHeldDirectionRepeatsAfterAPause() {
        key(InputWatcher.BTN_DPAD_LEFT, 1)
        assertEquals(listOf("left"), keys)
        executor.advanceBy(RecentsNavigator.REPEAT_DELAY_MS - 1)
        assertEquals(1, keys.size)
        executor.advanceBy(1)
        assertEquals(2, keys.size)
        executor.advanceBy(RecentsNavigator.REPEAT_EVERY_MS)
        assertEquals(listOf("left", "left", "left"), keys)

        key(InputWatcher.BTN_DPAD_LEFT, 0)
        executor.advanceBy(1000)
        assertEquals(3, keys.size)
        assertEquals(0, executor.pending)
    }

    @Test
    fun bumpersAndArrowKeysMoveToo() {
        tap(RecentsNavigator.BTN_TL)
        tap(RecentsNavigator.BTN_TR)
        tap(InputWatcher.KEY_LEFT)
        tap(InputWatcher.KEY_RIGHT)
        tap(InputWatcher.BTN_DPAD_RIGHT)
        assertEquals(listOf("left", "right", "left", "right", "right"), keys)
    }

    @Test
    fun theHatMovesUntilCentred() {
        axis(InputWatcher.ABS_HAT0X, 1)
        axis(InputWatcher.ABS_HAT0X, 1)
        axis(InputWatcher.ABS_HAT0X, 0)
        axis(InputWatcher.ABS_HAT0X, -1)
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
        navigator.start(homeHeld = true)
        key(InputWatcher.KEY_HOME, 0)
        assertEquals(emptyList(), keys)
        key(InputWatcher.KEY_HOME, 1)
        key(InputWatcher.KEY_HOME, 0)
        assertEquals(listOf("home"), keys)
    }

    @Test
    fun aHomeReleaseWithoutPressIsIgnored() {
        key(InputWatcher.KEY_HOME, 0)
        assertEquals(emptyList(), keys)
    }

    @Test
    fun stopEndsTheRepeat() {
        key(InputWatcher.KEY_RIGHT, 1)
        navigator.stop()
        executor.advanceBy(2000)
        assertEquals(listOf("right"), keys)
        assertEquals(0, executor.pending)
    }
}
