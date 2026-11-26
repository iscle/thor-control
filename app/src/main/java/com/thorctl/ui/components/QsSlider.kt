package com.thorctl.ui.components

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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.ui.theme.qsColors
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
    val colors = MaterialTheme.colorScheme
    val shade = qsColors()
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    if (!dragging && local != value) local = value
    var lastStep by remember { mutableIntStateOf(-1) }
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val shown by animateFloatAsState(
        local,
        if (dragging) snap() else spring(stiffness = 500f),
        label = "slider"
    )
    val handleWidth by animateDpAsState(
        if (dragging) 2.dp else 4.dp,
        spring(stiffness = 1400f),
        label = "handle"
    )

    val active = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.28f)
    val onActive = if (enabled) colors.onPrimary else colors.surface
    val textMeasurer = rememberTextMeasurer()
    val painter = rememberVectorPainter(icon)
    val labelStyle =
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val minX = 26.dp.toPx()
                val maxX = size.width - 2.dp.toPx()
                fun update(touchX: Float) {
                    val fraction = ((touchX - minX) / (maxX - minX)).coerceIn(0f, 1f)
                    local = fraction
                    val step = (fraction * 20).roundToInt()
                    if (step != lastStep) {
                        lastStep = step
                        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    }
                    onChange(fraction)
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
        val canvasWidth = size.width
        val trackHeight = 40.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val bottom = top + trackHeight
        val outer = 12.dp.toPx()
        val inner = 2.dp.toPx()
        val handleWidthPx = handleWidth.toPx()
        val gap = 6.dp.toPx()
        val minX = 26.dp.toPx()
        val maxX = canvasWidth - 2.dp.toPx()
        val handleX = minX + (maxX - minX) * shown
        val activeEnd = handleX - handleWidthPx / 2f - gap
        val inactiveStart = handleX + handleWidthPx / 2f + gap

        if (inactiveStart < canvasWidth - 2f) {
            val inactiveTrack = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(inactiveStart, top, canvasWidth, bottom),
                        topLeft = CornerRadius(inner), bottomLeft = CornerRadius(inner),
                        topRight = CornerRadius(outer), bottomRight = CornerRadius(outer),
                    ),
                )
            }
            if (trackBrush != null) drawPath(
                inactiveTrack,
                trackBrush,
                alpha = 0.45f
            ) else drawPath(inactiveTrack, shade.container)
        }
        val activeTrack = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(0f, top, activeEnd.coerceAtLeast(outer * 1.2f), bottom),
                    topLeft = CornerRadius(outer), bottomLeft = CornerRadius(outer),
                    topRight = CornerRadius(inner), bottomRight = CornerRadius(inner),
                ),
            )
        }
        if (trackBrush != null) drawPath(activeTrack, trackBrush) else drawPath(activeTrack, active)
        drawRoundRect(
            color = if (trackBrush != null) colors.onSurface else active,
            topLeft = Offset(handleX - handleWidthPx / 2f, 0f),
            size = Size(handleWidthPx, size.height),
            cornerRadius = CornerRadius(handleWidthPx / 2f),
        )

        // Icon inset at the end of the track.
        val iconSize = 24.dp.toPx()
        val iconX = canvasWidth - 16.dp.toPx() - iconSize
        val iconY = (size.height - iconSize) / 2f
        clipRect(right = activeEnd) {
            translate(iconX, iconY) {
                with(painter) {
                    draw(
                        Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(onActive)
                    )
                }
            }
        }
        clipRect(left = activeEnd) {
            translate(iconX, iconY) {
                with(painter) {
                    draw(
                        Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(colors.onSurfaceVariant)
                    )
                }
            }
        }
        // Label at the start.
        val text = textMeasurer.measure(label, labelStyle)
        val labelX = 18.dp.toPx()
        val labelY = (size.height - text.size.height) / 2f
        clipRect(right = activeEnd) {
            drawText(
                text,
                color = onActive,
                topLeft = Offset(labelX, labelY)
            )
        }
        clipRect(left = activeEnd) {
            drawText(
                text,
                color = colors.onSurface,
                topLeft = Offset(labelX, labelY)
            )
        }
    }
}
