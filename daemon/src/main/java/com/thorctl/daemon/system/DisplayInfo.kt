package com.thorctl.daemon.system

/** A logical display as the display manager reports it; [state] is a Display.STATE_* value. */
data class DisplayInfo(val id: Int, val uniqueId: String, val type: Int, val width: Int, val height: Int, val state: Int)
