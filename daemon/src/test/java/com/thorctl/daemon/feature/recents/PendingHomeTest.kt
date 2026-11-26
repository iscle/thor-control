package com.thorctl.daemon.feature.recents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingHomeTest {
    private val pendingHome = PendingHome()

    @Test
    fun goesHomeOncePerAnimation() {
        val sequence = pendingHome.start(display = 1)
        assertEquals(1, pendingHome.take(sequence))
        assertNull(pendingHome.take(sequence))
    }

    @Test
    fun onlyTheLatestAnimationGoesHome() {
        val first = pendingHome.start(display = 0)
        val second = pendingHome.start(display = 1)
        assertEquals(first + 1, second)
        assertNull(pendingHome.take(first))
        assertEquals(1, pendingHome.take(second))
    }

    @Test
    fun nothingPendingAtFirst() {
        assertNull(pendingHome.take(0))
        assertNull(pendingHome.take(1))
    }
}
