package com.thorctl.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import com.thorctl.platform.keyboard.KeyboardActions

/** What the keys do; provided by the window that shows them (the keyboard's ViewModel). */
val LocalKeyboardActions =
    staticCompositionLocalOf<KeyboardActions> { error("no KeyboardActions provided") }
