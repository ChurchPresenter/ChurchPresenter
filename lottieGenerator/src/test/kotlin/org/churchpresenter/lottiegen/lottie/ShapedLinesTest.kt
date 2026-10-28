package org.churchpresenter.lottiegen.lottie

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import org.churchpresenter.lottiegen.render.GeneratorLottieFonts
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val VERSE =
    "கர்த்தராகிய இயேசுவினாலே நாங்கள் உங்களுக்குக் கொடுத்த கட்டளைகளை அறிந்திருக்கிறீர்களே."

/**
 * Whole-line layout: wrapped by shaped word widths and placed by padding, because Compottie places
 * a whole line by its letters measured one at a time and so puts a Tamil line left of centre.
 */
class ShapedLinesTest {

    /** Every character ten wide, a space five, a hair space one — easy to read off. */
    private val tens = ShapedLines(wordWidth = { it.length * 10f }, spaceWidth = 5f, hairSpaceWidth = 1f)

    @Test
    fun `a line is its words and the spaces between them`() {
        assertEquals(45f, tens.width("aa bb"))
        assertEquals(20f, tens.width("aa"))
        assertEquals(0f, tens.width(""))
    }

    @Test
    fun `words wrap greedily at the box, keeping written line breaks`() {
        assertEquals(listOf("aa bb", "cc dd"), tens.wrap("aa bb cc dd", 50f))
        assertEquals(listOf("aa", "", "bb"), tens.wrap("aa\n\nbb", 50f), "an empty written line stays")
        assertEquals(listOf("aa bb cc dd"), tens.wrap("aa bb cc dd", 0f), "no box, no wrap")
    }

    @Test
    fun `a word wider than the box breaks between whole characters, never inside one`() {
        val word = "கர்த்தர்நல்லவர்"
        // Ten per character as a reader counts them, so the width is the clusters, not the chars.
        val clusters = ShapedLines(wordWidth = { clusterCount(it) * 10f }, spaceWidth = 5f)
        val pieces = clusters.wrap("a $word", 25f)

        assertEquals("a", pieces.first())
        assertEquals(word, pieces.drop(1).joinToString(""))
        pieces.drop(1).forEach { piece ->
            assertTrue(clusterCount(piece) * 10f <= 25f, "'$piece' fits the box")
            val first = piece.codePointAt(0)
            val type = Character.getType(first)
            assertTrue(
                type != Character.NON_SPACING_MARK.toInt() && type != Character.COMBINING_SPACING_MARK.toInt(),
                "'$piece' does not start with a sign cut from its letter",
            )
        }
    }

    @Test
    fun `padding puts a line where its justification would, in spaces then hair spaces`() {
        val hair = ShapedLines.HAIR_SPACE.toString()
        assertEquals(" ".repeat(8) + hair.repeat(2), tens.padding("aa", 104f, 0.5f), "centred: 42 to the left")
        assertEquals(" ".repeat(16), tens.padding("aa", 100f, 1f), "right: 80 to the left")
        assertEquals("", tens.padding("aa", 100f, 0f), "left")
        assertEquals("", tens.padding("aaaaaaaaaaaa", 100f, 0.5f), "a line wider than the box starts at its edge")
        val noHair = ShapedLines(wordWidth = { it.length * 10f }, spaceWidth = 5f)
        assertEquals(" ".repeat(8), noHair.padding("aa", 104f, 0.5f), "no hair space: the spaces alone")
    }

    @Test
    fun `typed text is padded line by line with each line's full padding`() {
        val pads = listOf("  ", "    ")
        assertEquals("  aa\n    b", ShapedLines.applyPadding("aa\nb", pads), "the second line part-typed")
        assertEquals("  a", ShapedLines.applyPadding("a", pads), "the first line part-typed")
        assertEquals("\n    b", ShapedLines.applyPadding("\nb", pads), "an empty line takes no padding")
        assertEquals("  aa\nb", ShapedLines.applyPadding("aa\nb", pads.take(1)), "a line with no pad")
    }

    @Test
    fun `a justification maps to how far across the spare width a line sits`() {
        assertEquals(0f, ShapedLines.alignmentOf(TextJustify.Left))
        assertEquals(1f, ShapedLines.alignmentOf(TextJustify.Right))
        assertEquals(0.5f, ShapedLines.alignmentOf(TextJustify.Center))
    }

    @Test
    fun `in a real Tamil face a padded line lands where it was meant to`() {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val font = GeneratorLottieFonts.load("Noto Sans Tamil", bold = false, style = FontStyle.Normal)
        val style = TextStyle(fontFamily = FontFamily(font), fontSize = 40.sp)
        fun drawn(s: String) = measurer.measure(s, style).multiParagraph.maxIntrinsicWidth
        val shaped = ShapedLines.measuredBy(measurer, style)

        val box = 2400f
        val lines = shaped.wrap(VERSE, box)
        lines.forEach { line ->
            assertTrue(drawn(line) <= box, "'$line' fits the box")
            val pad = shaped.padding(line, box, 0.5f)
            val left = drawn(pad + line) - drawn(line)
            val wanted = (box - drawn(line)) / 2f
            // The padding lands within half a hair space, and these widths are measured at the
            // real size, rounded to whole pixels; a tenth of an em covers both on every platform.
            // Placed letter by letter, the line was off by far more than that.
            assertTrue(abs(left - wanted) < style.fontSize.value / 10f, "'$line' starts at $left, wanted $wanted")
        }
    }

    private fun clusterCount(s: String): Int {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(s) }
        return generateSequence { breaks.next().takeIf { it != BreakIterator.DONE } }.count()
    }
}
