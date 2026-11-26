package com.thorctl.ui.keyboard

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.thorctl.ui.keyboard.KeyboardColors.LipColor
import com.thorctl.ui.keyboard.KeyboardMetrics.LIP

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
