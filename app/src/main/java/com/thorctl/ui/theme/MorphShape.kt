package com.thorctl.ui.theme

import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/**
 * One frame of a morph between two Material 3 Expressive shapes (MaterialShapes polygons are
 * normalized to the unit square). Create a new instance per animated progress value; the Morph
 * itself is remembered so only the path is rebuilt.
 */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress.coerceIn(0f, 1f), Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is MorphShape && other.morph === morph && other.progress == progress
    override fun hashCode(): Int = 31 * morph.hashCode() + progress.hashCode()
}
