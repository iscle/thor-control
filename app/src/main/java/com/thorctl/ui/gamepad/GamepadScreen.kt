package com.thorctl.ui.gamepad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.Usb
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
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.ui.components.ConnectedChoice
import com.thorctl.ui.components.DetailHeader
import com.thorctl.ui.components.NavTile
import com.thorctl.ui.components.PagePadding
import com.thorctl.ui.components.StatusTile
import com.thorctl.ui.components.SwitchTile
import com.thorctl.ui.format.Format
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.map

@Composable
fun GamepadScreen(onBack: () -> Unit, viewModel: GamepadViewModel = metroViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val g = ui.gamepad
    val enabled = ui.enabled
    val bt = g.isBluetooth

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding)) {
        DetailHeader("Gamepad mode", onBack) {
            Switch(
                checked = g.on,
                onCheckedChange = viewModel::setOn,
                enabled = enabled,
                thumbContent = {
                    Icon(if (g.on) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize))
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Play on a Mac, PC, console or phone with the Thor's controller. It shows up there as a Stadia Controller, a gamepad they already know: no drivers or apps needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StatusTile(if (bt) Icons.Rounded.Bluetooth else Icons.Rounded.Usb, Format.gamepadSummary(g), Format.gamepadHint(g), active = g.on && g.connected)

        Spacer(Modifier.height(16.dp))
        Text("Connection", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        ConnectedChoice(listOf("USB cable", "Bluetooth"), selected = if (bt) 1 else 0, enabled = enabled) {
            viewModel.setTransport(if (it == 1) GamepadTransport.BLUETOOTH else GamepadTransport.USB)
        }
        if (bt) {
            Spacer(Modifier.height(8.dp))
            NavTile(
                Icons.Rounded.BluetoothSearching,
                "Pair a new device",
                "Makes the Thor visible for two minutes. Tap Allow on the top screen",
                active = false,
                enabled = enabled && g.on,
                onClick = viewModel::pairNewDevice,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("While connected", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        SwitchTile(
            Icons.Rounded.DesktopAccessDisabled, "Darken both screens",
            "Top screen off, bottom screen black: next to no power and no burn-in. AYN or power brings them back",
            g.screensOff, enabled,
            onChange = viewModel::setScreensOff,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "While gamepad mode is on, the controller only reaches the other device; AYN shortcuts still work here. " +
                "Macs list it under Game Controllers. Linux, SteamOS and Android map every button, and Steam knows it everywhere. Paired before? Remove the Thor on the other device and pair again.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}
