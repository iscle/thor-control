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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thorctl.ui.theme.qsColors

/** A row that opens something (a page, a prompt). */
@Composable
fun NavTile(icon: ImageVector, title: String, desc: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val qs = qsColors()
    val motion = MaterialTheme.motionScheme
    val corner by animateFloatAsState(if (active) 29f else 50f, motion.defaultSpatialSpec(), label = "navCorner")
    val iconBg by animateColorAsState(if (active) qs.iconActive else qs.iconInactive, motion.defaultEffectsSpec(), label = "navBg")
    val iconFg by animateColorAsState(if (active) qs.onIconActive else qs.onIconInactive, motion.defaultEffectsSpec(), label = "navFg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(qs.container)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(CornerSize(corner))).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconFg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = qs.text, maxLines = 1)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = qs.textSecondary, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = qs.textSecondary)
    }
}
