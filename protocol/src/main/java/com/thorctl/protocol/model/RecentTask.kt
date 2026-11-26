package com.thorctl.protocol.model

data class RecentTask(
    val id: Int,
    val packageName: String,
    val displayId: Int,
    val visible: Boolean,
    /** Not running any more: opening it starts the app again. */
    val running: Boolean,
    val thumb: Jpeg?,
)
