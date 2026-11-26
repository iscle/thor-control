package com.thorctl.ui.keyboard

import com.thorctl.ui.keyboard.KeyboardColors.KbTop
import com.thorctl.ui.keyboard.KeyboardColors.KbBottom
import com.thorctl.ui.keyboard.KeyboardMetrics.INLINE_KEYBOARD_HEIGHT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.thorctl.platform.keyboard.KeyboardUi
import kotlinx.coroutines.flow.StateFlow

/**
 * Held upright (the top screen in portrait), the keyboard is an ordinary on-screen keyboard at the
 * bottom of the top screen, under the app: the same keys, with Paste and hide above them (no
 * touchpad; the text field is right there to tap).
 */
@Composable
fun InlineKeyboard(ui: StateFlow<KeyboardUi>) {
    val kb = LocalKeyboardActions.current
    val state by ui.collectAsState()
    val popup = remember { KeyPopup() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(INLINE_KEYBOARD_HEIGHT)
            .background(Brush.verticalGradient(listOf(KbTop, KbBottom))),
    ) {
        popup.panelW = constraints.maxWidth.toFloat()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(Icons.Rounded.ContentPaste, "Paste") { kb.paste() }
                Spacer(Modifier.weight(1f))
                HideButton()
            }
            Keys(state, popup, Modifier.fillMaxWidth().weight(1f).padding(start = 2.dp, end = 2.dp, bottom = 3.dp))
        }
        AccentPopup(popup)
    }
}
