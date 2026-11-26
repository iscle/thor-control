package com.thorctl.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared header for detail pages: tonal back button (morphs on press) + title. */
@Composable
fun DetailHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium), color = colors.onSurface)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
