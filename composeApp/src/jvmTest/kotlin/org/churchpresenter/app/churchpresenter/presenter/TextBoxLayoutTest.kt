package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a text box lies and what size its text is drawn at. The fit is checked by re-measuring what
 * it returns -- it fits, and one point more would not -- rather than pinning a number to one
 * platform's font metrics.
 */
class TextBoxLayoutTest {

    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    // ── The area boxes are measured against ─────────────────────────────────────────────────────

    private val margins = BoxMargins(left = 100f, top = 50f, right = 100f, bottom = 50f)

    @Test
    fun `boxes span the whole output by default`() {
        assertEquals(Rect(0f, 0f, 1920f, 1080f), textBoxArea(1920f, 1080f, TextBoxOptions(), margins))
    }

    @Test
    fun `inside the margins, the margins are taken off each side`() {
        val area = textBoxArea(1920f, 1080f, TextBoxOptions(insideMargins = true), margins)
        assertEquals(Rect(100f, 50f, 1820f, 1030f), area)
    }

    @Test
    fun `margins that would leave nothing fall back to the whole area`() {
        val huge = BoxMargins(left = 1000f, right = 1000f)
        assertEquals(Rect(0f, 0f, 1920f, 1080f), textBoxArea(1920f, 1080f, TextBoxOptions(insideMargins = true), huge))
    }

    @Test
    fun `on a lower third boxes span the band, unless asked for the whole screen`() {
        val band = Rect(0f, 720f, 1920f, 1080f)
        assertEquals(band, textBoxArea(1920f, 1080f, TextBoxOptions(), margins, band))
        val screen = textBoxArea(1920f, 1080f, TextBoxOptions(lowerThirdWholeScreen = true), margins, band)
        assertEquals(Rect(0f, 0f, 1920f, 1080f), screen)
    }

    @Test
    fun `a box lies at its percentages of the area`() {
        val box = TextBox(xPercent = 10f, yPercent = 20f, widthPercent = 50f, heightPercent = 25f)
        assertEquals(Rect(100f, 250f, 600f, 500f), box.rectIn(Rect(0f, 50f, 1000f, 1050f)))
    }

    // ── Overlap and Keep clear ──────────────────────────────────────────────────────────────────

    @Test
    fun `boxes that only touch do not overlap`() {
        val a = Rect(0f, 0f, 100f, 100f)
        assertEquals(emptyList(), a.overlapsOf(listOf(Rect(100f, 0f, 200f, 100f), a)))
        assertEquals(1, a.overlapsOf(listOf(Rect(50f, 50f, 150f, 150f))).size)
    }

    @Test
    fun `keep clear cuts an overlap off the side that loses the least`() {
        val verse = Rect(0f, 100f, 1000f, 600f)
        val reference = Rect(0f, 50f, 1000f, 150f)
        assertEquals(Rect(0f, 150f, 1000f, 600f), verse.clearOf(listOf(reference)))
    }

    @Test
    fun `keep clear never removes a box an overlap covers completely`() {
        val small = Rect(10f, 10f, 20f, 20f)
        assertEquals(small, small.clearOf(listOf(Rect(0f, 0f, 100f, 100f))))
    }

    // ── Fitting ─────────────────────────────────────────────────────────────────────────────────

    private val verse = AnnotatedString("For God so loved the world, that he gave his only begotten Son")
    private val style = TextStyle()

    private fun fits(size: Int, room: IntSize): Boolean {
        val measured = measurer.measure(
            verse,
            style.copy(fontSize = size.sp),
            constraints = Constraints(maxWidth = room.width),
            density = Density(1f),
        ).size
        return measured.height <= room.height && measured.width <= room.width
    }

    @Test
    fun `shrink to fit picks the largest size that fits`() {
        val room = IntSize(600, 200)
        val size = fitInBox(measurer, BoxFitText(verse, style, 200), TextBox(), room)
        assertTrue(size < 200, "a long verse at 200 cannot fit a 600x200 box")
        assertTrue(fits(size, room), "$size fits")
        assertTrue(!fits(size + 1, room), "${size + 1} would not")
    }

    @Test
    fun `short text stays at its size unless the box asks to be filled`() {
        val word = AnnotatedString("Amen")
        val room = IntSize(1000, 600)
        assertEquals(40, fitInBox(measurer, BoxFitText(word, style, 40), TextBox(), room))
        assertTrue(fitInBox(measurer, BoxFitText(word, style, 40), TextBox(fill = true), room) > 40)
    }

    @Test
    fun `cut off and spill over draw at the configured size whatever happens`() {
        val room = IntSize(100, 20)
        val text = BoxFitText(verse, style, 200)
        assertEquals(200, fitInBox(measurer, text, TextBox(overflow = TextBoxOverflow.CUT), room))
        assertEquals(200, fitInBox(measurer, text, TextBox(overflow = TextBoxOverflow.SPILL), room))
    }

    @Test
    fun `an empty box or no text leaves the size alone`() {
        assertEquals(70, fitInBox(measurer, BoxFitText(verse, style, 70), TextBox(), IntSize(0, 100)))
        assertEquals(70, fitInBox(measurer, BoxFitText(AnnotatedString(""), style, 70), TextBox(), IntSize(500, 500)))
    }

    // ── Alignment ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `vertical and horizontal settings become biases`() {
        assertEquals(-1f, TextBox(vertical = Constants.TOP).verticalBias())
        assertEquals(0f, TextBox(vertical = Constants.MIDDLE).verticalBias())
        assertEquals(1f, TextBox(vertical = Constants.BOTTOM).verticalBias())
        assertEquals(-1f, horizontalBias(Constants.LEFT))
        assertEquals(0f, horizontalBias(Constants.CENTER))
        assertEquals(1f, horizontalBias(Constants.RIGHT))
    }
}
