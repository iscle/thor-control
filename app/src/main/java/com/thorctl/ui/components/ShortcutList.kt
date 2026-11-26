package com.thorctl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** AYN-button shortcut reference. */
@Composable
fun ShortcutList() {
    val colors = MaterialTheme.colorScheme
    val rows = listOf(
        "AYN + ▲" to "Controller → top screen",
        "AYN + ▼" to "Controller → bottom screen",
        "AYN + ◀ / ▶" to "Controller follows focus",
        "AYN + B" to "Move top app to bottom screen",
        "AYN + X" to "Move bottom app to top screen",
        "AYN + Y" to "Game mode on / off",
        "AYN + L1 / R1" to "Bottom screen dimmer / brighter",
        "AYN + L2 / R2" to "Top screen dimmer / brighter",
        "Hold Home" to "Recent apps",
    )
    Column {
        rows.forEach { (keys, what) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    keys,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(12.dp))
                Text(what, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
