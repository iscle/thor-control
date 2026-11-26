package com.thorctl.platform.keyboard

import androidx.compose.runtime.Immutable

@Immutable
data class KeyboardUi(
    val layer: KeyboardLayer = KeyboardLayer.LETTERS,
    val shift: Boolean = false,
    val caps: Boolean = false,
    val selecting: Boolean = false,
    val enter: EnterKey = EnterKey.RETURN,
    /** Text around the cursor, for the preview on the touchpad. */
    val before: String = "",
    val selected: String = "",
    val after: String = "",
    /** Short confirmation shown on the touchpad ("Copied"), or null. */
    val flash: String? = null,
    val flashId: Long = 0,
)
