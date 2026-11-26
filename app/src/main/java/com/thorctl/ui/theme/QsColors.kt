package com.thorctl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors of the Android 17 shade: everything sits on a heavily blurred, tinted backdrop, so
 * containers are translucent darker layers rather than opaque cards.
 */
@Immutable
data class QsColors(
    val scrim: Color,
    val container: Color,
    val containerPressed: Color,
    val iconActive: Color,
    val onIconActive: Color,
    val iconInactive: Color,
    val onIconInactive: Color,
    val text: Color,
    val textSecondary: Color,
) {
    companion object {
        fun of(scheme: ColorScheme, dark: Boolean): QsColors = with(scheme) {
            QsColors(
                scrim = if (dark) Color(0xFF000000).copy(alpha = 0.40f) else Color(0xFFFFFFFF).copy(alpha = 0.35f),
                container = if (dark) surfaceContainerLowest.copy(alpha = 0.58f) else surfaceContainerLowest.copy(alpha = 0.62f),
                containerPressed = if (dark) surfaceContainerLow.copy(alpha = 0.75f) else surfaceContainerHigh.copy(alpha = 0.75f),
                iconActive = primary,
                onIconActive = onPrimary,
                iconInactive = surfaceContainerHighest.copy(alpha = 0.85f),
                onIconInactive = onSurface,
                text = onSurface,
                textSecondary = onSurfaceVariant,
            )
        }
    }
}
