package com.thorctl.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thorctl.platform.overlay.OverlayLayer
import com.thorctl.ui.components.ShortcutList
import com.thorctl.ui.navigation.MenuPanel
import com.thorctl.ui.theme.qsColors
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun StatusScreen(onOpenControllerSettings: () -> Unit, viewModel: StatusViewModel = metroViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxSize()
            .background(qsColors().scrim)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.widthIn(max = 300.dp).fillMaxHeight()) {
            Text("Thor Control", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium), color = cs.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Press the AYN button to open the menu on the bottom screen. Hold it with a button for shortcuts.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            ShortcutList()
            Spacer(Modifier.height(16.dp))
            StatusRow("Root service", if (ui.connected) "Running" else "Not running", ui.connected)
            StatusRow("Menu layer", if (ui.accessibilityBound) "Accessibility overlay" else "App overlay (fallback)", ui.accessibilityBound)
            StatusRow("System app", if (ui.systemApp) "Yes" else "No – install the Magisk module", ui.systemApp)
            StatusRow("Persistent", if (ui.persistent) "Yes" else "No", ui.persistent)
            ui.menuShownAs?.let { StatusRow("Last shown as", if (it == OverlayLayer.ACCESSIBILITY) "accessibility" else "application", true) }
            Spacer(Modifier.weight(1f))
            Button(onClick = viewModel::showMenu, modifier = Modifier.fillMaxWidth()) {
                Text("Show menu on bottom screen")
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onOpenControllerSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Controller calibration & mapping")
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .background(qsColors().scrim),
        ) {
            MenuPanel(entered = true, onOpenApp = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Color(0xFF7BD88F) else cs.error))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        }
    }
}
