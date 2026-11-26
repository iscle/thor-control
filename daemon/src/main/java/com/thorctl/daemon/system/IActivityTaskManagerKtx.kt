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

