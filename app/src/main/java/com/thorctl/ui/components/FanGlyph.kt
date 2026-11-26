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
    val currentSpeed by rememberUpdatedState(speed)
    LaunchedEffect(speed > 0.01f) {
        if (currentSpeed <= 0.01f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (last != 0L) angle = (angle + (frameNanos - last) / 1e9f * (90f + 900f * currentSpeed)) % 360f
                last = frameNanos
            }
        }
    }
    Canvas(modifier) {
        val gridUnit = size.minDimension / 24f
        val blade = Path().apply {
            moveTo(12f * gridUnit, 12f * gridUnit)
            cubicTo(10.4f * gridUnit, 9.2f * gridUnit, 8.6f * gridUnit, 5.4f * gridUnit, 10.9f * gridUnit, 3.7f * gridUnit)
            cubicTo(13.1f * gridUnit, 2.3f * gridUnit, 16.1f * gridUnit, 4.1f * gridUnit, 15.0f * gridUnit, 7.4f * gridUnit)
            cubicTo(14.4f * gridUnit, 9.4f * gridUnit, 12.9f * gridUnit, 10.9f * gridUnit, 12f * gridUnit, 12f * gridUnit)
            close()
        }
        rotate(angle) {
            repeat(3) { bladeIndex -> rotate(bladeIndex * 120f) { drawPath(blade, color) } }
        }
        drawCircle(color, radius = 2.4f * gridUnit)
    }
}
