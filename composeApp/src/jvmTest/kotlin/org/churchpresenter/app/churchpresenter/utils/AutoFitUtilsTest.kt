package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.models.LyricSection
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

    /** Re-measures [text] exactly the way [calculateAutoFitFontSize] does internally. */
    private fun measuredHeight(text: String, fontSize: Int, width: Int): Int =
        text.split("\n").sumOf { line ->
            measurer.measure(
                text = line,
                style = style.copy(fontSize = fontSize.sp),
                constraints = Constraints(maxWidth = width),
                density = Density(1f),
            ).size.height
        }

    private fun section(vararg lines: String, secondary: List<String> = emptyList()) =
        LyricSection(lines = lines.toList(), secondaryLines = secondary)

    // ── calculateAutoFitFontSize ────────────────────────────────────────────────

    @Test
    fun `degenerate input yields the minimum size instead of throwing`() {
        assertEquals(8, calculateAutoFitFontSize(measurer, "", style, 1920, 1080))
        assertEquals(8, calculateAutoFitFontSize(measurer, "   ", style, 1920, 1080))
        assertEquals(8, calculateAutoFitFontSize(measurer, "text", style, 0, 1080))
        assertEquals(8, calculateAutoFitFontSize(measurer, "text", style, 1920, 0))
        assertEquals(8, calculateAutoFitFontSize(measurer, "text", style, -100, -100))
    }

    @Test
    fun `never returns below the minimum even when nothing can fit`() {
        val size = calculateAutoFitFontSize(measurer, "A very long line of lyrics", style, 20, 5)
        assertTrue(size >= 8, "got $size")
    }

    @Test
    fun `the returned size actually fits the box`() {
        val text = "Amazing grace how sweet the sound\nThat saved a wretch like me"
        val w = 1600
        val h = 400
        val size = calculateAutoFitFontSize(measurer, text, style, w, h)
        assertTrue(
            measuredHeight(text, size, w) <= h,
            "auto-fit returned $size but that overflows ${h}px (measured ${measuredHeight(text, size, w)})",
        )
    }

    @Test
    fun `the returned size is close to the largest that fits`() {
        val text = "Amazing grace how sweet the sound"
        val w = 1600
        val h = 400
        val size = calculateAutoFitFontSize(measurer, text, style, w, h)
        // The implementation deliberately backs off by one step, so allow a small margin --
        // but a size far larger than the answer must genuinely overflow.
        assertTrue(
            measuredHeight(text, size + 3, w) > h,
            "auto-fit returned $size, but ${size + 3} also fits -- the search is leaving space unused",
        )
    }

    @Test
    fun `more vertical room never yields a smaller font`() {
        val text = "Amazing grace how sweet the sound"
        var previous = 0
        for (h in listOf(100, 200, 400, 800, 1080)) {
            val size = calculateAutoFitFontSize(measurer, text, style, 1600, h)
            assertTrue(size >= previous, "height $h gave $size, smaller than the previous $previous")
            previous = size
        }
    }

    @Test
    fun `a narrower box never yields a larger font`() {
        val text = "Amazing grace how sweet the sound"
        val wide = calculateAutoFitFontSize(measurer, text, style, 1600, 400)
        val narrow = calculateAutoFitFontSize(measurer, text, style, 600, 400)
        assertTrue(narrow <= wide, "narrow box gave $narrow, larger than the wide box's $wide")
    }

    @Test
    fun `more lines of text yield a smaller font in the same box`() {
        val one = calculateAutoFitFontSize(measurer, "Amazing grace", style, 1600, 400)
        val many = calculateAutoFitFontSize(
            measurer, List(6) { "Amazing grace" }.joinToString("\n"), style, 1600, 400,
        )
        assertTrue(many < one, "6 lines gave $many, not smaller than 1 line's $one")
    }

    // ── calculateAutoFitForAllSections ──────────────────────────────────────────

    @Test
    fun `all-sections fit degrades safely on empty input`() {
        assertEquals(8, calculateAutoFitForAllSections(measurer, emptyList(), style, 1920, 1080))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("", "  ")), style, 1920, 1080))
        assertEquals(8, calculateAutoFitForAllSections(measurer, listOf(section("x")), style, 0, 1080))
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
}
