package com.thorctl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@Composable
fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }
