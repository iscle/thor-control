package com.thorctl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Android 17 status bar battery: rounded body with the level printed inside, bolt when charging. */
@Composable
fun BatteryPill(level: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val fill = when {
        charging -> Color(0xFF63CF79)
        level <= 15 -> cs.error
        else -> cs.onSurface
    }
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 32.dp, height = 17.dp)) {
            val bodyW = size.width - 2.5.dp.toPx()
            val r = CornerRadius(5.dp.toPx())
            val split = bodyW * (level.coerceIn(0, 100) / 100f)
            drawRoundRect(cs.onSurface.copy(alpha = 0.3f), size = Size(bodyW, size.height), cornerRadius = r)
            clipRect(right = split) { drawRoundRect(fill, size = Size(bodyW, size.height), cornerRadius = r) }
            drawRoundRect(
                cs.onSurface.copy(alpha = 0.45f),
                topLeft = Offset(bodyW + 0.8.dp.toPx(), size.height * 0.32f),
                size = Size(1.7.dp.toPx(), size.height * 0.36f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            val t = textMeasurer.measure("$level", style)
            val o = Offset((bodyW - t.size.width) / 2f, (size.height - t.size.height) / 2f)
            clipRect(right = split) { drawText(t, color = cs.surface, topLeft = o) }
            clipRect(left = split) { drawText(t, color = cs.onSurface, topLeft = o) }
        }
        if (charging) Icon(Icons.Rounded.Bolt, null, Modifier.size(15.dp), tint = cs.onSurface)
    }
}
