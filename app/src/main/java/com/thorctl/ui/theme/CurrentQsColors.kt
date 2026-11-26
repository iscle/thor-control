package com.thorctl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** The shade's colors for the current theme. */
@Composable
fun qsColors(): QsColors = QsColors.of(MaterialTheme.colorScheme, isSystemInDarkTheme())
