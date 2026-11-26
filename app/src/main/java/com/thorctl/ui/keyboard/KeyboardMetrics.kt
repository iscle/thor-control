package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardMetrics.INLINE_KEYBOARD_HEIGHT
import com.thorctl.ui.keyboard.KeyboardMetrics.LIP
import com.thorctl.ui.keyboard.KeyboardMetrics.STEP
import com.thorctl.ui.keyboard.KeyboardMetrics.WORD_STEP
import com.thorctl.ui.keyboard.KeyboardMetrics.LONG_PRESS_MS
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes and timings of the keys and the touchpad. */
internal object KeyboardMetrics {
    /** Height of the keyboard on the top screen when the Thor is held upright. */
    val INLINE_KEYBOARD_HEIGHT: Dp = 268.dp

    /** Height of the hard edge under each key (the iOS keyboard's "raise"). */
    val LIP: Dp = 2.dp

    /** Touchpad distance per character. */
    val STEP: Dp = 11.dp

    /** Backspace swipe distance per word. */
    val WORD_STEP: Dp = 28.dp

    /** Hold a key this long for its accents. */
    const val LONG_PRESS_MS = 380L
}
