package com.thorctl.ui.keyboard

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** The accent strip shown above a held key; positions are in window (root) pixels. */
@Stable
internal class KeyPopup {
    var options by mutableStateOf<List<String>>(emptyList())
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var cellW by mutableFloatStateOf(0f)
    var cellH by mutableFloatStateOf(0f)
    var selected by mutableIntStateOf(0)
    var panelW = 0f

    fun indexAt(rootX: Float): Int = ((rootX - x) / cellW).toInt().coerceIn(0, (options.size - 1).coerceAtLeast(0))

    fun show(opts: List<String>, face: Rect, fingerX: Float, gap: Float) {
        cellW = face.width
        cellH = face.height
        val total = cellW * opts.size
        // Grow to the right of the key, or to the left when that would leave the screen.
        x = if (face.left + total <= panelW - gap) face.left else face.right - total
        x = x.coerceIn(gap, (panelW - total - gap).coerceAtLeast(gap))
        y = (face.top - cellH - gap).coerceAtLeast(0f)
        options = opts
        selected = indexAt(fingerX)
    }
}
