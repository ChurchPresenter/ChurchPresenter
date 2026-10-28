package org.churchpresenter.lottiegen.lottie

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import java.text.BreakIterator

/**
 * Line layout for text a player draws as whole lines (see [TextShaping]), measured the way it is
 * drawn: a word at a time, as one shaped run.
 *
 * Compottie still places a whole line by the sum of its letters measured one by one, and a Tamil
 * vowel sign measured alone is as wide as a letter, so a centred line lands left of centre and a
 * wrap breaks early. A player that uses this wraps by [wrap], hands the player its lines
 * left-justified in a box too wide to wrap again, and puts each line where its own justification
 * would by prefixing it with [padding].
 *
 * Words are measured once each and summed with the space between them: a script that shapes its
 * letters together does not shape across a space, and measuring each candidate line whole would
 * cost a frame on every verse change.
 */
class ShapedLines(
    /** A word's width as one shaped run, at the size being laid out. */
    private val wordWidth: (String) -> Float,
    /** A space's width at that size. */
    private val spaceWidth: Float,
    /** A hair space's width at that size; 0 when the font cannot draw one. */
    private val hairSpaceWidth: Float = 0f,
) {

    /** [line]'s width: its words, shaped, and the spaces between them. */
    fun width(line: String): Float {
        val words = line.split(' ')
        return words.sumOf { wordWidth(it).toDouble() }.toFloat() + (words.size - 1) * spaceWidth
    }

    /**
     * [text] broken into lines no wider than [boxWidth], greedily, a word at a time; each written
     * line break starts a new line. A word wider than the box on its own is broken between
     * characters as a reader counts them, never inside one. No box (0 or less) wraps nothing.
     */
    fun wrap(text: String, boxWidth: Float): List<String> =
        text.split('\n').flatMap { paragraph -> wrapParagraph(paragraph, boxWidth).ifEmpty { listOf("") } }

    private fun wrapParagraph(paragraph: String, boxWidth: Float): List<String> {
        if (boxWidth <= 0f) return listOf(paragraph)
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        var lineWidth = 0f
        for (word in paragraph.split(' ')) {
            val w = wordWidth(word)
            val needed = if (line.isEmpty()) w else lineWidth + spaceWidth + w
            when {
                needed <= boxWidth -> {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                    lineWidth = needed
                }
                w > boxWidth -> {
                    if (line.isNotEmpty()) lines += line.toString()
                    val pieces = breakWord(word, boxWidth)
                    lines += pieces.dropLast(1)
                    line = StringBuilder(pieces.last())
                    lineWidth = wordWidth(pieces.last())
                }
                else -> {
                    lines += line.toString()
                    line = StringBuilder(word)
                    lineWidth = w
                }
            }
        }
        if (line.isNotEmpty() || lines.isEmpty()) lines += line.toString()
        return lines
    }

    /** [word] cut between its user-perceived characters into pieces no wider than [boxWidth]. */
    private fun breakWord(word: String, boxWidth: Float): List<String> {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(word) }
        val pieces = mutableListOf<String>()
        var start = 0
        var end = breaks.next()
        var lastFit = start
        while (end != BreakIterator.DONE) {
            if (wordWidth(word.substring(start, end)) > boxWidth && lastFit > start) {
                pieces += word.substring(start, lastFit)
                start = lastFit
            }
            lastFit = end
            end = breaks.next()
        }
        pieces += word.substring(start)
        return pieces
    }

    /**
     * How far from the left edge of a box [boxWidth] wide a line [lineWidth] wide starts when
     * [alignment] places it — 0 left, 0.5 centred, 1 right. A line wider than the box starts at
     * its left edge.
     */
    fun offset(lineWidth: Float, boxWidth: Float, alignment: Float): Float =
        ((boxWidth - lineWidth) * alignment).coerceAtLeast(0f)

    /**
     * What to put before [line] so that, drawn from the left edge of a box [boxWidth] wide, it
     * sits where [alignment] puts it (see [offset]). Spaces, then hair spaces for the remainder,
     * so it lands within half a hair space; a font with no hair space leaves it to the spaces.
     */
    fun padding(line: String, boxWidth: Float, alignment: Float): String {
        val offset = offset(width(line), boxWidth, alignment)
        if (offset <= 0f || spaceWidth <= 0f) return ""
        val spaces = (offset / spaceWidth).toInt()
        val rest = offset - spaces * spaceWidth
        val hairs = if (hairSpaceWidth > 0f) Math.round(rest / hairSpaceWidth) else 0
        return " ".repeat(spaces) + HAIR_SPACE.toString().repeat(hairs)
    }

    companion object {
        const val HAIR_SPACE = '\u200A'

        /**
         * Lines measured by [measurer] in [style] — which must be the face and size the player
         * draws with — each word once. A space measures as nothing at the end of a run, so the
         * spaces are measured between two letters.
         *
         * The measurer rounds a width up to a whole pixel, which over the hundreds of spaces that
         * centre a short reference adds up to a visible shift, so it measures [MEASURE_SCALE]
         * times larger and scales the result back down.
         */
        fun measuredBy(measurer: TextMeasurer, style: TextStyle): ShapedLines {
            val large = style.copy(fontSize = style.fontSize * MEASURE_SCALE)
            fun width(s: String) = measurer.measure(s, large).multiParagraph.maxIntrinsicWidth / MEASURE_SCALE
            val words = HashMap<String, Float>()
            val pair = width("aa")
            return ShapedLines(
                wordWidth = { word -> words.getOrPut(word) { width(word) } },
                spaceWidth = width("a a") - pair,
                hairSpaceWidth = (width("a${HAIR_SPACE}a") - pair).coerceAtLeast(0f),
            )
        }

        private const val MEASURE_SCALE = 16f
        private const val CENTRED = 0.5f

        /** The alignment [offset] and [padding] take for a Lottie [justify]. */
        fun alignmentOf(justify: TextJustify): Float = when (justify) {
            TextJustify.Left -> 0f
            TextJustify.Right -> 1f
            else -> CENTRED
        }

        /**
         * [text] — the lines a layout produced joined with `\n`, or any leading part of them a
         * typewriter has revealed — with each line prefixed by its own entry of [pads], so a line
         * being typed grows from where it will finally sit.
         */
        fun applyPadding(text: String, pads: List<String>): String =
            text.split('\n').mapIndexed { i, line ->
                if (line.isEmpty()) line else pads.getOrElse(i) { "" } + line
            }.joinToString("\n")
    }
}
