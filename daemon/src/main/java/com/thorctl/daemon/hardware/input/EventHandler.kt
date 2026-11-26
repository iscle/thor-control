package com.thorctl.daemon.hardware.input

/** Receives raw evdev events one at a time, as the kernel reports them. */
typealias EventHandler = (type: Int, code: Int, value: Int) -> Unit
