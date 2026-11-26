package com.thorctl.daemon.system

/** ServiceManager names of system services that Context does not expose (they are hidden there). */
object HiddenServiceNames {
    /** Context.ACTIVITY_TASK_SERVICE: the activity task manager. */
    const val ACTIVITY_TASK_SERVICE = "activity_task"

    /** The package manager's binder service. */
    const val PACKAGE_SERVICE = "package"
}
