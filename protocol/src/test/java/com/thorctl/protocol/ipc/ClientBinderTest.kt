package com.thorctl.protocol.ipc

import android.os.ParcelFileDescriptor
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.protocol.model.HudEvent
import com.thorctl.protocol.model.Jpeg
import com.thorctl.protocol.model.RecentTask
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The daemon's [ClientEventSender] calling straight into the app's [ClientBinder]. */
class ClientBinderTest {
    private class TestJpeg(val name: String) : Jpeg {
        override fun open() = ByteArrayInputStream(name.toByteArray())

        override fun close() {}
    }

    /** Stands in for the pipes: every image becomes a descriptor and back into the same image. */
    private class Pipes : JpegExporter, JpegImporter {
        private val sent = HashMap<ParcelFileDescriptor, Jpeg>()

        override fun export(jpeg: Jpeg?): ParcelFileDescriptor? = jpeg?.let { j -> ParcelFileDescriptor(null).also { sent[it] = j } }

        override fun import(pfd: ParcelFileDescriptor?): Jpeg? = pfd?.let { sent.getValue(it) }
    }

    private val received = mutableListOf<DaemonEvent>()
    private val pipes = Pipes()
    private val sender = ClientEventSender(ClientBinder(received::add, pipes), pipes)

    private fun roundTrip(e: DaemonEvent): DaemonEvent {
        received.clear()
        sender.send(e)
        return received.single()
    }

    @Test
    fun eventsWithoutImagesArriveAsSent() {
        val events = listOf(
            DaemonEvent.StateChanged(Samples.state),
            DaemonEvent.StatsUpdated(Samples.stats),
            DaemonEvent.MenuButton,
            DaemonEvent.Haptic,
            DaemonEvent.Hud(HudEvent.Controller(ControllerTarget.BOTTOM), 2),
            DaemonEvent.RecentsKey("a"),
            DaemonEvent.RecentsClosed,
            DaemonEvent.KeyboardKey("shift"),
        )
        events.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun recentsCarryTheirImages() {
        val hero = TestJpeg("hero")
        val thumb = TestJpeg("thumb")
        val e = DaemonEvent.RecentsOpened(
            displayId = 1, fromHome = false, originTask = 5, hero = hero, home = null,
            tasks = listOf(RecentTask(5, "com.a", 1, true, true, null), RecentTask(6, "com.b", -1, false, false, thumb)),
        )
        val got = assertIs<DaemonEvent.RecentsOpened>(roundTrip(e))
        assertSame(hero, got.hero)
        assertNull(got.home)
        assertEquals(listOf(null, thumb), got.tasks.map { it.thumb })
        assertEquals(e.tasks.map { it.copy(thumb = null) }, got.tasks.map { it.copy(thumb = null) })
    }

    @Test
    fun homeAnimationAndThumbnails() {
        val app = TestJpeg("app")
        val home = TestJpeg("home")
        assertEquals(DaemonEvent.HomeAnimation(3, 0, app, home), roundTrip(DaemonEvent.HomeAnimation(3, 0, app, home)))
        assertEquals(DaemonEvent.RecentsThumb(9, app), roundTrip(DaemonEvent.RecentsThumb(9, app)))
    }

    @Test
    fun gamepadReportsAreRawBytes() {
        val r = byteArrayOf(8, 0, 0x40, -128, -128, -128, -128, 0, 0, 0)
        assertContentEquals(r, assertIs<DaemonEvent.GamepadReport>(roundTrip(DaemonEvent.GamepadReport(r))).report)
    }
}
