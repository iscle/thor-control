package com.thorctl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.thorctl.platform.haptics.Haptics
import com.thorctl.ui.theme.ThorTheme
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * What every window's content runs in (the activity, the overlays, the keyboard): the theme,
 * ViewModels from the app's graph (`metroViewModel()`) and [LocalHaptics].
 */
@Inject
class ThorContent(private val viewModels: MetroViewModelFactory, private val haptics: Haptics) {
    @Composable
    operator fun invoke(content: @Composable () -> Unit) {
        ThorTheme {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides viewModels,
                LocalHaptics provides haptics,
                content = content
            )
        }
    }
}
