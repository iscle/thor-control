package com.thorctl.daemon.system

/** An input device as the input manager reports it. */
data class InputDeviceInfo(
    val id: Int,
    val name: String,
    val descriptor: String,
    val isExternal: Boolean,
    val isGamepad: Boolean,
)
