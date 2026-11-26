package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.daemon.system.SystemServiceUnavailableException
import com.thorctl.daemon.system.TaskService

/** A task service whose binder calls fail, as while the activity task manager is restarting. */
class UnavailableTaskService(private val fake: FakeTaskService = FakeTaskService()) : TaskService by fake {
    override fun topAppTask(displayId: Int): AppTask? = throw SystemServiceUnavailableException("activity_task")

    override fun takeSnapshot(taskId: Int) = throw SystemServiceUnavailableException("activity_task")

    override fun moveTaskToDisplay(taskId: Int, displayId: Int) = throw SystemServiceUnavailableException("activity_task")

    override fun startFromRecents(taskId: Int, displayId: Int) = throw SystemServiceUnavailableException("activity_task")
}
