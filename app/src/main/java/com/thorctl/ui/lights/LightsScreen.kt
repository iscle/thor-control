package com.thorctl.ui.lights

import com.thorctl.ui.components.DaemonPage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.protocol.model.LedMode
import com.thorctl.ui.components.ConnectedChoice
import com.thorctl.ui.components.DetailHeader
import com.thorctl.ui.components.PagePadding
import com.thorctl.ui.components.QsSlider
import com.thorctl.ui.theme.MorphShape
import com.thorctl.ui.theme.rememberMorph
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun LightsScreen(onBack: () -> Unit, viewModel: LightsViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DaemonPage(
        title = "Stick lights",
        onBack = onBack,
        state = state,
        trailing = { content ->
            Switch(
                checked = content.leds.enabled,
                onCheckedChange = viewModel::setEnabled,
                thumbContent = {
                    Icon(if (content.leds.enabled) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
                },
            )
        },
    ) { content ->
        val leds = content.leds
        Spacer(Modifier.height(10.dp))
        val modes = listOf(LedMode.SOLID, LedMode.BREATHE, LedMode.RAINBOW)
        ConnectedChoice(listOf("Solid", "Breathe", "Rainbow"), modes.indexOf(leds.mode).coerceAtLeast(0)) { index -> viewModel.setMode(modes[index]) }
        Spacer(Modifier.height(8.dp))
        ConnectedChoice(listOf("Both sticks", "Left", "Right"), content.target.ordinal, enabled = content.colorEditable) { index ->
            viewModel.setTarget(StickTarget.entries[index])
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Swatches.forEach { rgb ->
                Swatch(rgb, selected = (content.color and 0xFFFFFF) == rgb, enabled = content.colorEditable) { viewModel.setColor(rgb) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val hsvComponents = FloatArray(3).also { android.graphics.Color.colorToHSV(0xFF000000.toInt() or content.color, it) }
        QsSlider(
            value = hsvComponents[0] / 360f,
            onValueChange = { hue -> viewModel.setColor(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, 1f, 1f))) },
            onValueChangeFinished = {},
            icon = Icons.Rounded.Palette,
            label = "Color",
            enabled = content.colorEditable,
            trackBrush = Brush.horizontalGradient((0..6).map { step -> Color.hsv(step * 60f % 360f, 1f, 1f) }),
        )
        Spacer(Modifier.height(4.dp))
        QsSlider(
            value = leds.brightness / 255f,
            onValueChange = viewModel::setBrightness,
            onValueChangeFinished = {},
            icon = Icons.Rounded.Lightbulb,
            label = "Brightness",
            enabled = leds.enabled,
        )
        Spacer(Modifier.height(4.dp))
        QsSlider(
            value = LightsViewModel.speedFraction(leds.speed),
            onValueChange = viewModel::setSpeed,
            onValueChangeFinished = {},
            icon = Icons.Rounded.Speed,
            label = "Effect speed",
            enabled = content.speedEditable,
        )
    }
}

private val Swatches = listOf(
    0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x00C7BE, 0x4FC3F7, 0x0A84FF, 0x5E5CE6, 0xBF5AF2, 0xFF2D92, 0xFFFFFF,
)

/** Color swatch: a circle that morphs into a 9-sided cookie (and turns) when selected. */
@Composable
private fun Swatch(rgb: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val morph = rememberMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided)
    val selection by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "swatch")
    val color = Color(0xFF000000.toInt() or rgb).copy(alpha = if (enabled) 1f else 0.3f)
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                rotationZ = selection * 40f
                val scale = 0.82f + 0.18f * selection
                scaleX = scale
                scaleY = scale
            }
            .clip(MorphShape(morph, selection))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val onColor = if (rgb == 0xFFFFFF || rgb == 0xFFCC00 || rgb == 0x4FC3F7 || rgb == 0x00C7BE) Color.Black else Color.White
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp).graphicsLayer { rotationZ = -selection * 40f }, tint = onColor.copy(alpha = selection))
        } else if (rgb == 0xFFFFFF || rgb == 0x000000) {
            Box(Modifier.size(40.dp).clip(MorphShape(morph, 0f)).background(colors.outline.copy(alpha = 0.15f)))
        }
    }
}