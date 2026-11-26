package com.thorctl.daemon.system

import java.io.File

/** The activity task manager: focus, tasks per display, recents and task previews. */
interface TaskService {
    /** Display of the focused task, or -1. */
    fun focusedDisplayId(): Int

    fun focusTopTask(displayId: Int)

    /** Top-most visible standard (non-home) root task on a display, i.e. the app the user sees. */
    fun topAppTask(displayId: Int): AppTask?

    /** Packages of every visible root task on a display (any activity type, home included). */
    fun visiblePackages(displayId: Int): Set<String>

    /** Moves a task to another display and brings it to front there. */
    fun moveTaskToDisplay(taskId: Int, displayId: Int)

    /** Most recent first, as the system's recents list (home and excluded tasks are left out). */
    fun recentTasks(limit: Int): List<RecentTaskEntry>

    /** Brings a recent task to front on [displayId] (any if < 0), restoring it if it isn't running. */
    fun startFromRecents(taskId: Int, displayId: Int)

    /** Removes the task from recents and closes it, like swiping it away. */
    fun removeTask(taskId: Int): Boolean

    /** Asks for a fresh preview of a visible task, written where [snapshotFile] finds it within ~150 ms. */
    fun takeSnapshot(taskId: Int)

    /** The persisted low-resolution preview of a task, if the system has one. */
    fun snapshotFile(taskId: Int): File?
}
