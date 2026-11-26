package com.thorctl.platform.haptics

/** The system's predefined vibrations, as the shade uses them. */
interface Haptics {
    /** Key presses, touchpad steps, cards passing by. */
    fun tick()

    /** Long presses, confirmations. */
    fun heavyClick()
}
