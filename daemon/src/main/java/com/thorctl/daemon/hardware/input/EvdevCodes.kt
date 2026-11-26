package com.thorctl.daemon.hardware.input

/** Linux input event types and codes (linux/input-event-codes.h) the daemon reads or writes. */
object EvdevCodes {
    const val EV_SYN = 0
    const val EV_KEY = 1
    const val EV_ABS = 3
    const val EV_SW = 5

    const val SYN_REPORT = 0

    const val SW_LID = 0

    const val KEY_HOME = 102
    const val KEY_UP = 103
    const val KEY_LEFT = 105
    const val KEY_RIGHT = 106
    const val KEY_DOWN = 108
    const val KEY_POWER = 116
    const val KEY_BACK = 158
    const val KEY_F24 = 194 // the AYN button on gpio-keys

    const val BTN_SOUTH = 304 // "A"
    const val BTN_EAST = 305 // "B" in the controller key layout
    const val BTN_NORTH = 307 // "X"
    const val BTN_WEST = 308 // "Y"
    const val BTN_TL = 310 // L1
    const val BTN_TR = 311 // R1
    const val BTN_TL2 = 312 // L2 (digital)
    const val BTN_TR2 = 313 // R2 (digital)
    const val BTN_THUMBR = 318
    const val BTN_DPAD_UP = 544
    const val BTN_DPAD_DOWN = 545
    const val BTN_DPAD_LEFT = 546
    const val BTN_DPAD_RIGHT = 547

    const val ABS_Z = 0x02 // L2 (analog, 0..255)
    const val ABS_RZ = 0x05 // R2 (analog, 0..255)
    const val ABS_HAT0X = 0x10
    const val ABS_HAT0Y = 0x11
    const val ABS_CNT = 0x40
}
