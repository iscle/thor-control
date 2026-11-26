package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.daemon.system.RecentTaskEntry
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentsSessionTest {
    private val taskService = FakeTaskService()
    private val session = RecentsSession(taskService)
    private val directory = createTempDirectory("recents").toFile()

    private fun entry(taskId: Int, displayId: Int = -1, visible: Boolean = false) =
        RecentTaskEntry(taskId, "app$taskId", displayId, visible, running = displayId >= 0)

    private fun snapshot(taskId: Int): File = File(directory, "$taskId.jpg").apply { writeBytes(ByteArray(10)) }

    @Test
    fun opensWithTheAppYouCameFromFirst() {
        taskService.recents = listOf(entry(1), entry(2), entry(3, displayId = 0, visible = true))
        session.open(display = 0, origin = AppTask(3, "app3"))
        assertTrue(session.isOpen)
        assertEquals(0, session.openOn)
        assertEquals(listOf(3, 1, 2), session.tasks.map { it.taskId })
    }

    @Test
    fun listsAtMostSixteenApps() {
        taskService.recents = (1..20).map { entry(it) }
        session.open(display = 0, origin = null)
        assertEquals(RecentsSession.MAX_TASKS, session.tasks.size)
    }

    @Test
    fun asksForFreshPreviewsOfAppsVisibleElsewhere() {
        taskService.recents = listOf(entry(1, displayId = 0, visible = true), entry(2, displayId = 1, visible = true), entry(3))
        val request = session.open(display = 0, origin = AppTask(1, "app1"))
        assertEquals(listOf(2), request.taskIds)
        assertEquals(listOf("snapshot 2"), taskService.actions)
    }

    @Test
    fun theAppYouCameFromShowsItsFreshPictureInsteadOfAPreview() {
        taskService.recents = listOf(entry(1, displayId = 0, visible = true), entry(2))
        taskService.snapshots[1] = snapshot(1)
        taskService.snapshots[2] = snapshot(2)
        val origin = AppTask(1, "app1")
        session.open(display = 0, origin = origin)

        val withHero = session.cards(origin, heroCaptured = true)
        assertNull(withHero[0].thumb)
        assertNotNull(withHero[1].thumb)
        assertNotNull(session.cards(origin, heroCaptured = false)[0].thumb)
        assertEquals("app2", withHero[1].packageName)
    }

    @Test
    fun aPreviewCountsWhenWrittenAfterTheRequest() {
        val file = snapshot(4)
        taskService.snapshots[4] = file
        file.setLastModified(10_000)
        assertTrue(session.previewWrittenSince(4, since = 10_000 + RecentsSession.PREVIEW_SLACK_MS))
        assertFalse(session.previewWrittenSince(4, since = 10_000 + RecentsSession.PREVIEW_SLACK_MS + 1000))
        assertFalse(session.previewWrittenSince(5, since = 0))
    }

    @Test
    fun anAppOnTheOtherScreenIsBroughtOver() {
        taskService.recents = listOf(entry(1, displayId = 1), entry(2, displayId = 0), entry(3))
        session.open(display = 0, origin = null)
        session.launch(1, session.task(1), 0)
        session.launch(2, session.task(2), 0)
        session.launch(3, session.task(3), 0)
        session.launch(9, session.task(9), 0)
        assertEquals(listOf("move 1 -> 0", "start 2 on 0", "start 3 on 0", "start 9 on 0"), taskService.actions)
    }

    @Test
    fun removesAndClearsApps() {
        taskService.recents = listOf(entry(1), entry(2), entry(3))
        session.open(display = 0, origin = null)
        session.remove(2)
        assertEquals(listOf(1, 3), session.tasks.map { it.taskId })
        session.clear(emptyList())
        assertEquals(listOf("remove 2", "remove 1", "remove 3"), taskService.actions)
        assertTrue(session.tasks.isEmpty())
    }

    @Test
    fun clearsOnlyTheAppsNamed() {
        taskService.recents = listOf(entry(1), entry(2), entry(3))
        session.open(display = 0, origin = null)
        session.clear(listOf(3))
        assertEquals(listOf("remove 3"), taskService.actions)
    }

    @Test
    fun opensAndLaunchesEvenWhenTheTaskManagerFails() {
        val fake = FakeTaskService().apply { recents = listOf(entry(1, displayId = 1, visible = true), entry(2)) }
        val session = RecentsSession(UnavailableTaskService(fake))
        val request = session.open(display = 0, origin = null)
        assertEquals(listOf(1), request.taskIds)
        assertTrue(session.isOpen)
        session.launch(1, session.task(1), 0)
        session.launch(2, session.task(2), 0)
        assertTrue(fake.actions.isEmpty())
    }

    @Test
    fun closesOnce() {
        assertFalse(session.close())
        taskService.recents = listOf(entry(1))
        session.open(display = 1, origin = null)
        assertTrue(session.close())
        assertFalse(session.isOpen)
        assertEquals(-1, session.openOn)
        assertTrue(session.tasks.isEmpty())
        assertFalse(session.close())
    }
}
