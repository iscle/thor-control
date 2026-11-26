package com.thorctl.ui.menu

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.BrightnessCurve
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.Screen
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.components.BatteryPill
import com.thorctl.ui.components.FanGlyph
import com.thorctl.ui.components.LedRingGlyph
import com.thorctl.ui.components.QsSlider
import com.thorctl.ui.components.QsTile
import com.thorctl.ui.components.StatTile
import com.thorctl.ui.components.enter
import com.thorctl.ui.format.Format
import com.thorctl.ui.navigation.ControllerKey
import com.thorctl.ui.navigation.FanKey
import com.thorctl.ui.navigation.GameKey
import com.thorctl.ui.navigation.LightsKey
import com.thorctl.ui.navigation.MenuKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

@Composable
fun MainPage(
    entered: Boolean,
    onNavigate: (MenuKey) -> Unit,
    onOpenApp: (() -> Unit)?,
    viewModel: MenuViewModel = metroViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val state = ui.state
    val stats = ui.stats
    val enabled = ui.enabled
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
        Header(battery, ui.connected, enabled, onTurnOffBottom = viewModel::turnOffBottomScreen, onOpenApp, Modifier.enter(0, entered))
        Spacer(Modifier.height(14.dp))

        QsSlider(
            value = BrightnessCurve.toPosition(state?.brightnessTop ?: 0.5f),
            onValueChange = { viewModel.setBrightness(Screen.TOP, it, final = false) },
            onValueChangeFinished = { viewModel.setBrightness(Screen.TOP, it, final = true) },
            icon = Icons.Rounded.BrightnessHigh,
            label = "Top screen",
            enabled = enabled,
            modifier = Modifier.enter(1, entered),
        )
        Spacer(Modifier.height(6.dp))
        QsSlider(
            value = BrightnessCurve.toPosition(state?.brightnessBottom ?: 0.5f),
            onValueChange = { viewModel.setBrightness(Screen.BOTTOM, it, final = false) },
            onValueChangeFinished = { viewModel.setBrightness(Screen.BOTTOM, it, final = true) },
            icon = Icons.Rounded.BrightnessLow,
            label = "Bottom screen",
            enabled = enabled,
            modifier = Modifier.enter(2, entered),
        )
        Spacer(Modifier.height(14.dp))

        val profile = state?.runningProfile
        val fanByGame = state != null && state.fanOverride.isNotEmpty()
        val fanPct = stats?.fanPercent ?: 0
        val game = state?.game
        val leds = state?.leds
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QsTile(
                label = "Fan",
                secondary = when {
                    profile == null -> "Unavailable"
                    fanByGame -> "${profile.name} (game) · $fanPct%"
                    else -> "${profile.name} · $fanPct%"
                },
                active = profile != null && !profile.isAuto,
                icon = { FanGlyph(speed = fanPct / 100f, color = it) },
                onClick = { onNavigate(FanKey) },
                onIconClick = viewModel::cycleFan,
                enabled = enabled,
                modifier = Modifier.weight(1f).enter(3, entered),
            )
            val gamepad = state?.gamepad
            QsTile(
                label = "Controller",
                secondary = when {
                    gamepad?.on != true -> Format.controller(state?.controller)
                    gamepad.connected -> "Gamepad · connected"
                    else -> "Gamepad · waiting"
                },
                active = state != null && (state.controller != ControllerTarget.FREE || state.gamepad.on),
                icon = { Icon(Icons.Rounded.SportsEsports, null, tint = it) },
                onClick = { onNavigate(ControllerKey) },
                onIconClick = viewModel::cycleController,
                enabled = enabled,
                modifier = Modifier.weight(1f).enter(4, entered),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QsTile(
                label = "Stick lights",
                secondary = when {
                    leds == null -> "Unavailable"
                    !leds.enabled -> "Off"
                    else -> leds.mode.wire.replaceFirstChar { it.uppercase() }
                },
                active = leds?.enabled == true,
                icon = { LedRingGlyph(leds, it) },
                onClick = { onNavigate(LightsKey) },
                onIconClick = viewModel::toggleLights,
                enabled = enabled,
                modifier = Modifier.weight(1f).enter(5, entered),
            )
            QsTile(
                label = "Game mode",
                secondary = when {
                    game == null -> "Unavailable"
                    !game.enabled -> "Off"
                    game.pkg.isEmpty() -> "Waiting for a game"
                    !game.boosted -> "${ui.gameLabel} · paused"
                    else -> ui.gameLabel
                },
                active = game?.enabled == true,
                icon = { Icon(Icons.Rounded.RocketLaunch, null, tint = it) },
                onClick = { onNavigate(GameKey) },
                onIconClick = viewModel::toggleGameMode,
                enabled = enabled,
                modifier = Modifier.weight(1f).enter(6, entered),
            )
        }
        Spacer(Modifier.height(8.dp))
        StatsGrid(stats, entered, ui.unit)
    }
}

/** Status-bar style header, as at the top of the Android 17 shade. */
@Composable
private fun Header(
    battery: BatteryStatus,
    connected: Boolean,
    enabled: Boolean,
    onTurnOffBottom: () -> Unit,
    onOpenApp: (() -> Unit)?,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = DateFormat.format(if (is24) "HH:mm" else "h:mm", now).toString()
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"), now).toString()
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)

    Row(modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = textStyle.copy(fontWeight = FontWeight.Medium), color = cs.onSurface)
        Spacer(Modifier.width(10.dp))
        Text(date, style = textStyle, color = cs.onSurface)
        Spacer(Modifier.weight(1f))
        if (!connected) {
            Text(
                "Service offline",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onErrorContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(cs.errorContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        BatteryPill(battery.level, battery.charging)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onTurnOffBottom, enabled = enabled, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.DesktopAccessDisabled, "Turn off bottom screen", tint = cs.onSurface, modifier = Modifier.size(20.dp))
        }
        if (onOpenApp != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onOpenApp, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Settings, "Open Thor Control", tint = cs.onSurface, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Telemetry as two more rows of the tile grid (wavy rings instead of icon containers). */
@Composable
private fun StatsGrid(stats: Stats?, entered: Boolean, unit: TemperatureUnit) {
    val cs = MaterialTheme.colorScheme
    val powerAccent = when {
        stats == null -> cs.primary
        stats.charging || stats.plugged -> Color(0xFF63CF79)
        stats.batteryLevel <= 15 -> cs.error
        else -> Color(0xFFF2C46B)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            Icons.Rounded.Memory, "CPU",
            value = stats?.let { Format.percent(it.cpuLoad) } ?: "–",
            sub = stats?.let { Format.cpuDetail(it, unit) } ?: "",
            progress = stats?.cpuLoad ?: 0f, accent = cs.primary,
            modifier = Modifier.weight(1f).enter(7, entered),
        )
        StatTile(
            Icons.Rounded.DeveloperBoard, "GPU",
            value = stats?.let { Format.percent(it.gpuLoad) } ?: "–",
            sub = stats?.let { Format.gpuDetail(it, unit) } ?: "",
            progress = stats?.gpuLoad ?: 0f, accent = cs.tertiary,
            modifier = Modifier.weight(1f).enter(8, entered),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            Icons.Rounded.Storage, "Memory",
            value = stats?.let { Format.memoryValue(it) } ?: "–",
            sub = stats?.let { Format.memoryDetail(it) } ?: "",
            progress = stats?.ramUsedFraction ?: 0f,
            accent = cs.secondary,
            modifier = Modifier.weight(1f).enter(9, entered),
        )
        StatTile(
            Icons.Rounded.Bolt, "Power",
            value = stats?.let { Format.powerValue(it) } ?: "–",
            sub = stats?.let { Format.powerDetail(it, unit) } ?: "",
            progress = (stats?.batteryLevel ?: 0) / 100f, accent = powerAccent,
            modifier = Modifier.weight(1f).enter(10, entered),
        )
    }
}
