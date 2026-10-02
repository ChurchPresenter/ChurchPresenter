package org.churchpresenter.sharedui.composables

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockBackingDrawTest {

    private fun draw(bounds: Rect, backdrop: TextBackdrop): ImageBitmap {
        val bitmap = ImageBitmap(100, 100)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(100f, 100f)) {
            drawBlockBacking(bounds, backdrop)
        }
        return bitmap
    }

    private fun ImageBitmap.at(x: Int, y: Int) = toPixelMap()[x, y]

    private val red = "#FF0000"

    @Test
    fun `a fill with no border paints the plate and nothing more`() {
        val image = draw(
            Rect(30f, 30f, 70f, 70f),
            TextBackdrop(lineBackground = true, lineBackgroundColor = red, lineBackgroundOpacity = 100),
        )
        assertEquals(Color.Red, image.at(50, 50))
    }

    @Test
    fun `a border with no fill outlines the plate and leaves its middle empty`() {
        val image = draw(
            Rect(20f, 20f, 80f, 80f),
            TextBackdrop(border = true, borderColor = red, borderOpacity = 100, borderWidth = 4, borderPadding = 0),
        )
        assertTrue(image.at(19, 50).red > 0.9f, "the stroke runs along the plate's edge")
        assertEquals(0f, image.at(50, 50).alpha)
    }

    @Test
    fun `a plate with no size paints nothing`() {
        val flat = TextBackdrop(lineBackground = true, lineBackgroundColor = red, borderPadding = 0)
        val image = draw(Rect(50f, 50f, 50f, 50f), flat)
        assertEquals(0f, image.at(50, 50).alpha)
    }

    @Test
    fun `a border too thick for its plate draws no stroke`() {
        val image = draw(
            Rect(50f, 50f, 51f, 51f),
            TextBackdrop(
                border = true, borderColor = red, borderOpacity = 100, borderWidth = 40, borderPadding = -20,
            ),
        )
        assertEquals(0f, image.at(50, 50).alpha)
    }
}
