package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.awaitUntil
import com.thorctl.protocol.GamepadHid
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsbHidEndpointTest {
    private val node = FakeHidgNode()
    private val endpoint = UsbHidEndpoint(node)

    private val current = report(1)

    private fun report(id: Int) = ByteArray(GamepadHid.PAYLOAD).also { it[0] = id.toByte() }

    /** A report as it goes out: the report ID, then the payload. */
    private fun frame(report: ByteArray) = byteArrayOf(GamepadHid.REPORT_ID.toByte()) + report

    @After
    fun stop() {
        endpoint.stop()
        endpoint.join()
    }

    @Test
    fun aFreshHostGetsTheCurrentStateThenEachSubmittedReport() {
        endpoint.start { current }
        awaitUntil("the current state") { node.written.size == 1 }
        assertContentEquals(frame(current), node.written[0])
        endpoint.submit(report(2))
        awaitUntil("the submitted report") { node.written.size == 2 }
        assertContentEquals(frame(report(2)), node.written[1])
    }

    @Test
    fun aReportSubmittedBeforeTheStartFollowsTheCurrentState() {
        endpoint.submit(report(2))
        endpoint.start { current }
        awaitUntil("both reports") { node.written.size == 2 }
        assertContentEquals(frame(current), node.written[0])
        assertContentEquals(frame(report(2)), node.written[1])
    }

    @Test
    fun noCurrentStateYetSendsNothingFirst() {
        endpoint.start { ByteArray(0) }
        awaitUntil("the node open") { node.opens.get() == 1 }
        endpoint.submit(report(2))
        awaitUntil("the submitted report") { node.written.size == 1 }
        assertContentEquals(frame(report(2)), node.written[0])
    }

    @Test
    fun aNodeNotThereYetIsOpenedAgain() {
        node.failOpens = 2
        endpoint.start { current }
        awaitUntil("the current state") { node.written.size == 1 }
        assertEquals(1, node.opens.get())
    }

    @Test
    fun aFailedWriteIsSentAgainOnAFreshOpen() {
        node.failWrites = 1
        endpoint.start { current }
        // The fresh open sends the current state, then the report that failed (the same one here).
        awaitUntil("the retried reports") { node.written.size == 2 }
        assertEquals(2, node.opens.get())
        assertContentEquals(frame(current), node.written[0])
        assertContentEquals(frame(current), node.written[1])
    }

    @Test
    fun stopWakesTheWriterAndJoinReturnsOnceTheNodeIsClosed() {
        endpoint.start { current }
        awaitUntil("the writer waiting for a report") { node.written.size == 1 && node.openNow == 2 }
        val stoppedAt = System.currentTimeMillis()
        endpoint.stop()
        assertTrue(endpoint.join())
        val took = System.currentTimeMillis() - stoppedAt
        assertEquals(0, node.openNow)
        // The writer waits up to a second for a report; a stop that didn't wake it would take that long.
        assertTrue(took < 500, "join took ${took}ms")
    }

    @Test
    fun joinWithNothingRunningIsImmediate() {
        assertTrue(endpoint.join())
    }

    @Test
    fun startingTwiceStartsOneWriter() {
        endpoint.start { current }
        endpoint.start { current }
        awaitUntil("the current state") { node.written.size == 1 }
        Thread.sleep(50)
        assertEquals(1, node.opens.get())
    }
}
