package com.thorctl.daemon.feature.gamepad

/**
 * The Thor's USB gadget as gamepad mode uses it: a HID function added next to adb, with the
 * gamepad's vendor / product ID on the whole gadget while it is there. Calls block (they rebind
 * the gadget), so they run off the caller's lock.
 */
interface UsbGadget {
    /** The HID function is linked into the gadget's configuration. */
    fun isLinked(): Boolean

    /** Something of ours is left behind (linked, or Android's IDs still saved): a crash in gamepad mode. */
    fun hasLeftovers(): Boolean

    /** Adds the HID function to the gadget and rebinds it. Returns an error message or null. */
    fun addHidFunction(): String?

    /** Takes the HID function out again and gives Android its IDs back. */
    fun removeHidFunction()

    /** A host has enumerated and configured the gadget. */
    fun isConfigured(): Boolean
}
