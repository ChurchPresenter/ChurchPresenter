@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Same width for every line" reaching song lyrics — issue #643.
 *
 * Lyrics are the one block in the app whose lines are **separate `Text`s**: they are laid out a line
 * at a time so each can carry its own alignment, chords and look-ahead styling, and
 * [TextBlockBackdrop] is what makes those several composables read as one block. Every line
 * therefore arrives at the painter as a layout of exactly one line — and a line asked on its own
 * what the widest line is answers "me". The setting could not do anything at all there, however it
 * was set, while working perfectly everywhere text is one `Text`. That is why it shipped: every test
 * of it went through [BackdropText].
 *
 * Measured from the bands' **edges** rather than by counting pixels, the same way
 * [BackdropBandShapeTest] does it: the glyphs are drawn over the band in another colour, so a count
 * is the band minus whatever sits on that line, which differs per line even when the bands match.
 */
class TextBlockBackdropUniformWidthTest {

    /** Deliberately ragged: three lines no two of which are the same width. */
    private val lines = listOf("Amazing grace how sweet the sound", "That saved", "a wretch like me")

    private fun band(uniform: Boolean) = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#FF0000",
        lineBackgroundOpacity = 100,
        // Grown sideways and pulled in vertically so neither edge of a band can land on a glyph,
        // and so consecutive lines' bands do not touch and merge into one measurement.
        lineBackgroundWidth = 8,
        lineBackgroundHeight = -6,
        lineBackgroundUniformWidth = uniform,
    )

    private val stage = Modifier.testTag("shot").size(420.dp).background(Color.Black)

    private fun ComposeUiTest.show(uniform: Boolean) {
        setContent {
            MaterialTheme {
                val block = rememberTextBlockBackdrop(band(uniform))
                Column(modifier = stage.then(block.containerModifier)) {
                    lines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth().then(block.lineModifier(index)),
                            style = TextStyle(fontSize = 22.sp),
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            onTextLayout = { block.onTextLayout(index, it) },
                        )
                    }
                }
            }
        }
    }

    /** How wide each band is, one entry per band, top to bottom. Rows with no red separate them. */
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

    /** The block with a border, which is one shape for the whole verse rather than a band per line. */
    private fun ComposeUiTest.showBordered(uniform: Boolean) {
        setContent {
            MaterialTheme {
                val block = rememberTextBlockBackdrop(
                    band(uniform).copy(
                        border = true,
                        borderColor = "#FF0000",
                        borderOpacity = 100,
                        borderWidth = 4,
                        borderPadding = 10,
                    ),
                )
                Column(modifier = stage.then(block.containerModifier)) {
                    lines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth().then(block.lineModifier(index)),
                            style = TextStyle(fontSize = 22.sp),
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            onTextLayout = { block.onTextLayout(index, it) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a bordered block is one shape however the width setting is set`() {
        // With a border in it the backdrop stops being a band per line and becomes one plate around
        // the verse, drawn from the union of where the lines landed. "Same width for every line" has
        // nothing left to say there -- there is only one shape -- so it must not change the picture.
        var shared = emptyList<Int>()
        var own = emptyList<Int>()
        runComposeUiTest { showBordered(uniform = true); shared = bandWidths() }
        runComposeUiTest { showBordered(uniform = false); own = bandWidths() }

        assertEquals(own, shared, "the block plate is one shape either way")
        assertTrue(own.isNotEmpty(), "and it is drawn at all")
    }

    @Test
    fun `off, each lyric line keeps a band of its own width`() {
        var widths = emptyList<Int>()
        runComposeUiTest { show(uniform = false); widths = bandWidths() }

        assertEquals(lines.size, widths.size, "one band per line, got $widths")
        assertTrue(
            widths.distinct().size > 1,
            "the sample has to be ragged for the other test to mean anything, got $widths",
        )
    }

    @Test
    fun `on, every lyric line's band comes out the same width`() {
        var widths = emptyList<Int>()
        runComposeUiTest { show(uniform = true); widths = bandWidths() }

        assertEquals(lines.size, widths.size, "one band per line, got $widths")
        // Exactly equal, not "close": they are one pair of edges by construction. A one-pixel spread
        // would mean each line was still measuring itself and merely happening to agree.
        assertEquals(1, widths.distinct().size, "every band must share one width, got $widths")
    }

    @Test
    fun `on, the shared band is as wide as the widest line`() {
        var own = emptyList<Int>()
        var shared = emptyList<Int>()
        runComposeUiTest { show(uniform = false); own = bandWidths() }
        runComposeUiTest { show(uniform = true); shared = bandWidths() }

        // Every band, not just the first: the widest line's own band already equals the shared
        // width, so asserting on `shared.first()` alone passes against the bug this test is for.
        assertTrue(
            shared.all { it == own.max() },
            "every band must be the widest line's width, got $shared against $own",
        )
        assertTrue(shared.min() > own.min(), "and the narrow lines grew to meet it, got $shared vs $own")
    }
}
