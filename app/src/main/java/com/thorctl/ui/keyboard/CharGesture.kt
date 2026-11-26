package com.thorctl.ui.keyboard

import androidx.compose.runtime.key

/** What a finger on a character key is doing. */
internal enum class CharGesture {
    PRESS,

    /** Swiping down to type the key's second character. */
    SWIPE,

    /** Held: sliding along the accent strip. */
    ACCENTS_OPEN,
}
