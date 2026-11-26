package com.thorctl.ui.fan

import com.thorctl.ui.components.DaemonPage
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.ui.components.ConnectedChoice
import com.thorctl.ui.components.DetailHeader
import com.thorctl.ui.components.FanGlyph
import com.thorctl.ui.components.PagePadding
import com.thorctl.ui.format.Format
import com.thorctl.ui.theme.qsColors
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun FanScreen(onBack: () -> Unit, viewModel: FanViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val shade = qsColors()
    DaemonPage(
        title = "Fan",
        onBack = onBack,
        state = state,
        trailing = { content ->
            ConnectedChoice(
                options = listOf("°C", "°F"),
                selected = if (content.unit == TemperatureUnit.FAHRENHEIT) 1 else 0,
                modifier = Modifier.width(176.dp),
                onSelect = { index -> viewModel.setUnit(if (index == 1) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS) },
            )
            Spacer(Modifier.width(8.dp))
            content.stats.let { stats ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.tertiaryContainer)
                        .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FanGlyph(stats.fanPercent / 100f, colors.onTertiaryContainer, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Format.temperature(stats.fanTemp, content.unit)} · ${stats.fanPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onTertiaryContainer,
                    )
                }
            }
        },
    ) { content ->
        val stats = content.stats
        val active = content.active
        // The curve being dragged; the daemon's copy comes back once a point is let go.
        var draft by remember(active) { mutableStateOf(active) }

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(content.profiles, key = { it.id }) { profile ->
                val checked = profile.id == active.id
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { viewModel.select(profile.id) },
                    shapes = ToggleButtonShapes(ToggleButtonDefaults.shape, ToggleButtonDefaults.pressedShape, ToggleButtonDefaults.checkedShape),
                    modifier = Modifier.height(44.dp),
                ) {
                    if (checked) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(profile.name)
                }
            }
            item(key = "_new") {
                OutlinedButton(onClick = { viewModel.createCustom(active) }, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(shade.container),
        ) {
            FanCurveEditor(
                profile = draft,
                editable = !draft.isBuiltin,
                liveTemp = stats.fanTemp,
                unit = content.unit,
                livePct = stats.fanPercent,
                onChange = { draft = it },
                onCommit = viewModel::save,
            )
            if (draft.isAuto) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("System control", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text("LineageOS thermal zones drive the fan", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (draft.isBuiltin) Format.fanDescription(draft) else "Drag the points to shape the curve",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!draft.isBuiltin) {
                TextButton(onClick = { viewModel.delete(draft.id) }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                Spacer(Modifier.width(6.dp))
            }
            if (!draft.isAuto) {
                FilledTonalButton(onClick = { viewModel.createCustom(draft) }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (draft.isBuiltin) "Customize" else "Duplicate")
                }
            }
        }
    }
}
