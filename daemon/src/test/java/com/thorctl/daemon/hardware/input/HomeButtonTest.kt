package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.hardware.input.HomeButton.Companion.LONG_HOME_MS
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeButtonTest {
    private val worker = FakeInputWorker()
    private val listener = RecordingListener()

    private fun home(remapped: Boolean = true) = HomeButton(remapped, worker, listener)

    @Test
    fun shortPressIsHomeOnRelease() {
        val home = home()
        home.onKey(1)
        home.onKey(2)
        home.onKey(1)
        home.onKey(0)
        home.onKey(0)
        worker.advance(LONG_HOME_MS)
        assertEquals(listOf("homeDown", "homeSingle"), listener.calls)
    }

    @Test
    fun withoutTheRemapOnlyTheLongPressIsOurs() {
        val home = home(remapped = false)
        home.onKey(1)
        home.onKey(0)
        home.onKey(1)
        worker.advance(LONG_HOME_MS)
        home.onKey(0)
        assertEquals(listOf("homeLong"), listener.calls)
    }

    @Test
    fun longPressFiresWhileHeldAndSkipsTheShortPress() {
        val home = home()
        home.onKey(1)
        worker.advance(LONG_HOME_MS - 1)
        assertEquals(listOf("homeDown"), listener.calls)
        worker.advance(1)
        assertEquals(listOf("homeDown", "homeLong"), listener.calls)
        home.onKey(0)
        assertEquals(listOf("homeDown", "homeLong"), listener.calls)
    }

    @Test
    fun releaseMissedDuringAGrabIsForgotten() {
        val home = home()
        home.onKey(1)
        home.resetIfReleased { true }
        home.resetIfReleased { false }
        worker.advance(LONG_HOME_MS)
        home.onKey(0)
        assertEquals(listOf("homeDown"), listener.calls)
    }
}
