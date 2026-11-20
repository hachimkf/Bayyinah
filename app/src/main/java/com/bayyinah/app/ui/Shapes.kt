package com.bayyinah.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
 * Material 3 Expressive shape morph (e.g. Circle → Cookie) at a given [progress]. The morph is
 * built from normalized MaterialShapes polygons (unit square) and scaled to the component size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    private val path = Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.rewind()
        morph.toPath(progress, path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        if (rotation != 0f) {
            matrix.translate(0.5f, 0.5f); matrix.rotateZ(rotation); matrix.translate(-0.5f, -0.5f)
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
