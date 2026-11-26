package com.thorctl.protocol.model

/**
 * Gamepad mode: the built-in controller becomes a standard HID gamepad for another device, over
 * USB (the Thor as a USB device) or Bluetooth (the Thor as a Bluetooth HID device).
 */
data class GamepadState(
    val on: Boolean = false,
    /** Kept when gamepad mode is off. */
    val transport: GamepadTransport = GamepadTransport.USB,
    /** A host is using the gamepad (USB: enumerated by a computer; Bluetooth: connected). */
    val connected: Boolean = false,
    /** Name of the connected Bluetooth host, or empty. */
    val host: String = "",
    /** Darken both screens while a host is connected (the controller keeps working). */
    val screensOff: Boolean = true,
    /** The screens are dark right now: top panel off, bottom panel black (drawn by the app). */
    val dark: Boolean = false,
    /** USB set-up failed (no gadget support or the port is in host mode). */
    val error: String = "",
) {
    val isBluetooth: Boolean get() = transport == GamepadTransport.BLUETOOTH
}
