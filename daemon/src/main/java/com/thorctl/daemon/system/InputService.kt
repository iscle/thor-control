package com.thorctl.daemon.system

/** The input manager: devices, their display association, and injected keys. */
interface InputService {
    fun devices(): List<InputDeviceInfo>

    /** Sends the device with [descriptor] to the display with [displayUniqueId]. */
    fun associate(descriptor: String, displayUniqueId: String)

    /** Lets the device with [descriptor] follow the focused display again. */
    fun removeAssociation(descriptor: String)

    fun setDeviceEnabled(deviceId: Int, enabled: Boolean)

    /** Injects a key press (down + up) as the system would; at one display if [displayId] >= 0. */
    fun injectKey(keyCode: Int, displayId: Int = -1)
}
