package com.thorctl.daemon.system

import android.app.ActivityOptions
import android.app.TaskInfo
import android.content.ComponentName
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import timber.log.Timber
import java.io.File

/** [TaskService] over IActivityTaskManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiTaskService : TaskService {
    private val service = SystemServiceProxy(HiddenServiceNames.ACTIVITY_TASK_SERVICE, "android.app.IActivityTaskManager")

    override fun focusedDisplayId(): Int = try {
        service.getFocusedRootTaskInfo()?.displayIdOrNone ?: -1
    } catch (_: ReflectiveOperationException) {
        -1
    }

    override fun focusTopTask(displayId: Int) {
        service.focusTopTask(displayId)
    }

    override fun topAppTask(displayId: Int): AppTask? =
        service.getAllRootTaskInfosOnDisplay(displayId)
            .firstOrNull { it.topActivityTypeOrNull == ACTIVITY_TYPE_STANDARD && it.isVisibleRootTask }
            ?.let { AppTask(it.taskId, it.topComponent()?.packageName) }

    override fun visiblePackages(displayId: Int): Set<String> =
        service.getAllRootTaskInfosOnDisplay(displayId)
            .filter { it.isVisibleRootTask }
            .mapNotNullTo(HashSet()) { it.topComponent()?.packageName }

    override fun moveTaskToDisplay(taskId: Int, displayId: Int) {
        service.moveRootTaskToDisplay(taskId, displayId)
        // A raw reparent skips the "task to front" transition that Launcher tracks; without it
        // Launcher believes it is still visible and the next app-switch toggles the wrong state.
        try {
            startFromRecents(taskId, displayId)
        } catch (e: ReflectiveOperationException) {
            Timber.w(e, "bring task $taskId to front failed")
        }
    }

    override fun recentTasks(limit: Int): List<RecentTaskEntry> =
        service.getRecentTasks(limit, RECENT_IGNORE_UNAVAILABLE, USER_SYSTEM).mapNotNull { info ->
            val packageName = info.topComponent()?.packageName ?: info.baseIntent?.component?.packageName ?: return@mapNotNull null
            RecentTaskEntry(
                taskId = info.taskId,
                packageName = packageName,
                displayId = info.displayIdOrNone,
                visible = info.isVisible,
                running = info.isRunning,
            )
        }

    override fun startFromRecents(taskId: Int, displayId: Int) {
        val options = ActivityOptions.makeBasic()
        if (displayId >= 0) options.launchDisplayId = displayId
        service.startActivityFromRecents(taskId, options.toBundle())
    }

    override fun removeTask(taskId: Int): Boolean = service.removeTask(taskId)

    override fun snapshotFile(taskId: Int): File? {
        val root = File(SNAPSHOT_DIRECTORY)
        val directories = listOf(root) + (root.listFiles()?.filter { it.isDirectory } ?: emptyList())
        for (name in listOf("${taskId}_reduced.jpg", "$taskId.jpg")) {
            directories.map { File(it, name) }.filter { it.isFile }.maxByOrNull { it.lastModified() }?.let { return it }
        }
        return null
    }

    private fun TaskInfo.topComponent(): ComponentName? = topActivity ?: baseActivity

    private companion object {
        const val ACTIVITY_TYPE_STANDARD = 1
        const val RECENT_IGNORE_UNAVAILABLE = 0x0002
        const val USER_SYSTEM = 0
        const val SNAPSHOT_DIRECTORY = "/data/system_ce/0/snapshots"
    }
}
