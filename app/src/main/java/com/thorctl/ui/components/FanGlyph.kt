package com.thorctl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Three-blade fan that spins proportionally to the real fan duty. */
@Composable
fun FanGlyph(speed: Float, color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    var angle by remember { mutableFloatStateOf(0f) }
    val sp by rememberUpdatedState(speed)
    LaunchedEffect(speed > 0.01f) {
        if (sp <= 0.01f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) angle = (angle + (t - last) / 1e9f * (90f + 900f * sp)) % 360f
                last = t
            }
        }
    }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val blade = Path().apply {
            moveTo(12f * s, 12f * s)
            cubicTo(10.4f * s, 9.2f * s, 8.6f * s, 5.4f * s, 10.9f * s, 3.7f * s)
            cubicTo(13.1f * s, 2.3f * s, 16.1f * s, 4.1f * s, 15.0f * s, 7.4f * s)
            cubicTo(14.4f * s, 9.4f * s, 12.9f * s, 10.9f * s, 12f * s, 12f * s)
            close()
        }
        rotate(angle) {
            repeat(3) { i -> rotate(i * 120f) { drawPath(blade, color) } }
        }
        drawCircle(color, radius = 2.4f * s)
    }
}
