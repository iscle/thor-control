package com.thorctl.ui.fan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GameState
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.LedState
import com.thorctl.protocol.model.Stats
import kotlin.math.hypot
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.ui.format.Format

private const val T_MIN = 25f
private const val T_MAX = 95f

@Composable
fun FanCurveEditor(
    profile: FanProfile,
    editable: Boolean,
    liveTemp: Float?,
    unit: TemperatureUnit,
    livePct: Int?,
    onChange: (FanProfile) -> Unit,
    onCommit: (FanProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val cur by rememberUpdatedState(profile)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    var dragIndex by remember { mutableIntStateOf(-1) }
    val pointGrow by animateFloatAsState(if (dragIndex >= 0) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "grow")

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(editable) {
                if (!editable) return@pointerInput
                val left = 44.dp.toPx()
                val right = 20.dp.toPx()
                val top = 20.dp.toPx()
                val bottom = 30.dp.toPx()
                val pw = size.width - left - right
                val ph = size.height - top - bottom
                fun px(t: Float) = left + (t - T_MIN) / (T_MAX - T_MIN) * pw
                fun py(p: Float) = top + (1f - p / 100f) * ph
                detectDragGestures(
                    onDragStart = { pos ->
                        val pts = cur.points
                        val (i, d) = pts.indices.map { it to hypot(px(pts[it].temp) - pos.x, py(pts[it].percent) - pos.y) }
                            .minByOrNull { it.second } ?: (-1 to Float.MAX_VALUE)
                        dragIndex = if (d < 40.dp.toPx()) i else -1
                    },
                    onDragEnd = {
                        if (dragIndex >= 0) commit(cur)
                        dragIndex = -1
                    },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { c, _ ->
                        val i = dragIndex
                        if (i < 0) return@detectDragGestures
                        c.consume()
                        val pts = cur.points.toMutableList()
                        val lo = if (i > 0) pts[i - 1].temp + 2f else T_MIN
                        val hi = if (i < pts.size - 1) pts[i + 1].temp - 2f else T_MAX
                        val t = (T_MIN + (c.position.x - left) / pw * (T_MAX - T_MIN)).coerceIn(lo, hi)
                        val p = ((1f - (c.position.y - top) / ph) * 100f).coerceIn(0f, 100f)
                        pts[i] = FanPoint(Math.round(t).toFloat(), Math.round(p).toFloat())
                        change(cur.copy(points = pts))
                    },
                )
            },
    ) {
        val left = 44.dp.toPx()
        val right = 20.dp.toPx()
        val top = 20.dp.toPx()
        val bottom = 30.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom
        fun px(t: Float) = left + (t - T_MIN) / (T_MAX - T_MIN) * pw
        fun py(p: Float) = top + (1f - p / 100f) * ph
        val grid = cs.outlineVariant.copy(alpha = 0.45f)

        for (p in 0..100 step 25) {
            drawLine(grid, Offset(left, py(p.toFloat())), Offset(left + pw, py(p.toFloat())), 1.dp.toPx())
            val l = tm.measure("$p%", labelStyle)
            drawText(l, cs.onSurfaceVariant, Offset(left - l.size.width - 8.dp.toPx(), py(p.toFloat()) - l.size.height / 2))
        }
        for (t in 30..90 step 10) {
            drawLine(grid, Offset(px(t.toFloat()), top), Offset(px(t.toFloat()), top + ph), 1.dp.toPx())
            val l = tm.measure(Format.temperature(t.toFloat(), unit), labelStyle)
            drawText(l, cs.onSurfaceVariant, Offset(px(t.toFloat()) - l.size.width / 2, top + ph + 6.dp.toPx()))
        }

        val pts = profile.points.sortedBy { it.temp }
        if (pts.isNotEmpty()) {
            val line = Path().apply {
                moveTo(px(T_MIN), py(pts.first().percent))
                pts.forEach { lineTo(px(it.temp.coerceIn(T_MIN, T_MAX)), py(it.percent)) }
                lineTo(px(T_MAX), py(pts.last().percent))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(px(T_MAX), top + ph)
                lineTo(px(T_MIN), top + ph)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.32f), cs.primary.copy(alpha = 0.02f)), startY = top, endY = top + ph))
            drawPath(line, cs.primary, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            pts.forEachIndexed { i, p ->
                val c = Offset(px(p.temp), py(p.percent))
                if (editable) {
                    val r = 8.dp.toPx() + if (i == dragIndex) 5.dp.toPx() * pointGrow else 0f
                    drawCircle(cs.surfaceContainerHigh, r + 3.dp.toPx(), c)
                    drawCircle(cs.primary, r, c)
                    if (i == dragIndex) {
                        val l = tm.measure("${Format.temperature(p.temp, unit)} → ${p.percent.toInt()}%", labelStyle.copy(fontWeight = FontWeight.SemiBold))
                        drawText(l, cs.onSurface, Offset((c.x - l.size.width / 2).coerceIn(left, left + pw - l.size.width), c.y - r - l.size.height - 6.dp.toPx()))
                    }
                } else {
                    drawCircle(cs.primary, 4.5.dp.toPx(), c)
                }
            }
        }

        if (liveTemp != null && liveTemp > 0f) {
            val x = px(liveTemp.coerceIn(T_MIN, T_MAX))
            drawLine(
                cs.tertiary, Offset(x, top), Offset(x, top + ph), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
            if (livePct != null) {
                val c = Offset(x, py(livePct.toFloat()))
                drawCircle(cs.surfaceContainerHigh, 9.dp.toPx(), c)
                drawCircle(cs.tertiary, 6.dp.toPx(), c)
            }
        }
    }
}
