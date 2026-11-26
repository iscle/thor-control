package com.thorctl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thorctl.ui.theme.qsColors

/** Selectable row in the QS tile style; the icon container morphs circle -> rounded square. */
@Composable
fun OptionTile(icon: ImageVector, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val shade = qsColors()
    val motion = MaterialTheme.motionScheme
    val corner by animateFloatAsState(if (selected) 29f else 50f, motion.defaultSpatialSpec(), label = "optCorner")
    val iconBackground by animateColorAsState(if (selected) shade.iconActive else shade.iconInactive, motion.defaultEffectsSpec(), label = "optBg")
    val iconForeground by animateColorAsState(if (selected) shade.onIconActive else shade.onIconInactive, motion.defaultEffectsSpec(), label = "optFg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(shade.container)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(CornerSize(corner))).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconForeground) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = shade.text)
            Text(description, style = MaterialTheme.typography.bodySmall, color = shade.textSecondary)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
