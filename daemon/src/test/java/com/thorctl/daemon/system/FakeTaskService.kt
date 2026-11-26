package com.thorctl.daemon.system

import java.io.File

class FakeTaskService : TaskService {
    var focusedDisplay = -1
    val topTasks = HashMap<Int, AppTask>()
    val visible = HashMap<Int, Set<String>>()
    var recents: List<RecentTaskEntry> = emptyList()
    val snapshots = HashMap<Int, File>()

    /** Every call that changes something, as readable strings ("move 5 -> 1"), in order. */
    val actions = mutableListOf<String>()

    override fun focusedDisplayId(): Int = focusedDisplay

    override fun focusTopTask(displayId: Int) {
        actions += "focus $displayId"
    }

    override fun topAppTask(displayId: Int): AppTask? = topTasks[displayId]

    override fun visiblePackages(displayId: Int): Set<String> = visible[displayId] ?: emptySet()

    override fun moveTaskToDisplay(taskId: Int, displayId: Int) {
        actions += "move $taskId -> $displayId"
    }

    override fun recentTasks(limit: Int): List<RecentTaskEntry> = recents.take(limit)

    override fun startFromRecents(taskId: Int, displayId: Int) {
        actions += "start $taskId on $displayId"
    }

    override fun removeTask(taskId: Int): Boolean {
        actions += "remove $taskId"
        recents = recents.filterNot { it.taskId == taskId }
        return true
    }

    override fun snapshotFile(taskId: Int): File? = snapshots[taskId]
}
