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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.Screen
import com.thorctl.protocol.model.Stats
import com.thorctl.ui.components.BatteryPill
import com.thorctl.ui.components.DaemonOffline
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
import com.thorctl.ui.state.DaemonUiState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun MainPage(
    entered: Boolean,
    onNavigate: (MenuKey) -> Unit,
    onOpenApp: (() -> Unit)?,
    viewModel: MenuViewModel = metroViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val connected = state is DaemonUiState.Connected
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Header(
            battery,
            connected,
            onTurnOffBottom = viewModel::turnOffBottomScreen,
            onOpenApp,
            Modifier.enter(0, entered)
        )
        Spacer(Modifier.height(14.dp))
        when (val current = state) {
            DaemonUiState.Disconnected -> DaemonOffline(Modifier.enter(1, entered))
            is DaemonUiState.Connected -> Controls(current.content, entered, onNavigate, viewModel)
        }
    }
}

/** Brightness, the four tiles and the telemetry. */
@Composable
private fun Controls(
    content: MenuContent,
    entered: Boolean,
    onNavigate: (MenuKey) -> Unit,
    viewModel: MenuViewModel
) {
    Column {

        QsSlider(
            value = content.brightnessTop,
            onValueChange = { viewModel.setBrightness(Screen.TOP, it, final = false) },
            onValueChangeFinished = { viewModel.setBrightness(Screen.TOP, it, final = true) },
            icon = Icons.Rounded.BrightnessHigh,
            label = "Top screen",
            modifier = Modifier.enter(1, entered),
        )
        Spacer(Modifier.height(6.dp))
        QsSlider(
            value = content.brightnessBottom,
            onValueChange = { viewModel.setBrightness(Screen.BOTTOM, it, final = false) },
            onValueChangeFinished = { viewModel.setBrightness(Screen.BOTTOM, it, final = true) },
            icon = Icons.Rounded.BrightnessLow,
            label = "Bottom screen",
            modifier = Modifier.enter(2, entered),
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QsTile(
                label = "Fan",
                secondary = content.fan.secondary,
                active = content.fan.active,
                icon = { FanGlyph(speed = content.fanPercent / 100f, color = it) },
                onClick = { onNavigate(FanKey) },
                onIconClick = viewModel::cycleFan,
                modifier = Modifier
                    .weight(1f)
                    .enter(3, entered),
            )
            QsTile(
                label = "Controller",
                secondary = content.controller.secondary,
                active = content.controller.active,
                icon = { Icon(Icons.Rounded.SportsEsports, null, tint = it) },
                onClick = { onNavigate(ControllerKey) },
                onIconClick = viewModel::cycleController,
                modifier = Modifier
                    .weight(1f)
                    .enter(4, entered),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QsTile(
                label = "Stick lights",
                secondary = content.lights.secondary,
                active = content.lights.active,
                icon = { LedRingGlyph(content.leds, it) },
                onClick = { onNavigate(LightsKey) },
                onIconClick = viewModel::toggleLights,
                modifier = Modifier
                    .weight(1f)
                    .enter(5, entered),
            )
            QsTile(
                label = "Game mode",
                secondary = content.game.secondary,
                active = content.game.active,
                icon = { Icon(Icons.Rounded.RocketLaunch, null, tint = it) },
                onClick = { onNavigate(GameKey) },
                onIconClick = viewModel::toggleGameMode,
                modifier = Modifier
                    .weight(1f)
                    .enter(6, entered),
            )
        }
        Spacer(Modifier.height(8.dp))
        StatsGrid(content.stats, entered, content.unit)
    }
}

/** Status-bar style header, as at the top of the Android 17 shade. */
@Composable
private fun Header(
    battery: BatteryStatus,
    connected: Boolean,
    onTurnOffBottom: () -> Unit,
    onOpenApp: (() -> Unit)?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val uses24HourClock = DateFormat.is24HourFormat(context)
    val time = DateFormat.format(if (uses24HourClock) "HH:mm" else "h:mm", now).toString()
    val date =
        DateFormat.format(DateFormat.getBestDateTimePattern(LocalConfiguration.current.locales[0], "EEEMMMd"), now)
            .toString()
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)

    Row(modifier
        .fillMaxWidth()
        .height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = textStyle.copy(fontWeight = FontWeight.Medium), color = colors.onSurface)
        Spacer(Modifier.width(10.dp))
        Text(date, style = textStyle, color = colors.onSurface)
        Spacer(Modifier.weight(1f))
        if (!connected) {
            Text(
                "Service offline",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onErrorContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.errorContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        BatteryPill(battery.level, battery.charging)
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onTurnOffBottom,
            enabled = connected,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Rounded.DesktopAccessDisabled,
                "Turn off bottom screen",
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        if (onOpenApp != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onOpenApp, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Settings,
                    "Open Thor Control",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Telemetry as two more rows of the tile grid (wavy rings instead of icon containers). */
@Composable
private fun StatsGrid(stats: Stats, entered: Boolean, unit: TemperatureUnit) {
    val colors = MaterialTheme.colorScheme
    val powerAccent = when {
        stats.charging || stats.plugged -> Color(0xFF63CF79)
        stats.batteryLevel <= 15 -> colors.error
        else -> Color(0xFFF2C46B)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            Icons.Rounded.Memory, "CPU",
            value = Format.percent(stats.cpuLoad),
            detail = Format.cpuDetail(stats, unit),
            progress = stats.cpuLoad, accent = colors.primary,
            modifier = Modifier
                .weight(1f)
                .enter(7, entered),
        )
        StatTile(
            Icons.Rounded.DeveloperBoard, "GPU",
            value = Format.percent(stats.gpuLoad),
            detail = Format.gpuDetail(stats, unit),
            progress = stats.gpuLoad, accent = colors.tertiary,
            modifier = Modifier
                .weight(1f)
                .enter(8, entered),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            Icons.Rounded.Storage, "Memory",
            value = Format.memoryValue(stats),
            detail = Format.memoryDetail(stats),
            progress = stats.ramUsedFraction,
            accent = colors.secondary,
            modifier = Modifier
                .weight(1f)
                .enter(9, entered),
        )
        StatTile(
            Icons.Rounded.Bolt, "Power",
            value = Format.powerValue(stats),
            detail = Format.powerDetail(stats, unit),
            progress = stats.batteryLevel / 100f, accent = powerAccent,
            modifier = Modifier
                .weight(1f)
                .enter(10, entered),
        )
    }
}
