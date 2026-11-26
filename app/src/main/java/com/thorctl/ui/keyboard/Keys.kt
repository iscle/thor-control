package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyPressed
import com.thorctl.ui.keyboard.KeyboardColors.FnFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.KeyShape
import com.thorctl.ui.keyboard.KeyboardMetrics.WORD_STEP
import com.thorctl.ui.keyboard.KeyboardMetrics.LONG_PRESS_MS
import com.thorctl.ui.keyboard.KeyboardLayout.rows
import com.thorctl.ui.keyboard.KeyboardLayout.ACCENTS
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorctl.platform.keyboard.KeyboardUi
import com.thorctl.platform.keyboard.EnterKey as Enter
import com.thorctl.platform.keyboard.KeyboardLayer as Layer
import com.thorctl.ui.components.LocalHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun Keys(state: KeyboardUi, popup: KeyPopup, modifier: Modifier) {
    val layout = rows(state.layer)
    Column(modifier) {
        layout.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEachIndexed { i, k ->
                    // A fresh key per layer and position: no state or handlers carry over.
                    key(state.layer, r, i) {
                        when (k) {
                            is Ch -> CharKey(k, state, popup)
                            is Fn -> FnKey(k, state)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A character key, iPad style: tap types it, swiping down types the small character shown above
 * it (that character grows into place while the letter slides down and fades), holding opens the
 * accent strip. The whole cell is the touch area; the key is drawn inset.
 */
@Composable
private fun RowScope.CharKey(k: Ch, state: KeyboardUi, popup: KeyPopup) {
    val kb = LocalKeyboardActions.current
    val haptics = LocalHaptics.current
    val shifted = state.layer == Layer.LETTERS && (state.shift || state.caps)
    val label = if (shifted) k.c.uppercase() else k.c
    val accents = ACCENTS[k.c].orEmpty().map { if (shifted && it != "ß") it.uppercase() else it }
    val accentsNow by rememberUpdatedState(accents)
    var pressed by remember { mutableStateOf(false) }
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cellRoot by remember { mutableStateOf(Offset.Zero) }
    var face by remember { mutableStateOf(Rect.Zero) }
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        Modifier
            .weight(k.w)
            .fillMaxHeight()
            .onGloballyPositioned { cellRoot = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val acc = accentsNow
                    pressed = true
                    haptics.tick()
                    var mode = CharGesture.PRESS
                    var released = false
                    var past = false
                    val slop = viewConfiguration.touchSlop
                    val swipeDist = (if (face.height > 0f) face.height else size.height.toFloat()) * 0.45f
                    val hold = scope.launch {
                        delay(LONG_PRESS_MS)
                        if (mode == CharGesture.PRESS && acc.isNotEmpty()) {
                            mode = CharGesture.ACCENTS_OPEN
                            popup.show(acc, face, cellRoot.x + down.position.x, gap)
                            haptics.tick()
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            released = true
                            break
                        }
                        val d = ch.position - down.position
                        when (mode) {
                            CharGesture.PRESS -> if (k.alt != null && d.y > slop && d.y > abs(d.x)) {
                                mode = CharGesture.SWIPE
                                hold.cancel()
                            } else if (d.getDistance() > slop * 3) {
                                hold.cancel()
                            }
                            CharGesture.SWIPE -> {
                                val p = (d.y / swipeDist).coerceIn(0f, 1f)
                                scope.launch { swipe.snapTo(p) }
                                if ((p >= 0.5f) != past) {
                                    past = p >= 0.5f
                                    haptics.tick()
                                }
                            }
                            CharGesture.ACCENTS_OPEN -> {
                                val i = popup.indexAt(cellRoot.x + ch.position.x)
                                if (i != popup.selected) {
                                    popup.selected = i
                                    haptics.tick()
                                }
                            }
                        }
                        ch.consume()
                    }
                    hold.cancel()
                    if (released) {
                        when (mode) {
                            CharGesture.PRESS -> kb.type(k.c)
                            CharGesture.SWIPE -> if (past && k.alt != null) kb.typeExact(k.alt) else kb.type(k.c)
                            CharGesture.ACCENTS_OPEN -> popup.options.getOrNull(popup.selected)?.let { kb.typeExact(it) }
                        }
                    }
                    popup.options = emptyList()
                    pressed = false
                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (k.lead > 0f) Spacer(Modifier.weight(k.lead))
            KeyFace(
                pressed = pressed,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onFace = { face = it },
            ) { CharLabels(label, k.alt, swipe) }
            if (k.trail > 0f) Spacer(Modifier.weight(k.trail))
        }
    }
}

/** The character, with the swipe-down character small above it; [swipe] 0..1 swaps them. */
@Composable
private fun CharLabels(label: String, alt: String?, swipe: Animatable<Float, *>) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val h = constraints.maxHeight.toFloat()
        if (alt != null) {
            Text(
                alt,
                color = KeyInk,
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer {
                    val p = swipe.value as Float
                    val s = 0.5f + 0.5f * p
                    scaleX = s
                    scaleY = s
                    translationY = -h * 0.27f * (1f - p)
                    alpha = 0.42f + 0.58f * p
                },
            )
        }
        Text(
            label,
            color = KeyInk,
            fontSize = 24.sp,
            modifier = Modifier.graphicsLayer {
                val p = swipe.value as Float
                translationY = (if (alt != null) h * 0.09f else 0f) + h * 0.55f * p
                alpha = 1f - 0.8f * p
                val s = 1f - 0.15f * p
                scaleX = s
                scaleY = s
            },
        )
    }
}

@Composable
private fun RowScope.FnKey(k: Fn, state: KeyboardUi) {
    val kb = LocalKeyboardActions.current
    val m = Modifier.weight(k.w).fillMaxHeight()
    when (k.kind) {
        FnKind.SHIFT -> FnCell(m, active = state.shift || state.caps, onTap = { kb.shift() }) {
            ShiftGlyph(filled = state.shift || state.caps, underline = state.caps, tint = KeyInk)
        }
        FnKind.BACK -> BackspaceKey(m)
        FnKind.SPACE -> FnCell(m, fn = false, onTap = { kb.space() }) {}
        FnKind.ENTER -> FnCell(m, onTap = { kb.enter() }) {
            Icon(enterIcon(state.enter), "Enter", tint = KeyInk, modifier = Modifier.size(26.dp))
        }
        FnKind.TO_LETTERS -> LayerKey(m, "ABC", Layer.LETTERS)
        FnKind.TO_SYMBOLS -> LayerKey(m, "?123", Layer.SYMBOLS)
        FnKind.TO_MORE -> LayerKey(m, "=\\<", Layer.MORE)
    }
}

/**
 * Backspace, Gboard style: tap deletes a character, hold repeats, swipe left selects whole words
 * back from the cursor (swipe back right to give them back) and letting go deletes them.
 */
@Composable
private fun BackspaceKey(m: Modifier) {
    val kb = LocalKeyboardActions.current
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wordStep = with(LocalDensity.current) { WORD_STEP.toPx() }
    Box(
        m.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                haptics.tick()
                val slop = viewConfiguration.touchSlop
                var mode = BackspaceGesture.PRESS
                var released = false
                val repeat = scope.launch {
                    delay(420)
                    if (mode == BackspaceGesture.PRESS) {
                        mode = BackspaceGesture.REPEATING
                        while (true) {
                            kb.backspace()
                            delay(55)
                        }
                    }
                }
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) {
                        released = true
                        break
                    }
                    val dx = ch.position.x - down.position.x
                    if (mode == BackspaceGesture.PRESS && dx < -slop) {
                        mode = BackspaceGesture.WORDS
                        repeat.cancel()
                        kb.beginWordDelete()
                    }
                    if (mode == BackspaceGesture.WORDS) {
                        val back = -dx - slop
                        kb.selectWords(if (back <= 0f) 0 else (back / wordStep).toInt() + 1)
                    }
                    ch.consume()
                }
                repeat.cancel()
                when (mode) {
                    BackspaceGesture.PRESS -> if (released) kb.backspace()
                    BackspaceGesture.REPEATING -> {} // the repeats already deleted
                    BackspaceGesture.WORDS -> kb.endWordDelete(commit = released)
                }
                pressed = false
            }
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), fn = true, onFace = {}) {
            Icon(Icons.Rounded.Backspace, "Backspace", tint = KeyInk, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LayerKey(m: Modifier, label: String, to: Layer) {
    val kb = LocalKeyboardActions.current
    FnCell(m, onTap = { kb.layer(to) }) {
        Text(label, color = KeyInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private fun enterIcon(e: Enter): ImageVector = when (e) {
    Enter.RETURN -> Icons.Rounded.KeyboardReturn
    Enter.GO, Enter.NEXT -> Icons.Rounded.ArrowForward
    Enter.SEARCH -> Icons.Rounded.Search
    Enter.SEND -> Icons.Rounded.Send
    Enter.DONE -> Icons.Rounded.Done
}

/**
 * A function key. The whole cell is the touch area. Acts on release with a tick on touch;
 * [repeat] keys act on touch and keep going while held (backspace).
 */
@Composable
private fun FnCell(m: Modifier, active: Boolean = false, repeat: Boolean = false, fn: Boolean = true, onTap: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    Box(
        m.pointerInput(repeat) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    haptics.tick()
                    var job: Job? = null
                    if (repeat) {
                        action()
                        job = scope.launch {
                            delay(420)
                            while (true) {
                                action()
                                delay(55)
                            }
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                },
                onTap = { if (!repeat) action() },
            )
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), active = active, fn = fn, onFace = {}) { content() }
    }
}

/** What a key looks like: white, rounded, soft shadow, inset in its cell. */
@Composable
private fun KeyFace(
    pressed: Boolean,
    modifier: Modifier,
    active: Boolean = false,
    fn: Boolean = false,
    onFace: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    // Like iOS: character keys white (light grey when pressed); function keys darker grey that
    // flash white when pressed; an active shift turns white with a filled arrow.
    val fill = when {
        active -> KeyFill
        pressed -> if (fn) KeyFill else KeyPressed
        fn -> FnFill
        else -> KeyFill
    }
    Box(
        modifier
            .padding(3.dp)
            .onGloballyPositioned { onFace(it.boundsInRoot()) }
            .raised(12.dp, pressed)
            .clip(KeyShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The ⇧ shift arrow: outlined, filled when shifted, underlined for caps lock. */
@Composable
private fun ShiftGlyph(filled: Boolean, underline: Boolean, tint: Color) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.52f)
            lineTo(w * 0.66f, h * 0.52f)
            lineTo(w * 0.66f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.80f)
            lineTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.52f)
            close()
        }
        val stroke = Stroke(width = w * 0.075f, join = StrokeJoin.Round)
        drawPath(p, tint, style = if (filled) Fill else stroke)
        if (filled) drawPath(p, tint, style = stroke)
        if (underline) drawLine(tint, Offset(w * 0.28f, h * 0.94f), Offset(w * 0.72f, h * 0.94f), strokeWidth = w * 0.08f)
    }
}
