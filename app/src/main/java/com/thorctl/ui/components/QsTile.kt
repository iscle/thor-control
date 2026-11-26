package com.thorctl.ui.components

import com.thorctl.ui.theme.qsColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Android 17 large QS tile: translucent dark pill with a 56dp icon container whose shape tells
 * the state (rounded square + primary = on, circle + neutral = off) and morphs between them
 * with the expressive spatial spring. Tap the icon to toggle, the label to open details.
 */
@Composable
fun QsTile(
    label: String,
    secondary: String?,
    active: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onIconClick: (() -> Unit)? = null,
    toggleable: Boolean = true,
    enabled: Boolean = true,
) {
    val qs = qsColors()
    val motion = MaterialTheme.motionScheme
    val src = remember { MutableInteractionSource() }
    val iconSrc = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val iconPressed by iconSrc.collectIsPressedAsState()
    val tileCorner by animateDpAsState(if (pressed) 14.dp else 24.dp, motion.fastSpatialSpec(), label = "tileCorner")
    val iconCorner by animateFloatAsState(
        when {
            iconPressed -> 22f
            active -> 29f
            else -> 50f
        },
        motion.defaultSpatialSpec(), label = "iconCorner",
    )
    val iconScale by animateFloatAsState(if (iconPressed) 0.88f else 1f, motion.fastSpatialSpec(), label = "iconScale")
    val iconBg by animateColorAsState(
        when {
            active -> qs.iconActive
            toggleable -> qs.iconInactive
            else -> Color.Transparent
        },
        motion.defaultEffectsSpec(), label = "iconBg",
    )
    val iconFg by animateColorAsState(if (active) qs.onIconActive else qs.onIconInactive, motion.defaultEffectsSpec(), label = "iconFg")
    val alpha = if (enabled) 1f else 0.45f

    Row(
        modifier
            .height(72.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(tileCorner))
            .background(if (pressed) qs.containerPressed else qs.container)
            .clickable(enabled = enabled, interactionSource = src, indication = ripple(), onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .clip(RoundedCornerShape(CornerSize(iconCorner)))
                .background(iconBg)
                .clickable(enabled = enabled, interactionSource = iconSrc, indication = ripple(), onClick = onIconClick ?: onClick),
            contentAlignment = Alignment.Center,
        ) { icon(iconFg) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = qs.text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    secondary,
                    color = qs.textSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
