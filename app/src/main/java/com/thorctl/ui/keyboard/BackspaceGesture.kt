package com.thorctl.ui.keyboard

/** What a finger on backspace is doing. */
internal enum class BackspaceGesture {
    PRESS,

    /** Held: deleting repeatedly. */
    REPEATING,

    /** Swiped left: selecting whole words to delete. */
    WORDS,
}
