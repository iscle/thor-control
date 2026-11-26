package com.thorctl.ui.fan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import com.thorctl.ui.format.Format
import kotlin.math.hypot

private const val MIN_TEMP = 25f
private const val MAX_TEMP = 95f

@Composable
fun FanCurveEditor(
    profile: FanProfile,
    editable: Boolean,
    liveTemp: Float?,
    unit: TemperatureUnit,
    livePercent: Int?,
    onChange: (FanProfile) -> Unit,
    onCommit: (FanProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val currentProfile by rememberUpdatedState(profile)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    var dragIndex by remember { mutableIntStateOf(-1) }
    val pointGrow by animateFloatAsState(
        if (dragIndex >= 0) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "grow"
    )

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(editable) {
                if (!editable) return@pointerInput
                val left = 44.dp.toPx()
                val right = 20.dp.toPx()
                val top = 20.dp.toPx()
                val bottom = 30.dp.toPx()
                val plotWidth = size.width - left - right
                val plotHeight = size.height - top - bottom
                fun xForTemp(temp: Float) =
                    left + (temp - MIN_TEMP) / (MAX_TEMP - MIN_TEMP) * plotWidth

                fun yForPercent(percent: Float) = top + (1f - percent / 100f) * plotHeight
                detectDragGestures(
                    onDragStart = { startPosition ->
                        val points = currentProfile.points
                        val (nearestIndex, nearestDistance) = points.indices.map {
                            it to hypot(
                                xForTemp(points[it].temp) - startPosition.x,
                                yForPercent(points[it].percent) - startPosition.y
                            )
                        }
                            .minByOrNull { it.second } ?: (-1 to Float.MAX_VALUE)
                        dragIndex = if (nearestDistance < 40.dp.toPx()) nearestIndex else -1
                    },
                    onDragEnd = {
                        if (dragIndex >= 0) commit(currentProfile)
                        dragIndex = -1
                    },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { pointerChange, _ ->
                        val index = dragIndex
                        if (index < 0) return@detectDragGestures
                        pointerChange.consume()
                        val points = currentProfile.points.toMutableList()
                        val lowestTemp = if (index > 0) points[index - 1].temp + 2f else MIN_TEMP
                        val highestTemp =
                            if (index < points.size - 1) points[index + 1].temp - 2f else MAX_TEMP
                        val temp =
                            (MIN_TEMP + (pointerChange.position.x - left) / plotWidth * (MAX_TEMP - MIN_TEMP)).coerceIn(
                                lowestTemp,
                                highestTemp
                            )
                        val percent =
                            ((1f - (pointerChange.position.y - top) / plotHeight) * 100f).coerceIn(
                                0f,
                                100f
                            )
                        points[index] =
                            FanPoint(Math.round(temp).toFloat(), Math.round(percent).toFloat())
                        change(currentProfile.copy(points = points))
                    },
                )
            },
    ) {
        val left = 44.dp.toPx()
        val right = 20.dp.toPx()
        val top = 20.dp.toPx()
        val bottom = 30.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        fun xForTemp(temp: Float) = left + (temp - MIN_TEMP) / (MAX_TEMP - MIN_TEMP) * plotWidth
        fun yForPercent(percent: Float) = top + (1f - percent / 100f) * plotHeight
        val grid = colors.outlineVariant.copy(alpha = 0.45f)

        for (percent in 0..100 step 25) {
            drawLine(
                grid,
                Offset(left, yForPercent(percent.toFloat())),
                Offset(left + plotWidth, yForPercent(percent.toFloat())),
                1.dp.toPx()
            )
            val label = textMeasurer.measure("$percent%", labelStyle)
            drawText(
                label,
                colors.onSurfaceVariant,
                Offset(
                    left - label.size.width - 8.dp.toPx(),
                    yForPercent(percent.toFloat()) - label.size.height / 2
                )
            )
        }
        for (temp in 30..90 step 10) {
            drawLine(
                grid,
                Offset(xForTemp(temp.toFloat()), top),
                Offset(xForTemp(temp.toFloat()), top + plotHeight),
                1.dp.toPx()
            )
            val label = textMeasurer.measure(Format.temperature(temp.toFloat(), unit), labelStyle)
            drawText(
                label,
                colors.onSurfaceVariant,
                Offset(
                    xForTemp(temp.toFloat()) - label.size.width / 2,
                    top + plotHeight + 6.dp.toPx()
                )
            )
        }

        val points = profile.points.sortedBy { it.temp }
        if (points.isNotEmpty()) {
            val line = Path().apply {
                moveTo(xForTemp(MIN_TEMP), yForPercent(points.first().percent))
                points.forEach {
                    lineTo(
                        xForTemp(it.temp.coerceIn(MIN_TEMP, MAX_TEMP)),
                        yForPercent(it.percent)
                    )
                }
                lineTo(xForTemp(MAX_TEMP), yForPercent(points.last().percent))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xForTemp(MAX_TEMP), top + plotHeight)
                lineTo(xForTemp(MIN_TEMP), top + plotHeight)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.32f),
                        colors.primary.copy(alpha = 0.02f)
                    ), startY = top, endY = top + plotHeight
                )
            )
            drawPath(line, colors.primary, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val pointCenter = Offset(xForTemp(point.temp), yForPercent(point.percent))
                if (editable) {
                    val radius =
                        8.dp.toPx() + if (index == dragIndex) 5.dp.toPx() * pointGrow else 0f
                    drawCircle(colors.surfaceContainerHigh, radius + 3.dp.toPx(), pointCenter)
                    drawCircle(colors.primary, radius, pointCenter)
                    if (index == dragIndex) {
                        val label = textMeasurer.measure(
                            "${
                                Format.temperature(
                                    point.temp,
                                    unit
                                )
                            } → ${point.percent.toInt()}%",
                            labelStyle.copy(fontWeight = FontWeight.SemiBold)
                        )
                        drawText(
                            label,
                            colors.onSurface,
                            Offset(
                                (pointCenter.x - label.size.width / 2).coerceIn(
                                    left,
                                    left + plotWidth - label.size.width
                                ), pointCenter.y - radius - label.size.height - 6.dp.toPx()
                            )
                        )
                    }
                } else {
                    drawCircle(colors.primary, 4.5.dp.toPx(), pointCenter)
                }
            }
        }

        if (liveTemp != null && liveTemp > 0f) {
            val liveX = xForTemp(liveTemp.coerceIn(MIN_TEMP, MAX_TEMP))
            drawLine(
                colors.tertiary, Offset(liveX, top), Offset(liveX, top + plotHeight), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
            if (livePercent != null) {
                val liveCenter = Offset(liveX, yForPercent(livePercent.toFloat()))
                drawCircle(colors.surfaceContainerHigh, 9.dp.toPx(), liveCenter)
                drawCircle(colors.tertiary, 6.dp.toPx(), liveCenter)
            }
        }
    }
}
