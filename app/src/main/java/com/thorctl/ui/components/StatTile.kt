package com.thorctl.ui.components

import com.thorctl.ui.theme.qsColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Telemetry tile: expressive wavy progress ring with the metric icon inside. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val qs = qsColors()
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 120f), label = "stat")
    Row(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(qs.container)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxSize(),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f),
            )
            Icon(icon, null, Modifier.size(20.dp), tint = accent)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = qs.textSecondary, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                color = qs.text,
                maxLines = 1,
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = qs.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
