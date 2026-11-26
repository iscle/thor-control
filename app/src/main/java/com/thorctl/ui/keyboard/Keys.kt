package com.thorctl.ui.keyboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.thorctl.ui.components.LocalHaptics
import com.thorctl.ui.keyboard.KeyboardColors.FunctionKeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyFill
import com.thorctl.ui.keyboard.KeyboardColors.KeyInk
import com.thorctl.ui.keyboard.KeyboardColors.KeyPressed
import com.thorctl.ui.keyboard.KeyboardColors.KeyShape
import com.thorctl.ui.keyboard.KeyboardLayout.ACCENTS
import com.thorctl.ui.keyboard.KeyboardLayout.rows
import com.thorctl.ui.keyboard.KeyboardMetrics.LONG_PRESS_MS
import com.thorctl.ui.keyboard.KeyboardMetrics.WORD_STEP
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.thorctl.platform.keyboard.EnterKey as Enter
import com.thorctl.platform.keyboard.KeyboardLayer as Layer

@Composable
internal fun Keys(state: KeyboardUi, popup: KeyPopup, modifier: Modifier) {
    val layout = rows(state.layer)
    Column(modifier) {
        layout.forEachIndexed { rowIndex, row ->
            Row(Modifier
                .fillMaxWidth()
                .weight(1f)) {
                row.forEachIndexed { keyIndex, layoutKey ->
                    // A fresh key per layer and position: no state or handlers carry over.
                    key(state.layer, rowIndex, keyIndex) {
                        when (layoutKey) {
                            is CharacterKey -> CharKey(layoutKey, state, popup)
                            is FunctionKey -> FunctionKeyButton(layoutKey, state)
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
private fun RowScope.CharKey(charKey: CharacterKey, state: KeyboardUi, popup: KeyPopup) {
    val actions = LocalKeyboardActions.current
    val haptics = LocalHaptics.current
    val shifted = state.layer == Layer.LETTERS && (state.shift || state.caps)
    val label = if (shifted) charKey.c.uppercase() else charKey.c
    val accents =
        ACCENTS[charKey.c].orEmpty().map { if (shifted && it != "ß") it.uppercase() else it }
    val accentsNow by rememberUpdatedState(accents)
    var pressed by remember { mutableStateOf(false) }
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var cellRoot by remember { mutableStateOf(Offset.Zero) }
    var face by remember { mutableStateOf(Rect.Zero) }
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        Modifier
            .weight(charKey.width)
            .fillMaxHeight()
            .onGloballyPositioned { cellRoot = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val accentOptions = accentsNow
                    pressed = true
                    haptics.tick()
                    var mode = CharacterGesture.PRESS
                    var released = false
                    var past = false
                    val slop = viewConfiguration.touchSlop
                    val swipeDistance =
                        (if (face.height > 0f) face.height else size.height.toFloat()) * 0.45f
                    val hold = scope.launch {
                        delay(LONG_PRESS_MS)
                        if (mode == CharacterGesture.PRESS && accentOptions.isNotEmpty()) {
                            mode = CharacterGesture.ACCENTS_OPEN
                            popup.show(accentOptions, face, cellRoot.x + down.position.x, gap)
                            haptics.tick()
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        val dragOffset = change.position - down.position
                        when (mode) {
                            CharacterGesture.PRESS -> if (charKey.alternate != null && dragOffset.y > slop && dragOffset.y > abs(
                                    dragOffset.x
                                )
                            ) {
                                mode = CharacterGesture.SWIPE
                                hold.cancel()
                            } else if (dragOffset.getDistance() > slop * 3) {
                                hold.cancel()
                            }

                            CharacterGesture.SWIPE -> {
                                val swipeProgress = (dragOffset.y / swipeDistance).coerceIn(0f, 1f)
                                scope.launch { swipe.snapTo(swipeProgress) }
                                if ((swipeProgress >= 0.5f) != past) {
                                    past = swipeProgress >= 0.5f
                                    haptics.tick()
                                }
                            }

                            CharacterGesture.ACCENTS_OPEN -> {
                                val accentIndex = popup.indexAt(cellRoot.x + change.position.x)
                                if (accentIndex != popup.selected) {
                                    popup.selected = accentIndex
                                    haptics.tick()
                                }
                            }
                        }
                        change.consume()
                    }
                    hold.cancel()
                    if (released) {
                        when (mode) {
                            CharacterGesture.PRESS -> actions.type(charKey.c)
                            CharacterGesture.SWIPE -> if (past && charKey.alternate != null) actions.typeExact(
                                charKey.alternate
                            ) else actions.type(charKey.c)

                            CharacterGesture.ACCENTS_OPEN -> popup.options.getOrNull(popup.selected)
                                ?.let { actions.typeExact(it) }
                        }
                    }
                    popup.options = emptyList()
                    pressed = false
                    scope.launch {
                        swipe.animateTo(
                            0f,
                            spring(dampingRatio = 1f, stiffness = 900f)
                        )
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (charKey.lead > 0f) Spacer(Modifier.weight(charKey.lead))
            KeyFace(
                pressed = pressed,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onFace = { face = it },
            ) { CharLabels(label, charKey.alternate, swipe) }
            if (charKey.trail > 0f) Spacer(Modifier.weight(charKey.trail))
        }
    }
}

/** The character, with the swipe-down character small above it; [swipe] 0..1 swaps them. */
@Composable
private fun CharLabels(label: String, alternate: String?, swipe: Animatable<Float, *>) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val h = constraints.maxHeight.toFloat()
        if (alternate != null) {
            Text(
                alternate,
                color = KeyInk,
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer {
                    val progress = swipe.value as Float
                    val scale = 0.5f + 0.5f * progress
                    scaleX = scale
                    scaleY = scale
                    translationY = -h * 0.27f * (1f - progress)
                    alpha = 0.42f + 0.58f * progress
                },
            )
        }
        Text(
            label,
            color = KeyInk,
            fontSize = 24.sp,
            modifier = Modifier.graphicsLayer {
                val progress = swipe.value as Float
                translationY = (if (alternate != null) h * 0.09f else 0f) + h * 0.55f * progress
                alpha = 1f - 0.8f * progress
                val scale = 1f - 0.15f * progress
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

@Composable
private fun RowScope.FunctionKeyButton(functionKey: FunctionKey, state: KeyboardUi) {
    val actions = LocalKeyboardActions.current
    val cellModifier = Modifier
        .weight(functionKey.width)
        .fillMaxHeight()
    when (functionKey.kind) {
        FunctionKind.SHIFT -> FunctionCell(
            cellModifier,
            active = state.shift || state.caps,
            onTap = { actions.shift() }) {
            ShiftGlyph(filled = state.shift || state.caps, underline = state.caps, tint = KeyInk)
        }

        FunctionKind.BACK -> BackspaceKey(cellModifier)
        FunctionKind.SPACE -> FunctionCell(
            cellModifier,
            isFunctionKey = false,
            onTap = { actions.space() }) {}

        FunctionKind.ENTER -> FunctionCell(cellModifier, onTap = { actions.enter() }) {
            Icon(enterIcon(state.enter), "Enter", tint = KeyInk, modifier = Modifier.size(26.dp))
        }

        FunctionKind.TO_LETTERS -> LayerKey(cellModifier, "ABC", Layer.LETTERS)
        FunctionKind.TO_SYMBOLS -> LayerKey(cellModifier, "?123", Layer.SYMBOLS)
        FunctionKind.TO_MORE -> LayerKey(cellModifier, "=\\<", Layer.MORE)
    }
}

/**
 * Backspace, Gboard style: tap deletes a character, hold repeats, swipe left selects whole words
 * back from the cursor (swipe back right to give them back) and letting go deletes them.
 */
@Composable
private fun BackspaceKey(modifier: Modifier) {
    val actions = LocalKeyboardActions.current
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wordStep = with(LocalDensity.current) { WORD_STEP.toPx() }
    Box(
        modifier.pointerInput(Unit) {
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
                            actions.backspace()
                            delay(55)
                        }
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    val dragX = change.position.x - down.position.x
                    if (mode == BackspaceGesture.PRESS && dragX < -slop) {
                        mode = BackspaceGesture.WORDS
                        repeat.cancel()
                        actions.beginWordDelete()
                    }
                    if (mode == BackspaceGesture.WORDS) {
                        val back = -dragX - slop
                        actions.selectWords(if (back <= 0f) 0 else (back / wordStep).toInt() + 1)
                    }
                    change.consume()
                }
                repeat.cancel()
                when (mode) {
                    BackspaceGesture.PRESS -> if (released) actions.backspace()
                    BackspaceGesture.REPEATING -> {} // the repeats already deleted
                    BackspaceGesture.WORDS -> actions.endWordDelete(commit = released)
                }
                pressed = false
            }
        },
    ) {
        KeyFace(pressed, Modifier.fillMaxSize(), isFunctionKey = true, onFace = {}) {
            Icon(
                Icons.Rounded.Backspace,
                "Backspace",
                tint = KeyInk,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LayerKey(modifier: Modifier, label: String, to: Layer) {
    val actions = LocalKeyboardActions.current
    FunctionCell(modifier, onTap = { actions.layer(to) }) {
        Text(label, color = KeyInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private fun enterIcon(enter: Enter): ImageVector = when (enter) {
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
private fun FunctionCell(
    modifier: Modifier,
    active: Boolean = false,
    repeat: Boolean = false,
    isFunctionKey: Boolean = true,
    onTap: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    Box(
        modifier.pointerInput(repeat) {
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
        KeyFace(
            pressed,
            Modifier.fillMaxSize(),
            active = active,
            isFunctionKey = isFunctionKey,
            onFace = {}) { content() }
    }
}

/** What a key looks like: white, rounded, soft shadow, inset in its cell. */
@Composable
private fun KeyFace(
    pressed: Boolean,
    modifier: Modifier,
    active: Boolean = false,
    isFunctionKey: Boolean = false,
    onFace: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    // Like iOS: character keys white (light grey when pressed); function keys darker grey that
    // flash white when pressed; an active shift turns white with a filled arrow.
    val fill = when {
        active -> KeyFill
        pressed -> if (isFunctionKey) KeyFill else KeyPressed
        isFunctionKey -> FunctionKeyFill
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
        val glyphWidth = size.width
        val h = size.height
        val arrow = Path().apply {
            moveTo(glyphWidth * 0.5f, h * 0.12f)
            lineTo(glyphWidth * 0.88f, h * 0.52f)
            lineTo(glyphWidth * 0.66f, h * 0.52f)
            lineTo(glyphWidth * 0.66f, h * 0.80f)
            lineTo(glyphWidth * 0.34f, h * 0.80f)
            lineTo(glyphWidth * 0.34f, h * 0.52f)
            lineTo(glyphWidth * 0.12f, h * 0.52f)
            close()
        }
        val stroke = Stroke(width = glyphWidth * 0.075f, join = StrokeJoin.Round)
        drawPath(arrow, tint, style = if (filled) Fill else stroke)
        if (filled) drawPath(arrow, tint, style = stroke)
        if (underline) drawLine(
            tint,
            Offset(glyphWidth * 0.28f, h * 0.94f),
            Offset(glyphWidth * 0.72f, h * 0.94f),
            strokeWidth = glyphWidth * 0.08f
        )
    }
}
