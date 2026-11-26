package com.thorctl.ui.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.thorctl.platform.keyboard.KeyboardUi
import com.thorctl.ui.keyboard.KeyboardColors.KeyboardBottom
import com.thorctl.ui.keyboard.KeyboardColors.KeyboardTop
import com.thorctl.ui.recents.StrongInOut
import kotlinx.coroutines.flow.StateFlow

/**
 * The keyboard on the bottom screen: the lower half is a staggered QWERTY keyboard, the upper half
 * a touchpad that moves the cursor one character per step (with a tick for every character), with
 * Select / Copy / Paste above it and a preview of the text around the cursor on it.
 */
@Composable
fun KeyboardPanel(uiState: StateFlow<KeyboardUi>, visible: StateFlow<Boolean>) {
    val state by uiState.collectAsState()
    val shown by visible.collectAsState()
    // Starts hidden, so even a freshly created window slides in. The whole panel slides up from
    // below the screen and back down, on the same strong in-out curve as the recents screen.
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = shown
    AnimatedVisibility(
        visibleState = transition,
        enter = slideInVertically(tween(330, easing = StrongInOut)) { it },
        exit = slideOutVertically(tween(290, easing = StrongInOut)) { it },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(KeyboardTop, KeyboardBottom)))
        ) {
            val keysHeight = maxHeight * 0.5f
            val popup = remember { KeyPopup() }
            popup.panelWidth = constraints.maxWidth.toFloat()
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                ) {
                    ToolRow(state)
                    Spacer(Modifier.height(10.dp))
                    Touchpad(state, Modifier
                        .fillMaxWidth()
                        .weight(1f))
                }
                // Keys draw 3 dp inside their cells; the cells themselves touch, so there is no
                // dead space between keys (and only 2 dp at the screen edges).
                Keys(
                    state,
                    popup,
                    Modifier
                        .fillMaxWidth()
                        .height(keysHeight)
                        .padding(start = 2.dp, end = 2.dp, bottom = 3.dp)
                )
            }
            AccentPopup(popup)
        }
    }
}

// ---- Select / Copy / Paste ----------------------------------------------------------------------

@Composable
private fun ToolRow(state: KeyboardUi) {
    val actions = LocalKeyboardActions.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill(Icons.Rounded.HighlightAlt, "Select", active = state.selecting) {
            actions.toggleSelect()
        }
        Spacer(Modifier.width(10.dp))
        Pill(Icons.Rounded.ContentCopy, "Copy") { actions.copy() }
        Spacer(Modifier.width(10.dp))
        Pill(Icons.Rounded.ContentPaste, "Paste") { actions.paste() }
        Spacer(Modifier.weight(1f))
        HideButton()
    }
}
