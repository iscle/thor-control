package com.thorctl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Staggered "shade pull" entrance: each row drops in from above with an expressive (slightly
 * bouncy) spring, scaled from the top edge. On exit everything leaves together, quickly.
 */
@Composable
fun Modifier.enter(index: Int, visible: Boolean): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(10L + index * 16L)
            p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 650f))
        } else {
            p.animateTo(0f, tween(110, easing = FastOutLinearInEasing))
        }
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * -28.dp.toPx()
        val s = 0.9f + 0.1f * v
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}
