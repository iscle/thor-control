package com.thorctl.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.thorctl.platform.haptics.Haptics

/** The system's predefined vibrations, for composables deep in a tree (keys, cards). */
val LocalHaptics = staticCompositionLocalOf<Haptics> { error("no Haptics provided: wrap the content in ThorContent") }
