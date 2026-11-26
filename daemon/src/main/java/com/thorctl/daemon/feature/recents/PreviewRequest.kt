package com.thorctl.daemon.feature.recents

/** Fresh previews asked of the system for [taskIds], at wall-clock time [since]. */
data class PreviewRequest(val taskIds: List<Int>, val since: Long)
