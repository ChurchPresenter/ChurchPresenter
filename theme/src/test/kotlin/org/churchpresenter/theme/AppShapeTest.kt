package org.churchpresenter.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [AppShape] and the [SquircleShape] it draws: every corner in the app goes through them, so what
 * they promise -- one scale for how rounded, pills that stay pills, and a corner that eases into its
 * edges rather than meeting them at a kink -- is pinned here rather than looked for in screenshots.
 *
 * Outlines are built at density 1, so a dp is a pixel and the numbers read directly.
 */
class AppShapeTest {

    private val density = Density(1f)
    private val card = Size(200f, 100f)

    private fun SquircleShape.path(size: Size, direction: LayoutDirection = LayoutDirection.Ltr): Path =
        assertIs<Outline.Generic>(createOutline(size, direction, density)).path

    private fun Path.covers(x: Float, y: Float) = asSkiaPath().contains(x, y)

    @Test
    fun `a radius in dp is drawn at the app's corner scale`() {
        assertEquals(
            SquircleShape(CornerSize(10.dp * CORNER_SCALE), CornerSize(10.dp * CORNER_SCALE),
                CornerSize(10.dp * CORNER_SCALE), CornerSize(10.dp * CORNER_SCALE)),
            AppShape(10.dp),
        )
        assertEquals(
            SquircleShape(
                CornerSize(8.dp * CORNER_SCALE), CornerSize(0.dp), CornerSize(0.dp), CornerSize(4.dp * CORNER_SCALE),
            ),
            AppShape(topStart = 8.dp, bottomStart = 4.dp),
        )
    }

    @Test
    fun `a percent corner is not scaled, so fifty percent is still a pill`() {
        assertEquals(AppShape(CornerSize(50)), AppShape(50))
    }

    @Test
    fun `no rounding at all is a plain rectangle`() {
        assertIs<Outline.Rectangle>(AppShape(0.dp).createOutline(card, LayoutDirection.Ltr, density))
    }

    @Test
    fun `the outline reaches every edge and cuts every corner`() {
        val path = AppShape(20.dp).path(card)
        val bounds = path.getBounds()

        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(card.width, bounds.right, 0.01f)
        assertEquals(card.height, bounds.bottom, 0.01f)
        assertTrue(path.covers(card.width / 2, card.height / 2), "the middle is inside")
        val right = card.width - 0.5f
        val bottom = card.height - 0.5f
        listOf(0.5f to 0.5f, right to 0.5f, right to bottom, 0.5f to bottom)
            .forEach { (x, y) -> assertFalse(path.covers(x, y), "the corner at ($x, $y) is cut away") }
    }

    @Test
    fun `the curve starts further along the edge than a circular corner of the same radius`() {
        val radius = 20f
        val squircle = SquircleShape(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius))
            .path(card)
        val circular = assertIs<Outline.Rounded>(
            RoundedCornerShape(radius).createOutline(card, LayoutDirection.Ltr, density),
        )
        // 17px along the top edge a 20px circle has dropped 0.23px from the edge and the squircle,
        // whose curve began earlier, 0.46px. A point between the two is inside one and not the other.
        val x = 17f
        val y = 0.34f
        assertTrue(circular.roundRect.contains(Offset(x, y)), "the circular corner has barely begun here")
        assertFalse(squircle.covers(x, y), "the squircle has already curved further in")
    }

    @Test
    fun `a corner bigger than its side gives up its smoothing and stays a clean pill`() {
        val pill = AppShape(50).path(Size(120f, 40f))

        assertTrue(pill.covers(60f, 0.5f), "the flat top between the ends is kept")
        assertTrue(pill.covers(20f, 20f), "the end cap's centre is inside")
        assertFalse(pill.covers(1f, 1f), "and its corner is not")
    }

    @Test
    fun `right to left mirrors which physical corner is the start one`() {
        val topStartOnly = AppShape(topStart = 30.dp)
        val ltr = topStartOnly.path(card, LayoutDirection.Ltr)
        val rtl = topStartOnly.path(card, LayoutDirection.Rtl)

        assertFalse(ltr.covers(1f, 1f), "left to right rounds the top left")
        assertTrue(ltr.covers(card.width - 1f, 1f), "and leaves the top right square")
        assertTrue(rtl.covers(1f, 1f), "right to left leaves the top left square")
        assertFalse(rtl.covers(card.width - 1f, 1f), "and rounds the top right")
    }

    @Test
    fun `copy changes only the corners it is given, and shapes compare by their corners`() {
        val shape = AppShape(10.dp)
        val copied = shape.copy(bottomEnd = CornerSize(0.dp))

        assertIs<SquircleShape>(copied)
        assertEquals(shape.topStart, copied.topStart)
        assertEquals(CornerSize(0.dp), copied.bottomEnd)
        assertEquals(AppShape(10.dp), shape)
        assertEquals(AppShape(10.dp).hashCode(), shape.hashCode())
        assertNotEquals<Any>(shape, copied)
        assertNotEquals<Any>(shape, RoundedCornerShape(10.dp * CORNER_SCALE))
    }
}
