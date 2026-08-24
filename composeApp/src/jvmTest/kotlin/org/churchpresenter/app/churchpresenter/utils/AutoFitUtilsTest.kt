package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Auto-fit decides the font size for every word the congregation ever sees, so the property that
 * matters is not "returns 42" (that would pin us to a specific font metric on a specific OS) but
 * "whatever it returns actually fits, and is close to the largest size that does". These tests
 * therefore re-measure the result rather than hard-coding pixel numbers -- keeping them stable
 * across the three platforms this app ships on.
 */
class AutoFitUtilsTest {

    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )
    private val style = TextStyle(fontSize = 100.sp)

    private fun section(vararg lines: String, secondary: List<String> = emptyList()) =
        LyricSection(lines = lines.toList(), secondaryLines = secondary)

    // ── calculateAutoFitForAllSections ──────────────────────────────────────────

    @Test
    fun `all-sections fit degrades safely on empty input`() {
        assertEquals(8, calculateAutoFitForAllSections(measurer, emptyList(), style, 1920, 1080))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("", "  ")), style, 1920, 1080))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("x")), style, 0, 1080))
    }

    @Test
    fun `all-sections fit degrades safely on a box with no height`() {
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("x")), style, 1920, 0))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("x")), style, 1920, -10))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("x")), style, -10, 1080))
    }

    @Test
    fun `the whole song is sized by its most demanding section`() {
        val shortOnly = listOf(section("Short line"))
        val withLongSection = listOf(
            section("Short line"),
            section("A considerably longer line of lyrics that needs far more horizontal room"),
        )
        val a = calculateAutoFitForAllSections(measurer, shortOnly, style, 1600, 900)
        val b = calculateAutoFitForAllSections(measurer, withLongSection, style, 1600, 900)
        assertTrue(b < a, "adding a longer section did not shrink the fit ($b vs $a)")
    }

    @Test
    fun `secondary bilingual lines constrain the fit too`() {
        val primaryOnly = listOf(section("Short line"))
        val withLongSecondary = listOf(
            section(
                "Short line",
                secondary = listOf("A considerably longer secondary translation line needing more room"),
            ),
        )
        val a = calculateAutoFitForAllSections(measurer, primaryOnly, style, 1600, 900)
        val b = calculateAutoFitForAllSections(measurer, withLongSecondary, style, 1600, 900)
        assertTrue(b < a, "a long secondary line was ignored by the fit ($b vs $a)")
    }

    @Test
    fun `reserved height shrinks the available box`() {
        val sections = listOf(section("Amazing grace", "how sweet the sound"))
        val full = calculateAutoFitForAllSections(measurer, sections, style, 1600, 900)
        val reserved = calculateAutoFitForAllSections(measurer, sections, style, 1600, 900, reservedHeight = 600)
        assertTrue(reserved < full, "reservedHeight was ignored ($reserved vs $full)")
    }

    @Test
    fun `the end-of-song indicator reserves room on the last section`() {
        // A box tight enough that the extra indicator line has to cost something.
        val sections = listOf(section("Amazing grace"), section("How sweet the sound"))
        val without = calculateAutoFitForAllSections(measurer, sections, style, 1600, 120)
        val with = calculateAutoFitForAllSections(
            measurer, sections, style, 1600, 120, includeEndIndicator = true,
        )
        assertTrue(with <= without, "indicator made the font larger ($with vs $without)")
    }

    @Test
    fun `no line wraps at the chosen size`() {
        val line = "A considerably longer line of lyrics that needs plenty of horizontal room"
        val w = 1200
        val size = calculateAutoFitForAllSections(measurer, listOf(section(line)), style, w, 900)
        val measured = measurer.measure(
            text = line,
            style = style.copy(fontSize = size.sp),
            constraints = Constraints(),
            density = Density(1f),
        )
        assertTrue(measured.size.width <= w, "line is ${measured.size.width}px wide at size $size, box is ${w}px")
        assertEquals(1, measured.lineCount, "line wrapped, but this fit is supposed to prevent wrapping")
    }

    // ── calculateChordChartFontSize ─────────────────────────────────────────────
    //
    // A chart stacks a chord row above every row of words, so its height is not the height of the
    // same text measured plainly. Rather than re-implement that model here (which would only assert
    // the test's copy of it), these pin the invariants the zone actually depends on: the result
    // never exceeds the cap, never drops below the floor, and always moves the right way when the
    // box shrinks or the content grows.

    private val chordLines = listOf(
        "[G]Amazing [C]grace how [D]sweet the sound",
        "[G]That saved a [Em]wretch like [D]me",
    )

    private fun chartSize(
        lines: List<String> = chordLines,
        width: Int = 1600,
        height: Int = 900,
        maxFontSize: Int = 60,
        hasInfoLine: Boolean = false,
    ) = calculateChordChartFontSize(measurer, lines, style, width, height, maxFontSize, hasInfoLine)

    @Test
    fun `a chart with nothing to draw or nowhere to draw it keeps the requested size`() {
        // Unlike the lyric fitter this returns the cap, not the floor: there is nothing to shrink
        // to fit, and handing back the minimum would render an empty zone in 8pt for no reason.
        assertEquals(40, chartSize(lines = emptyList(), maxFontSize = 40))
        assertEquals(40, chartSize(width = 0, maxFontSize = 40))
        assertEquals(40, chartSize(height = 0, maxFontSize = 40))
        assertEquals(40, chartSize(width = -10, height = -10, maxFontSize = 40))
    }

    @Test
    fun `a chart with room to spare is drawn at the size asked for`() {
        assertEquals(30, chartSize(height = 4000, width = 4000, maxFontSize = 30))
    }

    @Test
    fun `a chart never comes back larger than the cap or smaller than the floor`() {
        val tight = chartSize(width = 60, height = 30, maxFontSize = 60)

        assertTrue(tight >= 8, "the floor must hold even when nothing fits; got $tight")
        assertTrue(tight <= 60, "the cap must hold; got $tight")
    }

    @Test
    fun `a shorter zone cannot produce a larger chart`() {
        val roomy = chartSize(height = 900)
        val cramped = chartSize(height = 200)

        assertTrue(cramped <= roomy, "shrinking the zone grew the chart: $roomy -> $cramped")
    }

    @Test
    fun `more lines cannot produce a larger chart`() {
        val two = chartSize(lines = chordLines, height = 300)
        val eight = chartSize(lines = List(4) { chordLines }.flatten(), height = 300)

        assertTrue(eight <= two, "adding lines grew the chart: $two -> $eight")
    }

    /**
     * Zone heights swept rather than one height picked, for the two tests below.
     *
     * Both assert that something *costs* height, which only shows up as a strictly smaller font at a
     * height where that cost straddles a size step. A single height cannot be relied on to be such a
     * height on every platform, because how much a row costs depends on the font metrics — which is
     * why these were originally written as `<=`/`>=` at one height. But an inequality that permits
     * equality everywhere is satisfied by the cost not existing at all: removing the info row from
     * the height model, or collapsing the chord-only branch into the stacked one, left both tests
     * green. Sweeping keeps the assertion platform-independent while still requiring the cost to be
     * real somewhere.
     */
    private val zoneHeights = 100..600 step 20

    @Test
    fun `the info line costs height, so it cannot make the chart bigger`() {
        val pairs = zoneHeights.map { chartSize(height = it, hasInfoLine = true) to chartSize(
            height = it,
            hasInfoLine = false,
        ) }

        assertTrue(pairs.all { (with, without) -> with <= without }, "the info row gained space somewhere: $pairs")
        assertTrue(pairs.any { (with, without) -> with < without }, "the info row never cost anything: $pairs")
    }

    @Test
    fun `a chord-only intro line is one row, not a stacked pair`() {
        // No words under the chords, so the chart writes them along the line — one row at 1.5x rather
        // than a stacked pair at 2.6x, so it must fit at a larger size in the same zone.
        val introOnly = listOf("[G] [C] [D] [Em] [G] [C] [D]")
        val withWords = listOf("[G]Amazing [C]grace how [D]sweet the [Em]sound")
        val pairs = zoneHeights.map { chartSize(
            lines = introOnly,
            height = it,
        ) to chartSize(lines = withWords, height = it) }

        assertTrue(
            pairs.all { (intro, words) -> intro >= words },
            "a chord-only line was tighter than a stacked one: $pairs",
        )
        assertTrue(pairs.any { (intro, words) -> intro > words }, "the two line shapes never differed: $pairs")
    }
}
