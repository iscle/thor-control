package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun HideButton() {
    val kb = LocalKeyboardActions.current
    Box(
        Modifier
            .size(44.dp)
            .raised(22.dp, pressed = false)
            .clip(CircleShape)
            .background(KeyFill)
            .pointerInput(Unit) { detectTapGestures(onTap = { kb.hideKeyboard() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.KeyboardHide, "Hide keyboard", tint = KeyInk, modifier = Modifier.size(22.dp))
    }
}

// ---- Upright: a regular keyboard on the top screen -----------------------------------------------
