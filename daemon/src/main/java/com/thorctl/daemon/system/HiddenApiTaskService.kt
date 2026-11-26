package com.thorctl.daemon.system

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
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
    private val service = SystemServiceProxy("activity_task", "android.app.IActivityTaskManager")

    override fun focusedDisplayId(): Int = try {
        service.call("getFocusedRootTaskInfo")?.hiddenField("displayId") as? Int ?: -1
    } catch (_: Throwable) {
        -1
    }

    override fun focusTopTask(displayId: Int) {
        service.call("focusTopTask", displayId)
    }

    override fun topAppTask(displayId: Int): AppTask? =
        rootTasks(displayId)
            .firstOrNull { it.isVisible() && it.hiddenField("topActivityType") == ACTIVITY_TYPE_STANDARD }
            ?.let { AppTask(it.hiddenField("taskId") as Int, it.topComponent()?.packageName) }

    override fun visiblePackages(displayId: Int): Set<String> =
        rootTasks(displayId).filter { it.isVisible() }.mapNotNullTo(HashSet()) { it.topComponent()?.packageName }

    override fun moveTaskToDisplay(taskId: Int, displayId: Int) {
        service.call("moveRootTaskToDisplay", taskId, displayId)
        // A raw reparent skips the "task to front" transition that Launcher tracks; without it
        // Launcher believes it is still visible and the next app-switch toggles the wrong state.
        try {
            startFromRecents(taskId, displayId)
        } catch (e: Throwable) {
            Timber.w(e, "bring task $taskId to front failed")
        }
    }

    override fun recentTasks(limit: Int): List<RecentTaskEntry> {
        val slice = service.call("getRecentTasks", limit, RECENT_IGNORE_UNAVAILABLE, 0) ?: return emptyList()
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return emptyList()
        return list.filterNotNull().mapNotNull { info ->
            val packageName = info.topComponent()?.packageName
                ?: (info.hiddenField("baseIntent") as? Intent)?.component?.packageName
                ?: return@mapNotNull null
            RecentTaskEntry(
                taskId = info.hiddenField("taskId") as? Int ?: return@mapNotNull null,
                packageName = packageName,
                displayId = info.hiddenField("displayId") as? Int ?: -1,
                visible = info.hiddenField("isVisible") as? Boolean ?: false,
                running = info.hiddenField("isRunning") as? Boolean ?: false,
            )
        }
    }

    override fun startFromRecents(taskId: Int, displayId: Int) {
        val options = ActivityOptions.makeBasic()
        if (displayId >= 0) options.launchDisplayId = displayId
        service.call("startActivityFromRecents", taskId, options.toBundle())
    }

    override fun removeTask(taskId: Int): Boolean = service.call("removeTask", taskId) as? Boolean ?: false

    override fun takeSnapshot(taskId: Int) {
        val snapshotManager = service.call("getTaskSnapshotManager") ?: return
        val takeMethod = snapshotManager.javaClass.methods.firstOrNull { it.name == "takeTaskSnapshot" } ?: return
        // updateCache = true writes it to disk; the extra flags vary by release.
        val arguments: Array<Any?> = when (takeMethod.parameterTypes.size) {
            4 -> arrayOf(taskId, true, false, false)
            3 -> arrayOf(taskId, true, false)
            2 -> arrayOf(taskId, true)
            else -> return
        }
        val snapshot = takeMethod.invoke(snapshotManager, *arguments) ?: return
        // Only the file is wanted: release the snapshot's graphics buffer right away.
        try {
            (snapshot.javaClass.getMethod("getHardwareBuffer").invoke(snapshot) as? AutoCloseable)?.close()
        } catch (_: Throwable) {
        }
    }

    override fun snapshotFile(taskId: Int): File? {
        val root = File(SNAPSHOT_DIRECTORY)
        val directories = listOf(root) + (root.listFiles()?.filter { it.isDirectory } ?: emptyList())
        for (name in listOf("${taskId}_reduced.jpg", "$taskId.jpg")) {
            directories.map { File(it, name) }.filter { it.isFile }.maxByOrNull { it.lastModified() }?.let { return it }
        }
        return null
    }

    private fun rootTasks(displayId: Int): List<Any> =
        (service.call("getAllRootTaskInfosOnDisplay", displayId) as? List<*>)?.filterNotNull() ?: emptyList()

    private fun Any.isVisible(): Boolean =
        hiddenField("visible") as? Boolean ?: false || hiddenField("isVisible") as? Boolean ?: false

    private fun Any.topComponent(): ComponentName? =
        (hiddenField("topActivity") ?: hiddenField("baseActivity")) as? ComponentName

    private companion object {
        const val ACTIVITY_TYPE_STANDARD = 1
        const val RECENT_IGNORE_UNAVAILABLE = 0x0002
        const val SNAPSHOT_DIRECTORY = "/data/system_ce/0/snapshots"
    }
}
