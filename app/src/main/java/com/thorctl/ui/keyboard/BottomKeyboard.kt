package com.thorctl.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** The bottom-screen keyboard with its touchpad. */
@Composable
fun BottomKeyboard(viewModel: KeyboardViewModel = metroViewModel()) {
    CompositionLocalProvider(LocalKeyboardActions provides viewModel) {
        KeyboardPanel(ui = viewModel.ui, visible = viewModel.visible)
    }
}
