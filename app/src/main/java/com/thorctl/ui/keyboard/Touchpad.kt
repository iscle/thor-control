package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.KeyInkSoft
import com.thorctl.ui.keyboard.KeyboardColors.Accent
import com.thorctl.ui.keyboard.KeyboardColors.AccentDeep
import com.thorctl.ui.keyboard.KeyboardColors.PadFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyShape
import com.thorctl.ui.keyboard.KeyboardMetrics.STEP
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.platform.keyboard.KeyboardUi
import kotlinx.coroutines.delay

@Composable
internal fun Touchpad(state: KeyboardUi, modifier: Modifier) {
    val kb = LocalKeyboardActions.current
    val stepPx = with(LocalDensity.current) { STEP.toPx() }
    var touching by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (touching) 1f else 0f, tween(160), label = "pad")
    Box(
        modifier
            .clip(KeyShape)
            .background(PadFill)
            .background(Accent.copy(alpha = 0.10f * glow))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    touching = true
                    kb.beginSwipe()
                    var acc = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        acc += ch.positionChange().x
                        ch.consume()
                        while (acc >= stepPx) {
                            acc -= stepPx
                            kb.move(1)
                        }
                        while (acc <= -stepPx) {
                            acc += stepPx
                            kb.move(-1)
                        }
                    }
                    kb.endSwipe()
                    touching = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Preview(state, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Swipe, null, tint = KeyInkSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.selecting) "Swipe to select" else "Swipe to move the cursor",
                color = KeyInkSoft,
                fontSize = 13.sp,
            )
        }
        Flash(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
}

/** One line of text around the cursor, the caret (or the selection) kept in the middle. */
@Composable
private fun Preview(state: KeyboardUi, modifier: Modifier) {
    val style = androidx.compose.ui.text.TextStyle(color = KeyInk, fontSize = 22.sp)
    Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.CenterEnd) {
            Text(state.before, style = style, softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true))
        }
        if (state.selected.isEmpty()) {
            Box(Modifier.width(2.dp).height(30.dp).background(AccentDeep))
        } else {
            Text(
                state.selected,
                style = style,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent.copy(alpha = 0.32f)).padding(horizontal = 2.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.CenterStart) {
            Text(state.after, style = style.copy(color = KeyInk.copy(alpha = 0.55f)), softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
        }
    }
}

@Composable
private fun Flash(state: KeyboardUi, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.flashId) {
        if (state.flash == null) return@LaunchedEffect
        text = state.flash
        delay(1300)
        text = null
    }
    val a by animateFloatAsState(if (text != null) 1f else 0f, tween(150), label = "flash")
    if (a > 0f || text != null) {
        Text(
            text ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .graphicsLayer { alpha = a }
                .clip(RoundedCornerShape(14.dp))
                .background(KeyInk.copy(alpha = 0.82f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ---- Keys ---------------------------------------------------------------------------------------
