package com.thorctl.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.thorctl.platform.keyboard.KeyboardActions
import androidx.compose.runtime.staticCompositionLocalOf

/** What the keys do; provided by the window that shows them (the keyboard's ViewModel). */
val LocalKeyboardActions = staticCompositionLocalOf<KeyboardActions> { error("no KeyboardActions provided") }
