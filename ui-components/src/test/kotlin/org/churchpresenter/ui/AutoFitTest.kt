package org.churchpresenter.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
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
class AutoFitTest {

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

}
