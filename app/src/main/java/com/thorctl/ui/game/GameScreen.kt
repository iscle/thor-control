package com.thorctl.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.ui.components.ConnectedChoice
import com.thorctl.ui.components.DetailHeader
import com.thorctl.ui.components.PagePadding
import com.thorctl.ui.components.StatusTile
import com.thorctl.ui.components.SwitchTile
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun GameScreen(onBack: () -> Unit, viewModel: GameViewModel = metroViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val g = ui.game
    val o = g.options
    val enabled = ui.enabled

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding)) {
        DetailHeader("Game mode", onBack) {
            Switch(
                checked = g.enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = enabled,
                thumbContent = {
                    Icon(if (g.enabled) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Gives the game on the top screen the whole device. Turn it on before or during a game, or press AYN + Y.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val (title, desc) = ui.status
        StatusTile(Icons.Rounded.RocketLaunch, title, desc, active = g.enabled && g.pkg.isNotEmpty())

        Spacer(Modifier.height(16.dp))
        Text("While a game runs", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        SwitchTile(
            Icons.Rounded.CleaningServices, "Close other apps",
            "Closes every other app when the game starts and keeps background apps closed",
            o.closeApps, enabled,
        ) { on -> viewModel.updateOptions { it.copy(closeApps = on) } }
        Spacer(Modifier.height(8.dp))
        SwitchTile(
            Icons.Rounded.DesktopAccessDisabled, "Turn off the bottom screen",
            "While the game is in front, so the launcher there frees its memory. AYN turns it back on",
            o.bottomOff, enabled,
        ) { on -> viewModel.updateOptions { it.copy(bottomOff = on) } }
        Spacer(Modifier.height(8.dp))
        SwitchTile(Icons.Rounded.Memory, "CPU at full speed", "Every core held at its top clock", o.cpu, enabled) { on ->
            viewModel.updateOptions { it.copy(cpu = on) }
        }
        Spacer(Modifier.height(8.dp))
        SwitchTile(Icons.Rounded.DeveloperBoard, "GPU at full speed", "GPU held at its top clock", o.gpu, enabled) { on ->
            viewModel.updateOptions { it.copy(gpu = on) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Fan while gaming", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        val fanIds = GameViewModel.FAN_CHOICES
        ConnectedChoice(listOf("Unchanged", "Sport", "Max"), selected = fanIds.indexOf(o.fan).coerceAtLeast(0), enabled = enabled) { i ->
            viewModel.updateOptions { it.copy(fan = fanIds[i]) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Clocks still drop if the chip runs too hot. Everything goes back to normal while the screen is off and when game mode ends.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}
