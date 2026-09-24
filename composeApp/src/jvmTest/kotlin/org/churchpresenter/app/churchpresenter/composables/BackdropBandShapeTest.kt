@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of the fill-only band: how far it grows past the text, and how its corners are cut.
 *
 * The band could only ever grow **vertically** and was always square-cornered. Horizontal padding
 * and rounding existed only once a border was switched on, which is a different shape entirely —
 * one plate around the whole block rather than a band per line — so a highlighter that stopped dead
 * at the last glyph could not be widened without changing what was drawn. Issue #600.
 *
 * Asserted in pixels, like `BackdropTextTest`: what a `drawBehind` painted is not something the
 * painter's own state will say.
 */
class BackdropBandShapeTest {

    private fun band(
        width: Int = 0,
        radius: Int = 0,
        height: Int = 0,
        uniform: Boolean = false,
    ) = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#FF0000",
        lineBackgroundOpacity = 100,
        lineBackgroundHeight = height,
        lineBackgroundWidth = width,
        lineBackgroundRadius = radius,
        lineBackgroundUniformWidth = uniform,
    )

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun ComposeUiTest.redPixels(): Int = onNodeWithTag("shot")
        .captureToImage().toPixelMap().count { it.red > 0.4f && it.green < 0.4f && it.blue < 0.4f }

    /**
     * How wide each band is, one entry per band, top to bottom.
     *
     * Measured from the band's **edges** rather than by counting its pixels: the text is drawn over
     * the band in another colour, so a count is the band minus whatever glyphs sit on that line —
     * which differs line by line even when the bands are identical. The leftmost and rightmost red
     * pixels are the band itself, and the tests that use this grow the band past the glyphs so
     * neither edge can land on one.
     *
     * Rows with no red separate one band from the next.
     */
    private fun ComposeUiTest.bandWidths(): List<Int> {
        val pixels = onNodeWithTag("shot").captureToImage().toPixelMap()
        fun isBand(x: Int, y: Int) = pixels[x, y]
            .let { it.red > 0.4f && it.green < 0.4f && it.blue < 0.4f }

        val widths = mutableListOf<Int>()
        var left = Int.MAX_VALUE
        var right = -1
        for (y in 0 until pixels.height) {
            val row = (0 until pixels.width).filter { isBand(it, y) }
            if (row.isEmpty()) {
                if (right >= 0) widths += right - left + 1
                left = Int.MAX_VALUE
                right = -1
            } else {
                left = minOf(left, row.first())
                right = maxOf(right, row.last())
            }
        }
        if (right >= 0) widths += right - left + 1
        return widths
    }

    private val stage = Modifier.testTag("shot").size(240.dp).background(Color.Black)

    private fun ComposeUiTest.show(backdrop: TextBackdrop, text: String = "Hi") {
        setContent {
            MaterialTheme {
                BackdropText(text, backdrop, modifier = stage, style = TextStyle(fontSize = 40.sp))
            }
        }
    }

    // ── Width ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a width offset grows the band past the text`() {
        var plain = 0
        var widened = 0
        runComposeUiTest { show(band()); plain = redPixels() }
        runComposeUiTest { show(band(width = 20)); widened = redPixels() }

        assertTrue(plain > 0, "the band is drawn to begin with")
        assertTrue(widened > plain, "20sp either side must add pixels, got $widened vs $plain")
    }

    @Test
    fun `a negative width pulls the band inside the text`() {
        // The same range the height uses, and negative for the same reason: it tightens a band onto
        // a face whose glyphs leave slack in their own box.
        var plain = 0
        var tightened = 0
        runComposeUiTest { show(band()); plain = redPixels() }
        runComposeUiTest { show(band(width = -6)); tightened = redPixels() }

        assertTrue(tightened < plain, "a negative width must take pixels away, got $tightened vs $plain")
    }

    @Test
    fun `width and height grow different axes`() {
        var wide = 0
        var tall = 0
        runComposeUiTest { show(band(width = 16)); wide = redPixels() }
        runComposeUiTest { show(band(height = 16)); tall = redPixels() }

        // Both add area, and they are not the same area: "Hi" at 40sp is wider than it is tall, so
        // growing the short axis adds more than growing the long one. If these came out equal the
        // two fields would be writing the same thing.
        assertTrue(wide > 0 && tall > 0)
        assertTrue(wide != tall, "the two axes must not be the same measurement, both gave $wide")
    }

    // ── Corners ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a corner radius takes the corners off the band`() {
        var square = 0
        var rounded = 0
        // Grown first, so there is a corner big enough to lose: a radius cannot bite into a band
        // that is only as tall as its line box.
        runComposeUiTest { show(band(width = 24, height = 24)); square = redPixels() }
        runComposeUiTest { show(band(width = 24, height = 24, radius = 24)); rounded = redPixels() }

        assertTrue(rounded < square, "rounding must remove pixels, got $rounded vs $square")
    }

    @Test
    fun `no radius is a square corner, as it always was`() {
        // The default, and the shape every existing document draws today.
        var zero = 0
        var explicitSquare = 0
        runComposeUiTest { show(band(width = 24, height = 24)); zero = redPixels() }
        runComposeUiTest { show(band(width = 24, height = 24, radius = 0)); explicitSquare = redPixels() }

        assertEquals(zero, explicitSquare)
    }

    // ── What a blank line does ──────────────────────────────────────────────────────────────────

    @Test
    fun `a blank line gets no band, however wide the band is told to be`() {
        // The guard measures the *text* before the grow. Measured after, an empty line between two
        // verses would come out twice the grow wide and paint a mark floating in the gap with no
        // text on it.
        var blank = 0
        runComposeUiTest { show(band(width = 30), text = "\n") ; blank = redPixels() }

        assertEquals(0, blank, "an empty line must draw nothing")
    }

    // ── One width for every band, or each line's own ─────────────────────────────────────────────

    private val ragged = "Hi\nHello there"

    /**
     * Wide enough that both band edges clear the glyphs, so [bandWidths] measures the band rather
     * than a glyph, and short enough vertically that consecutive bands do not touch — [bandWidths]
     * separates one band from the next by the empty rows between them, and a line box sits directly
     * on the one below it.
     */
    private fun sized(uniform: Boolean) = band(width = 8, height = -6, uniform = uniform)

    @Test
    fun `off, each band is sized to its own line`() {
        var widths = emptyList<Int>()
        runComposeUiTest { show(sized(uniform = false), text = ragged); widths = bandWidths() }

        assertEquals(2, widths.size, "two lines, two bands")
        assertTrue(widths[0] < widths[1], "the short line's band must be narrower: $widths")
    }

    @Test
    fun `on, every band takes the width of the widest line`() {
        var widths = emptyList<Int>()
        runComposeUiTest { show(sized(uniform = true), text = ragged); widths = bandWidths() }

        assertEquals(2, widths.size, "two lines, two bands")
        assertEquals(widths[0], widths[1], "the bands must share one width: $widths")
    }

    @Test
    fun `the shared width is the widest line's, not the narrowest`() {
        // Sharing the *narrow* line's width would crop the long one, and that is equally "one
        // width" — so the equality above cannot tell the two apart on its own.
        var own = emptyList<Int>()
        var shared = emptyList<Int>()
        runComposeUiTest { show(sized(uniform = false), text = ragged); own = bandWidths() }
        runComposeUiTest { show(sized(uniform = true), text = ragged); shared = bandWidths() }

        assertEquals(own.max(), shared.first(), "every band should be as wide as the widest line")
    }

    @Test
    fun `lines that are already equal are unchanged by sharing a width`() {
        var own = emptyList<Int>()
        var shared = emptyList<Int>()
        runComposeUiTest { show(sized(uniform = false), text = "Hi\nHi"); own = bandWidths() }
        runComposeUiTest { show(sized(uniform = true), text = "Hi\nHi"); shared = bandWidths() }

        assertEquals(own, shared, "there is nothing to widen when the lines already match")
    }

    @Test
    fun `a blank line still gets no band when the width is shared`() {
        // The shared width decides how wide a band is, never *whether* a line gets one — a blank
        // line between two verses would otherwise paint a full-width bar across the gap.
        var widths = emptyList<Int>()
        runComposeUiTest { show(sized(uniform = true), text = "Hi\n\nHi"); widths = bandWidths() }

        assertEquals(2, widths.size, "the blank line must contribute no band: $widths")
    }

    @Test
    fun `a band inverted by its own offsets is not painted`() {
        // Either grow can be negative enough to turn the rectangle inside out.
        var inverted = 0
        runComposeUiTest { show(band(width = -100, height = -100)); inverted = redPixels() }

        assertEquals(0, inverted, "an inverted band is a rectangle drawn back to front")
    }
}
