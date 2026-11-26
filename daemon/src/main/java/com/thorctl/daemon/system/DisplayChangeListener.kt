package com.thorctl.daemon.system

/** Told about display events, on a binder thread. */
fun interface DisplayChangeListener {
    fun onDisplayChanged(displayId: Int, change: DisplayChange)
}
