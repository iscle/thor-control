package com.thorctl.daemon.system

import android.app.ActivityTaskManager
import android.app.TaskInfo
import java.lang.reflect.Field

/** TaskInfo's hidden fields, resolved once (TaskInfo is public, so the stubs can't declare them); null where a release lacks one. */
private val displayIdField: Field? by lazy { TaskInfo::class.java.declaredFieldOrNull("displayId") }
private val topActivityTypeField: Field? by lazy { TaskInfo::class.java.declaredFieldOrNull("topActivityType") }

/** The display the task is on; -1 if unknown (or, for a recent task, not running). */
internal val TaskInfo.displayIdOrNone: Int get() = displayIdField?.get(this) as? Int ?: -1

/** WindowConfiguration.ACTIVITY_TYPE_* of the task's top activity, or null. */
internal val TaskInfo.topActivityTypeOrNull: Int? get() = topActivityTypeField?.get(this) as? Int

/** Visible by a root task's own flag or by the task's. */
internal val TaskInfo.isVisibleRootTask: Boolean get() = (this as? ActivityTaskManager.RootTaskInfo)?.visible == true || isVisible
