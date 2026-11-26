package com.thorctl.ui.components

import com.thorctl.ui.theme.qsColors
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Android 17 Quick Settings brightness slider (M3 Expressive, large): 40dp track with small
 * inner corners, a 4dp-wide handle that sticks out above and below the track and narrows while
 * dragging, icon inset at the end. The label is drawn two-tone where the active track covers it.
 */
@Composable
fun QsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackBrush: Brush? = null,
) {
    val cs = MaterialTheme.colorScheme
    val qs = qsColors()
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    if (!dragging && local != value) local = value
    var lastStep by remember { mutableIntStateOf(-1) }
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val shown by animateFloatAsState(local, if (dragging) snap() else spring(stiffness = 500f), label = "slider")
    val handleW by animateDpAsState(if (dragging) 2.dp else 4.dp, spring(stiffness = 1400f), label = "handle")

    val active = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.28f)
    val onActive = if (enabled) cs.onPrimary else cs.surface
    val textMeasurer = rememberTextMeasurer()
    val painter = rememberVectorPainter(icon)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val minX = 26.dp.toPx()
                val maxX = size.width - 2.dp.toPx()
                fun update(x: Float) {
                    val v = ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
                    local = v
                    val step = (v * 20).roundToInt()
                    if (step != lastStep) {
                        lastStep = step
                        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    }
                    onChange(v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    update(down.position.x)
                    drag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                    dragging = false
                    onFinished(local)
                }
            },
    ) {
        val w = size.width
        val trackH = 40.dp.toPx()
        val top = (size.height - trackH) / 2f
        val bottom = top + trackH
        val outer = 12.dp.toPx()
        val inner = 2.dp.toPx()
        val hw = handleW.toPx()
        val gap = 6.dp.toPx()
        val minX = 26.dp.toPx()
        val maxX = w - 2.dp.toPx()
        val hx = minX + (maxX - minX) * shown
        val activeEnd = hx - hw / 2f - gap
        val inactiveStart = hx + hw / 2f + gap

        if (inactiveStart < w - 2f) {
            val p = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(inactiveStart, top, w, bottom),
                        topLeft = CornerRadius(inner), bottomLeft = CornerRadius(inner),
                        topRight = CornerRadius(outer), bottomRight = CornerRadius(outer),
                    ),
                )
            }
            if (trackBrush != null) drawPath(p, trackBrush, alpha = 0.45f) else drawPath(p, qs.container)
        }
        val ap = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(0f, top, activeEnd.coerceAtLeast(outer * 1.2f), bottom),
                    topLeft = CornerRadius(outer), bottomLeft = CornerRadius(outer),
                    topRight = CornerRadius(inner), bottomRight = CornerRadius(inner),
                ),
            )
        }
        if (trackBrush != null) drawPath(ap, trackBrush) else drawPath(ap, active)
        drawRoundRect(
            color = if (trackBrush != null) cs.onSurface else active,
            topLeft = Offset(hx - hw / 2f, 0f),
            size = Size(hw, size.height),
            cornerRadius = CornerRadius(hw / 2f),
        )

        // Icon inset at the end of the track.
        val iconSize = 24.dp.toPx()
        val ix = w - 16.dp.toPx() - iconSize
        val iy = (size.height - iconSize) / 2f
        clipRect(right = activeEnd) {
            translate(ix, iy) { with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(onActive)) } }
        }
        clipRect(left = activeEnd) {
            translate(ix, iy) { with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(cs.onSurfaceVariant)) } }
        }
        // Label at the start.
        val text = textMeasurer.measure(label, labelStyle)
        val tx = 18.dp.toPx()
        val ty = (size.height - text.size.height) / 2f
        clipRect(right = activeEnd) { drawText(text, color = onActive, topLeft = Offset(tx, ty)) }
        clipRect(left = activeEnd) { drawText(text, color = cs.onSurface, topLeft = Offset(tx, ty)) }
    }
}
