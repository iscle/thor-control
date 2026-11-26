package com.thorctl.ui.keyboard

/** What a finger on a character key is doing. */
internal enum class CharacterGesture {
    PRESS,

    /** Swiping down to type the key's second character. */
    SWIPE,

    /** Held: sliding along the accent strip. */
    ACCENTS_OPEN,
}
