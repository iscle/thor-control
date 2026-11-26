package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.RecentTaskEntry
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.model.Jpeg
import com.thorctl.protocol.model.RecentTask
import dev.zacsweers.metro.Inject
import timber.log.Timber

/**
 * One showing of the recents screen: the display it is open on and the apps it lists (the
 * system's recents list, the app you came from first), and what can be done to them through
 * [TaskService]. Used on the recents thread; [openOn] may be read from any thread.
 *
 * Only running tasks are opened from here. Restoring a task that is no longer running counts as
 * a background activity start for this (root, windowless) process and Android blocks it; the app
 * launches those itself (it is a persistent process with a visible window, which is allowed).
 */
@Inject
class RecentsSession(private val taskService: TaskService) {
    /** Display the recents screen is open on, or -1. */
    @Volatile
    var openOn = -1
        private set

    val isOpen: Boolean get() = openOn >= 0

    var tasks: List<RecentTaskEntry> = emptyList()
        private set

    /**
     * Opens on [display], coming from [origin] (null: the home screen): lists the recent apps and
     * asks for fresh previews of the ones visible elsewhere. Returns that request.
     */
    fun open(display: Int, origin: AppTask?): PreviewRequest {
        val list = taskService.recentTasks(MAX_TASKS).toMutableList()
        // The app you came from is "the most recent" one, right next to the home screen.
        if (origin != null) {
            val index = list.indexOfFirst { it.taskId == origin.taskId }
            if (index > 0) list.add(0, list.removeAt(index))
        }
        // Apps visible elsewhere (the other screen) get a fresh preview; written within ~150 ms.
        val requested = System.currentTimeMillis()
        val others = list.filter { it.visible && it.taskId != origin?.taskId }
        others.forEach {
            try {
                taskService.takeSnapshot(it.taskId)
            } catch (e: Throwable) {
                Timber.w(e, "snapshot of task ${it.taskId} failed")
            }
        }
        tasks = list
        openOn = display
        return PreviewRequest(others.map { it.taskId }, requested)
    }

    /** False if it was not open. */
    fun close(): Boolean {
        if (!isOpen) return false
        openOn = -1
        tasks = emptyList()
        return true
    }

    /** The cards to show; the app you came from has no preview when the app gets a fresh picture of it ([heroCaptured]). */
    fun cards(origin: AppTask?, heroCaptured: Boolean): List<RecentTask> = tasks.map { task ->
        val thumb = if (task.taskId == origin?.taskId && heroCaptured) null else thumb(task.taskId)
        RecentTask(task.taskId, task.packageName, task.displayId, task.visible, task.running, thumb)
    }

    /** The system's persisted preview of a task. */
    fun thumb(taskId: Int): Jpeg? = JpegFiles.read(taskService.snapshotFile(taskId))

    /** The system wrote the preview of [taskId] after [since] (wall-clock, allowing a little slack). */
    fun previewWrittenSince(taskId: Int, since: Long): Boolean {
        val snapshot = taskService.snapshotFile(taskId)
        return snapshot != null && snapshot.lastModified() >= since - PREVIEW_SLACK_MS
    }

    /** The listed entry of task [taskId], if any. */
    fun task(taskId: Int): RecentTaskEntry? = tasks.firstOrNull { it.taskId == taskId }

    /** Brings task [taskId] (listed as [task]) to front on [display]. */
    fun launch(taskId: Int, task: RecentTaskEntry?, display: Int) {
        try {
            if (task != null && task.displayId >= 0 && task.displayId != display) {
                // Running on the other screen: bring it over to where the user is looking.
                taskService.moveTaskToDisplay(taskId, display)
            } else {
                taskService.startFromRecents(taskId, display)
            }
            Timber.i("recents: opened task $taskId (${task?.packageName}) on display $display")
        } catch (e: Throwable) {
            Timber.w(e, "recents: open task $taskId failed")
        }
    }

    /** Closes the app of task [taskId], like swiping it away. */
    fun remove(taskId: Int) {
        taskService.removeTask(taskId)
        tasks = tasks.filter { it.taskId != taskId }
        Timber.i("recents: closed task $taskId")
    }

    /** Closes the apps of [taskIds]; with none given (the debug CLI), everything listed. */
    fun clear(taskIds: List<Int>) {
        val ids = taskIds.ifEmpty { tasks.map { it.taskId } }
        ids.forEach { taskService.removeTask(it) }
        tasks = emptyList()
        Timber.i("recents: closed all (${ids.size})")
    }

    companion object {
        const val MAX_TASKS = 16
        const val PREVIEW_SLACK_MS = 50L
    }
}
