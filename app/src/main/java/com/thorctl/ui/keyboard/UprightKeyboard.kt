package com.thorctl.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** The keys alone, inside the keyboard service's own window (top screen, held upright). */
@Composable
fun UprightKeyboard(viewModel: KeyboardViewModel = metroViewModel()) {
    CompositionLocalProvider(LocalKeyboardActions provides viewModel) {
        InlineKeyboard(viewModel.uiState)
    }
}
