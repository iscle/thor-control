package com.thorctl.ui.controller

import com.thorctl.ui.components.DaemonPage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.ui.components.DetailHeader
import com.thorctl.ui.components.NavTile
import com.thorctl.ui.components.OptionTile
import com.thorctl.ui.components.PagePadding
import com.thorctl.ui.components.ShortcutList
import com.thorctl.ui.format.Format
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun ControllerScreen(onBack: () -> Unit, onGamepad: () -> Unit, viewModel: ControllerViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    DaemonPage(title = "Controller input", onBack = onBack, state = state, scrollable = true) { content ->
        val gamepad = content.gamepad
        Spacer(Modifier.height(4.dp))
        NavTile(
            Icons.Rounded.Gamepad,
            "Gamepad mode",
            if (gamepad.on) Format.gamepadSummary(gamepad) else "Play on a PC, console or phone over USB or Bluetooth",
            active = gamepad.on,
            onClick = onGamepad,
        )
        Spacer(Modifier.height(12.dp))
        Text("Choose which screen receives the built-in controller.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OptionTile(Icons.Rounded.TouchApp, "Follow focus", "Goes to whichever screen you touched last", content.target == ControllerTarget.FREE) {
            viewModel.setTarget(ControllerTarget.FREE)
        }
        Spacer(Modifier.height(8.dp))
        OptionTile(Icons.Rounded.VerticalAlignTop, "Top screen", "Always control the app on the top screen", content.target == ControllerTarget.TOP) {
            viewModel.setTarget(ControllerTarget.TOP)
        }
        Spacer(Modifier.height(8.dp))
        OptionTile(Icons.Rounded.VerticalAlignBottom, "Bottom screen", "Always control the app on the bottom screen", content.target == ControllerTarget.BOTTOM) {
            viewModel.setTarget(ControllerTarget.BOTTOM)
        }
        Spacer(Modifier.height(16.dp))
        ShortcutList()
    }
}
