package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.hardware.input.EvdevCodes
import com.thorctl.protocol.GamepadHid
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControllerReportsTest {
    private val sent: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
    private val reports = ControllerReports { sent += it }

    private fun press(code: Int, down: Boolean = true) {
        reports.onEvent(EvdevCodes.EV_KEY, code, if (down) 1 else 0)
        reports.onEvent(EvdevCodes.EV_SYN, EvdevCodes.SYN_REPORT, 0)
    }

    @Test
    fun nothingIsSentBeforeTheFirstFrame() {
        assertEquals(0, reports.last.size)
        reports.onEvent(EvdevCodes.EV_KEY, EvdevCodes.BTN_SOUTH, 1)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aFrameIsSentOnlyWhenItChangesSomething() {
        reports.restart(emptySet(), IntArray(EvdevCodes.ABS_CNT))
        assertContentEquals(GamepadHid.neutral(), sent.single())
        reports.onEvent(EvdevCodes.EV_SYN, EvdevCodes.SYN_REPORT, 0)
        assertEquals(1, sent.size)
        press(EvdevCodes.BTN_SOUTH)
        assertEquals(2, sent.size)
        assertEquals(GamepadHid.B2_A, sent.last()[2].toInt())
        assertContentEquals(sent.last(), reports.last)
        press(EvdevCodes.BTN_SOUTH, down = false)
        assertContentEquals(GamepadHid.neutral(), sent.last())
    }

    @Test
    fun restartAlwaysSendsWhatIsHeldNow() {
        reports.restart(emptySet(), IntArray(EvdevCodes.ABS_CNT))
        reports.restart(emptySet(), IntArray(EvdevCodes.ABS_CNT))
        assertEquals(2, sent.size)
        reports.restart(setOf(EvdevCodes.BTN_EAST), IntArray(EvdevCodes.ABS_CNT))
        assertEquals(GamepadHid.B2_B, sent.last()[2].toInt())
    }

    @Test
    fun sendGoesOutAsItIsAndBecomesTheLast() {
        press(EvdevCodes.BTN_SOUTH)
        reports.send(GamepadHid.neutral())
        assertContentEquals(GamepadHid.neutral(), sent.last())
        assertContentEquals(GamepadHid.neutral(), reports.last)
        // The encoder still has A held: the next frame says so again.
        reports.onEvent(EvdevCodes.EV_SYN, EvdevCodes.SYN_REPORT, 0)
        assertEquals(GamepadHid.B2_A, sent.last()[2].toInt())
    }

    @Test
    fun grabThreadEventsAndRestartsNeverInterleave() {
        // Anything a worker thread throws (a ConcurrentModificationException, above all) is collected
        // here, since an exception on another thread would not fail the test.
        val failures: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
        val buttons = listOf(EvdevCodes.BTN_SOUTH, EvdevCodes.BTN_EAST, EvdevCodes.BTN_NORTH, EvdevCodes.BTN_WEST, EvdevCodes.BTN_TL, EvdevCodes.BTN_TR)
        val grabThread = thread {
            try {
                repeat(20_000) { i ->
                    reports.onEvent(EvdevCodes.EV_KEY, buttons[i % buttons.size], (i / buttons.size) % 2)
                    reports.onEvent(EvdevCodes.EV_ABS, EvdevCodes.ABS_Z, i % 256)
                    reports.onEvent(EvdevCodes.EV_SYN, EvdevCodes.SYN_REPORT, 0)
                }
            } catch (e: Throwable) {
                failures += e
            }
        }
        val coordinator = thread {
            try {
                repeat(2_000) { i -> reports.restart(buttons.take(i % buttons.size).toSet(), IntArray(EvdevCodes.ABS_CNT)) }
            } catch (e: Throwable) {
                failures += e
            }
        }
        grabThread.join()
        coordinator.join()
        assertEquals(emptyList(), failures)
        assertTrue(sent.all { it.size == GamepadHid.PAYLOAD })
        assertContentEquals(sent.last(), reports.last)
    }
}
