package com.thorctl.ui.keyboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** The accent strip shown above a held key; positions are in window (root) pixels. */
@Stable
internal class KeyPopup {
    var options by mutableStateOf<List<String>>(emptyList())
    var left by mutableFloatStateOf(0f)
    var top by mutableFloatStateOf(0f)
    var cellWidth by mutableFloatStateOf(0f)
    var cellHeight by mutableFloatStateOf(0f)
    var selected by mutableIntStateOf(0)
    var panelWidth = 0f

    fun indexAt(rootX: Float): Int =
        ((rootX - left) / cellWidth).toInt().coerceIn(0, (options.size - 1).coerceAtLeast(0))

    fun show(accentOptions: List<String>, face: Rect, fingerX: Float, gap: Float) {
        cellWidth = face.width
        cellHeight = face.height
        val total = cellWidth * accentOptions.size
        // Grow to the right of the key, or to the left when that would leave the screen.
        left = if (face.left + total <= panelWidth - gap) face.left else face.right - total
        left = left.coerceIn(gap, (panelWidth - total - gap).coerceAtLeast(gap))
        top = (face.top - cellHeight - gap).coerceAtLeast(0f)
        options = accentOptions
        selected = indexAt(fingerX)
    }
}
