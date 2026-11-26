package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyPressed
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.Accent
import com.thorctl.ui.keyboard.KeyboardColors.AccentDeep
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.ui.components.LocalHaptics

@Composable
internal fun Pill(icon: ImageVector, label: String, active: Boolean = false, onTap: () -> Unit) {
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val fill = when {
        active -> Brush.verticalGradient(listOf(Accent, AccentDeep))
        pressed -> Brush.verticalGradient(listOf(KeyPressed, KeyPressed))
        else -> Brush.verticalGradient(listOf(KeyFill, KeyFill))
    }
    val ink = if (active) Color.White else KeyInk
    Row(
        Modifier
            .height(44.dp)
            .raised(22.dp, pressed)
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.tick()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() },
                )
            }
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
    }
}

// ---- Touchpad -----------------------------------------------------------------------------------
