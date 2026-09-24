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

    private fun band(width: Int = 0, radius: Int = 0, height: Int = 0) = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#FF0000",
        lineBackgroundOpacity = 100,
        lineBackgroundHeight = height,
        lineBackgroundWidth = width,
        lineBackgroundRadius = radius,
    )

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun ComposeUiTest.redPixels(): Int = onNodeWithTag("shot")
        .captureToImage().toPixelMap().count { it.red > 0.4f && it.green < 0.4f && it.blue < 0.4f }

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

    @Test
    fun `a band inverted by its own offsets is not painted`() {
        // Either grow can be negative enough to turn the rectangle inside out.
        var inverted = 0
        runComposeUiTest { show(band(width = -100, height = -100)); inverted = redPixels() }

        assertEquals(0, inverted, "an inverted band is a rectangle drawn back to front")
    }
}
