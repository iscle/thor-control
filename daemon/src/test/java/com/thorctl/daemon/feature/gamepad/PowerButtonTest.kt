package com.thorctl.daemon.feature.gamepad

import java.util.concurrent.FutureTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerButtonTest {
    private val events = mutableListOf<String>()
    private val timers = mutableListOf<Pair<Long, FutureTask<Unit>>>()

    private val button = PowerButton(
        schedule = { delayMs, action -> FutureTask { action() }.also { timers += delayMs to it } },
        onPress = { events += "press" },
        onHold = { events += "hold" },
    )

    /** The hold timer fires (a cancelled one does nothing, as on a real executor). */
    private fun fireTimers() = timers.forEach { it.second.run() }

    @Test
    fun aQuickReleaseIsAPress() {
        button.onKey(true)
        assertEquals(listOf(PowerButton.LONG_PRESS_MS), timers.map { it.first })
        button.onKey(false)
        assertEquals(listOf("press"), events)
        assertTrue(timers.single().second.isCancelled)
        fireTimers()
        assertEquals(listOf("press"), events)
    }

    @Test
    fun holdingFiresTheHoldAndTheReleaseDoesNothing() {
        button.onKey(true)
        fireTimers()
        assertEquals(listOf("hold"), events)
        button.onKey(false)
        assertEquals(listOf("hold"), events)
    }

    @Test
    fun aNewPressAfterAHoldIsAPressAgain() {
        button.onKey(true)
        fireTimers()
        button.onKey(false)
        timers.clear()
        button.onKey(true)
        button.onKey(false)
        assertEquals(listOf("hold", "press"), events)
    }

    @Test
    fun aRepeatedDownRestartsTheHoldTimer() {
        button.onKey(true)
        button.onKey(true)
        assertEquals(2, timers.size)
        assertTrue(timers[0].second.isCancelled)
        fireTimers()
        assertEquals(listOf("hold"), events)
    }

    @Test
    fun cancelStopsAPendingHoldAndSpendsThePress() {
        button.onKey(true)
        button.cancel()
        assertTrue(timers.single().second.isCancelled)
        fireTimers()
        button.onKey(false)
        assertEquals(emptyList(), events)
    }

    @Test
    fun aHoldTimerAlreadyRunningWhenCancelledDoesNothing() {
        button.onKey(true)
        // The timer started but waits for the owner's lock while the owner cancels.
        val lateAction = timers.single().second
        button.cancel()
        lateAction.run() // FutureTask.cancel(false) already marked it; run() is a no-op either way
        assertEquals(emptyList(), events)
    }

    @Test
    fun aHoldTimerRunningLateAfterTheReleaseDoesNothing() {
        val actions = mutableListOf<() -> Unit>()
        val lateButton = PowerButton(
            schedule = { _, action -> FutureTask { }.also { actions += action } },
            onPress = { events += "press" },
            onHold = { events += "hold" },
        )
        lateButton.onKey(true)
        lateButton.onKey(false)
        // The executor had already started the hold when the release cancelled its future.
        actions.single().invoke()
        assertEquals(listOf("press"), events)
    }

    @Test
    fun cancelWithTheButtonUpLeavesTheNextPressAlone() {
        button.cancel()
        button.onKey(true)
        button.onKey(false)
        assertEquals(listOf("press"), events)
    }

    @Test
    fun aReleaseWithoutADownIsAPress() {
        button.onKey(false)
        assertEquals(listOf("press"), events)
    }
}
