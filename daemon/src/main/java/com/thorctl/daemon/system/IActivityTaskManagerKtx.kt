package com.thorctl.daemon.system

import android.app.TaskInfo
import android.os.Bundle
import java.lang.reflect.Method

/** android.app.IActivityTaskManager (hidden), resolved once. */
private val activityTaskManager: Class<*> by lazy { Class.forName("android.app.IActivityTaskManager") }
private val getFocusedRootTaskInfoMethod: Method by lazy { activityTaskManager.method("getFocusedRootTaskInfo", 0) }
private val focusTopTaskMethod: Method by lazy { activityTaskManager.method("focusTopTask", 1) }
private val getAllRootTaskInfosOnDisplayMethod: Method by lazy { activityTaskManager.method("getAllRootTaskInfosOnDisplay", 1) }
private val moveRootTaskToDisplayMethod: Method by lazy { activityTaskManager.method("moveRootTaskToDisplay", 2) }
private val startActivityFromRecentsMethod: Method by lazy { activityTaskManager.method("startActivityFromRecents", 2) }
private val getRecentTasksMethod: Method by lazy { activityTaskManager.method("getRecentTasks", 3) }
private val removeTaskMethod: Method by lazy { activityTaskManager.method("removeTask", 1) }
private val getTaskSnapshotManagerMethod: Method by lazy { activityTaskManager.method("getTaskSnapshotManager", 0) }

/**
 * The snapshot manager's takeTaskSnapshot, found on the type getTaskSnapshotManager declares (its
 * name and parameters vary by release); null if this release has none.
 */
private val takeTaskSnapshotMethod: Method? by lazy {
    getTaskSnapshotManagerMethod.returnType.methods.firstOrNull { it.name == "takeTaskSnapshot" }
}

/** The focused root task (an ActivityTaskManager.RootTaskInfo), or null. */
internal fun SystemServiceProxy.getFocusedRootTaskInfo(): TaskInfo? = call(getFocusedRootTaskInfoMethod) as? TaskInfo

internal fun SystemServiceProxy.focusTopTask(displayId: Int) {
    call(focusTopTaskMethod, displayId)
}

/** The root tasks on a display, top-most first. */
internal fun SystemServiceProxy.getAllRootTaskInfosOnDisplay(displayId: Int): List<TaskInfo> =
    (call(getAllRootTaskInfosOnDisplayMethod, displayId) as? List<*>)?.filterIsInstance<TaskInfo>() ?: emptyList()

internal fun SystemServiceProxy.moveRootTaskToDisplay(taskId: Int, displayId: Int) {
    call(moveRootTaskToDisplayMethod, taskId, displayId)
}

internal fun SystemServiceProxy.startActivityFromRecents(taskId: Int, options: Bundle?) {
    call(startActivityFromRecentsMethod, taskId, options)
}

/** [flags]: ActivityManager.RECENT_* values. The tasks are ActivityManager.RecentTaskInfo. */
internal fun SystemServiceProxy.getRecentTasks(limit: Int, flags: Int, userId: Int): List<TaskInfo> =
    call(getRecentTasksMethod, limit, flags, userId)?.parceledList()?.filterIsInstance<TaskInfo>() ?: emptyList()

internal fun SystemServiceProxy.removeTask(taskId: Int): Boolean = call(removeTaskMethod, taskId) as? Boolean ?: false

/**
 * Asks for a fresh preview of a task, with updateCache so it is also written to disk, and
 * returns the TaskSnapshot (see TaskSnapshotKtx.kt); null if this release can't.
 */
internal fun SystemServiceProxy.takeTaskSnapshot(taskId: Int): Any? {
    val takeMethod = takeTaskSnapshotMethod ?: return null
    val snapshotManager = call(getTaskSnapshotManagerMethod) ?: return null
    // updateCache = true writes it to disk; the extra flags vary by release.
    val arguments: Array<Any?> = when (takeMethod.parameterCount) {
        4 -> arrayOf(taskId, true, false, false)
        3 -> arrayOf(taskId, true, false)
        2 -> arrayOf(taskId, true)
        else -> return null
    }
    return takeMethod.invoke(snapshotManager, *arguments)
}
