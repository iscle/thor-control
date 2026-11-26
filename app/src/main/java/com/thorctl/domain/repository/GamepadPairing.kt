package com.thorctl.domain.repository

/** Lets a new host find the Thor as a Bluetooth gamepad. */
interface GamepadPairing {
    /** Android's "make visible" prompt, for two minutes. */
    fun makeDiscoverable()
}
