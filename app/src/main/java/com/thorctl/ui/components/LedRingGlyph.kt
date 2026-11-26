package com.thorctl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState

/** Ring split in four segments showing the current stick light colors. */
@Composable
fun LedRingGlyph(leds: LedState, color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    var hueShift by remember { mutableFloatStateOf(0f) }
    val rainbow = leds.enabled && leds.mode == LedMode.RAINBOW
    LaunchedEffect(rainbow) {
        if (!rainbow) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (last != 0L) hueShift = (hueShift + (frameNanos - last) / 1e9f * 90f) % 360f
                last = frameNanos
            }
        }
    }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.17f
        val inset = stroke / 2 + size.minDimension * 0.06f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        for (segment in 0 until 4) {
            val segmentColor = when {
                !leds.enabled -> color.copy(alpha = 0.45f)
                rainbow -> Color.hsv((hueShift + segment * 90f) % 360f, 0.85f, 1f)
                else -> Color(0xFF000000.toInt() or (if (segment < 2) leds.left else leds.rightColor))
            }
            drawArc(
                segmentColor,
                startAngle = -90f + segment * 90f + 9f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
