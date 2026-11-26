package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.LipColor
import com.thorctl.ui.keyboard.KeyboardMetrics.LIP
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * A key's raise: a hard, unblurred darker edge along its bottom, the face sitting [LIP] above it.
 * Pressed, the face drops onto the edge, so the key looks pushed in. Apply before clip/background.
 */
internal fun Modifier.raised(corner: Dp, pressed: Boolean): Modifier = this
    .drawBehind {
        val lip = LIP.toPx()
        drawRoundRect(
            LipColor,
            topLeft = Offset(0f, lip),
            size = Size(size.width, size.height - lip),
            cornerRadius = CornerRadius(corner.toPx()),
        )
    }
    .padding(bottom = LIP)
    .graphicsLayer { translationY = if (pressed) LIP.toPx() else 0f }
