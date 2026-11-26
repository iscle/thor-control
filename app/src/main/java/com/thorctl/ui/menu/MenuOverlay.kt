package com.thorctl.ui.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.thorctl.ui.navigation.MenuPanel
import com.thorctl.ui.theme.qsColors
import kotlinx.coroutines.flow.StateFlow

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Full-screen overlay content. Opening: the window blur and tint ramp up (emphasized
 * decelerate) while the panel rows drop in one after another on expressive springs, like the
 * shade being pulled down. Closing reverses it quickly.
 */
@Composable
fun MenuOverlay(
    visible: StateFlow<Boolean>,
    blur: Boolean,
    onReveal: (Float) -> Unit,
    onExitFinished: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val vis by visible.collectAsState()
    val reveal = remember { Animatable(0f) }
    val exitDone by rememberUpdatedState(onExitFinished)
    val revealCb by rememberUpdatedState(onReveal)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(vis) {
        if (vis) {
            shown = true
            reveal.animateTo(1f, tween(260, easing = EmphasizedDecelerate))
        } else if (shown) {
            reveal.animateTo(0f, tween(170, easing = EmphasizedAccelerate))
            exitDone()
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { reveal.value }.collect { revealCb(it) } }

    val qs = qsColors()
    val scrim = if (blur) qs.scrim else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = reveal.value }
                .background(scrim)
                // Taps on the backdrop are swallowed; the menu closes only with the AYN button.
                .pointerInput(Unit) { detectTapGestures { } },
        )
        MenuPanel(entered = vis, onOpenApp = onOpenApp, modifier = Modifier.fillMaxSize())
    }
}
