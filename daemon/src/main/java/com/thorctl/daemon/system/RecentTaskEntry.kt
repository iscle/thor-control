package com.thorctl.daemon.system

/** A task in the system's recents list; [displayId] is -1 for one no longer running (it is restored when opened). */
data class RecentTaskEntry(val taskId: Int, val packageName: String, val displayId: Int, val visible: Boolean, val running: Boolean)
