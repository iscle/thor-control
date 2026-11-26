package com.thorctl.ui.keyboard

import androidx.compose.runtime.key

internal enum class FunctionKind { SHIFT, BACK, SPACE, ENTER, TO_LETTERS, TO_SYMBOLS, TO_MORE }

// Every layer has the same key positions; a key's swipe-down character is the next layer's key in
// the same spot (letters -> ?123 -> =\<), like the iPad keyboard.
