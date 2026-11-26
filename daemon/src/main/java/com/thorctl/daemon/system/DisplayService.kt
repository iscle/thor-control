package com.thorctl.daemon.system

/** The display manager: displays, their brightness and their power. */
interface DisplayService {
    fun displays(): List<DisplayInfo>

    fun brightness(displayId: Int): Float

    fun setBrightness(displayId: Int, value: Float)

    /** [state]: Display.STATE_OFF (1) forces it off, Display.STATE_UNKNOWN (0) hands control back. */
    fun requestDisplayPower(displayId: Int, state: Int): Boolean

    /**
     * Tells [listener] about displays added, removed, or changing power state or brightness, from now on. The
     * registration dies with the display manager (a system_server restart): calling this again
     * registers anew if that happened, and does nothing otherwise.
     */
    fun watch(listener: DisplayChangeListener)
}
