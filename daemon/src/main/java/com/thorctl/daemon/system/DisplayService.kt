package com.thorctl.daemon.system

/** The display manager: displays, their brightness and their power. */
interface DisplayService {
    fun displays(): List<DisplayInfo>

    fun brightness(displayId: Int): Float

    fun setBrightness(displayId: Int, value: Float)

    /** [state]: Display.STATE_OFF (1) forces it off, Display.STATE_UNKNOWN (0) hands control back. */
    fun requestDisplayPower(displayId: Int, state: Int): Boolean
}
