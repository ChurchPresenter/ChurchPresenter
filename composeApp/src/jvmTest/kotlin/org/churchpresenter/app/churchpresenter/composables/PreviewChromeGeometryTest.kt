package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic the monitor mockup and the resolution field are drawn from.
 *
 * [tvScreenBoxWidthFor] is the inverse of what the composable does internally, and the reason it
 * exists is that doing it at the call site forgot the chrome: 28dp of height that is not screen put
 * the lower-third band in the wrong place in the background preview.
 */
class PreviewChromeGeometryTest {

    private fun screenWidthOf(totalHeight: androidx.compose.ui.unit.Dp, ratio: Float) =
        tvScreenBoxWidthFor(totalHeight, ratio) - TvScreenBoxHorizontalChrome

    @Test
    fun `the width includes the bezel on each side`() {
        val screenHeight = 100.dp
        val total = screenHeight + TvScreenBoxVerticalChrome
        assertEquals(100.dp * (16f / 9f) + TvScreenBoxHorizontalChrome, tvScreenBoxWidthFor(total, 16f / 9f))
    }

    @Test
    fun `the screen inside comes out at the aspect ratio asked for`() {
        val screenHeight = 90.dp
        val total = screenHeight + TvScreenBoxVerticalChrome
        val screenWidth = screenWidthOf(total, 16f / 9f)
        assertEquals(160f, screenWidth.value, 0.01f)
    }

    @Test
    fun `a portrait output produces a narrower box than a landscape one of the same height`() {
        val total = 200.dp
        assertTrue(tvScreenBoxWidthFor(total, 9f / 16f) < tvScreenBoxWidthFor(total, 16f / 9f))
    }

    @Test
    fun `a square screen is as wide as it is tall, plus the chrome`() {
        val total = 50.dp + TvScreenBoxVerticalChrome
        assertEquals(50f, screenWidthOf(total, 1f).value, 0.01f)
    }

    @Test
    fun `a height smaller than the chrome leaves the chrome alone rather than going negative`() {
        val width = tvScreenBoxWidthFor(1.dp, 16f / 9f)
        assertEquals(TvScreenBoxHorizontalChrome, width)
    }

    @Test
    fun `a height of nothing is still the chrome`() {
        assertEquals(TvScreenBoxHorizontalChrome, tvScreenBoxWidthFor(0.dp, 16f / 9f))
    }

    @Test
    fun `the chrome is a real amount, not zero`() {
        assertTrue(TvScreenBoxHorizontalChrome > 0.dp)
        assertTrue(TvScreenBoxVerticalChrome > TvScreenBoxHorizontalChrome, "the stand only adds height")
    }

    @Test
    fun `a taller box has a wider screen at a fixed ratio`() {
        val small = tvScreenBoxWidthFor(100.dp, 16f / 9f)
        val large = tvScreenBoxWidthFor(200.dp, 16f / 9f)
        assertTrue(large > small)
    }

    // ── formatResolution ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a resolution is written with a multiplication sign, not a letter x`() {
        assertEquals("1920×1080", formatResolution(1920, 1080))
    }

    @Test
    fun `a portrait resolution keeps the order it was given in`() {
        assertEquals("1080×1920", formatResolution(1080, 1920))
    }

    @Test
    fun `an unset resolution is written as it stands rather than hidden`() {
        assertEquals("0×0", formatResolution(0, 0))
    }
}
