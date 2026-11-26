package com.thorctl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Android 17 status bar battery: rounded body with the level printed inside, bolt when charging. */
@Composable
fun BatteryPill(level: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = when {
        charging -> Color(0xFF63CF79)
        level <= 15 -> colors.error
        else -> colors.onSurface
    }
    val textMeasurer = rememberTextMeasurer()
    val style =
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 32.dp, height = 17.dp)) {
            val bodyWidth = size.width - 2.5.dp.toPx()
            val bodyCorner = CornerRadius(5.dp.toPx())
            val split = bodyWidth * (level.coerceIn(0, 100) / 100f)
            drawRoundRect(
                colors.onSurface.copy(alpha = 0.3f),
                size = Size(bodyWidth, size.height),
                cornerRadius = bodyCorner
            )
            clipRect(right = split) {
                drawRoundRect(
                    fill,
                    size = Size(bodyWidth, size.height),
                    cornerRadius = bodyCorner
                )
            }
            drawRoundRect(
                colors.onSurface.copy(alpha = 0.45f),
                topLeft = Offset(bodyWidth + 0.8.dp.toPx(), size.height * 0.32f),
                size = Size(1.7.dp.toPx(), size.height * 0.36f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            val levelText = textMeasurer.measure("$level", style)
            val textOffset = Offset(
                (bodyWidth - levelText.size.width) / 2f,
                (size.height - levelText.size.height) / 2f
            )
            clipRect(right = split) {
                drawText(
                    levelText,
                    color = colors.surface,
                    topLeft = textOffset
                )
            }
            clipRect(left = split) {
                drawText(
                    levelText,
                    color = colors.onSurface,
                    topLeft = textOffset
                )
            }
        }
        if (charging) Icon(Icons.Rounded.Bolt, null, Modifier.size(15.dp), tint = colors.onSurface)
    }
}
