package org.churchpresenter.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [AppShape], which every corner in the app goes through: one scale for how rounded, and pills that
 * stay pills. Pinned here rather than looked for in screenshots.
 *
 * Outlines are built at density 1, so a dp is a pixel and the numbers read directly.
 */
class AppShapeTest {

    private val density = Density(1f)
    private val card = Size(200f, 100f)

    private fun RoundedCornerShape.rounded(direction: LayoutDirection = LayoutDirection.Ltr) =
        assertIs<Outline.Rounded>(createOutline(card, direction, density)).roundRect

    @Test
    fun `a radius in dp is drawn at the app's corner scale`() {
        assertEquals(RoundedCornerShape(10.dp * CORNER_SCALE), AppShape(10.dp))
        assertEquals(
            RoundedCornerShape(topStart = 8.dp * CORNER_SCALE, bottomStart = 4.dp * CORNER_SCALE),
            AppShape(topStart = 8.dp, bottomStart = 4.dp),
        )
    }

    @Test
    fun `the scale makes corners tighter, not rounder`() {
        assertEquals(6f, AppShape(10.dp).rounded().topLeftCornerRadius.x, 0.001f)
    }

    @Test
    fun `a percent corner is not scaled, so fifty percent is still a pill`() {
        assertEquals(RoundedCornerShape(50), AppShape(50))
        assertEquals(card.height / 2, AppShape(50).rounded().topLeftCornerRadius.x, 0.001f)
    }

    @Test
    fun `a corner size is used as given`() {
        assertEquals(RoundedCornerShape(CornerSize(3.dp)), AppShape(CornerSize(3.dp)))
    }

    @Test
    fun `no rounding at all is a plain rectangle`() {
        assertIs<Outline.Rectangle>(AppShape(0.dp).createOutline(card, LayoutDirection.Ltr, density))
    }

    @Test
    fun `right to left mirrors which physical corner is the start one`() {
        val topStartOnly = AppShape(topStart = 30.dp)

        assertEquals(18f, topStartOnly.rounded(LayoutDirection.Ltr).topLeftCornerRadius.x, 0.001f)
        assertEquals(0f, topStartOnly.rounded(LayoutDirection.Ltr).topRightCornerRadius.x, 0.001f)
        assertEquals(0f, topStartOnly.rounded(LayoutDirection.Rtl).topLeftCornerRadius.x, 0.001f)
        assertEquals(18f, topStartOnly.rounded(LayoutDirection.Rtl).topRightCornerRadius.x, 0.001f)
    }
}
