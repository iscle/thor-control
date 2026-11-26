package com.thorctl.daemon.system

/** A running app process; [importance] is a RunningAppProcessInfo.IMPORTANCE_* value. */
data class AppProcess(val pid: Int, val uid: Int, val name: String, val packageNames: List<String>, val importance: Int)
