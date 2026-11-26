package com.thorctl.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** The last computer or console the Bluetooth gamepad was connected to, to reconnect to it. */
interface GamepadHostRepository {
    /** Its Bluetooth address, or null. */
    val lastHost: StateFlow<String?>

    fun remember(address: String)
}
