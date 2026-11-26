package com.thorctl.ui.recents

/** Where the camera starts: the home card when opened from home, else the app recents was opened from. */
fun startIndex(cards: List<Card>, fromHome: Boolean, originTask: Int): Int =
    if (fromHome) {
        cards.lastIndex
    } else {
        cards.indexOfFirst { it.key == originTask }.takeIf { it >= 0 } ?: (cards.lastIndex - 1).coerceAtLeast(0)
    }
