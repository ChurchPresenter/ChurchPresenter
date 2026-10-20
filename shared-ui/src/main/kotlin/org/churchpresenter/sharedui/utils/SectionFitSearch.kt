package org.churchpresenter.sharedui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.LyricSection

/*
 * The search behind [calculateAutoFitForAllSections], made cheap enough for an output's first frame.
 *
 * The answer is the one a plain binary search over 8..300 gives when every probe measures every line
 * of every section -- the same probes in the same order, so the same size. What changes is how each
 * probe is answered. A single-row line in the style's own face grows in exact proportion to its font
 * size (letter and word spacing are in `em`; only the box's rounding to whole pixels moves), so every
 * distinct line is measured once, at [FIT_REFERENCE_SIZE], and a probe that scaling settles beyond
 * doubt -- well clear of the edge either way -- is answered from it. A unit (a section's lines in one
 * language) that lands near the edge is measured for real at the probed size, exactly as before. Far
 * from the answer that is no measuring at all; at it, a handful of lines.
 *
 * A line that is not that -- one whose box at the reference size is not the face's own single row,
 * because a fallback face drew some of it (a colour emoji's bitmap strikes do not scale) or it broke
 * onto a second row -- is never predicted: every probe measures it, as the plain search did.
 *
 * The result is shared: two outputs and a preview showing one song ask the same question, and the
 * second and third read the first's answer from [SectionFitCache].
 */

/** The size every line is measured at once, from which the other sizes are predicted. */
private const val FIT_REFERENCE_SIZE = 100

/**
 * Headroom past the pixel rounding a scaled box can be off by, as a share of it. Measured, the boxes
 * of a face's own single-row lines stay inside that rounding at every size from 8 to 299 (heights
 * stray under half a pixel past it); this is spare on top, and a probe inside it is measured.
 */
private const val PREDICTION_RELATIVE_MARGIN = 0.01f

/** The same headroom in pixels: the box's ceiling at either size, and a pixel to spare. */
private const val PREDICTION_ABSOLUTE_MARGIN_PX = 2f

/** What the end-of-song indicator draws, measured in the lyrics' style. */
internal const val END_INDICATOR_TEXT = "* * *"

/** The gap above the end-of-song indicator, in reference pixels. */
internal const val SECTION_INDICATOR_SPACER_PX = 4

/** The largest size the search considers, exclusive. */
internal const val MAX_AUTO_FIT_SEARCH_SIZE = 300

/**
 * The binary search both fits share: [fits] is asked about the midpoints of 8..300 in turn, and the
 * answer is one below the largest size it accepted, never under [MIN_AUTO_FIT_FONT_SIZE].
 */
internal inline fun autoFitBinarySearch(fits: (Int) -> Boolean): Int {
    var low = MIN_AUTO_FIT_FONT_SIZE
    var high = MAX_AUTO_FIT_SEARCH_SIZE
    while (high - low > 1) {
        val mid = (low + high) / 2
        if (fits(mid)) low = mid else high = mid
    }
    return (low - 1).coerceAtLeast(MIN_AUTO_FIT_FONT_SIZE)
}

/**
 * The distinct lines of a song as the presenter draws them, and each unit of them that has to fit:
 * one language's lines of one section, as indexes into [lines]. Repeated choruses collapse to one.
 */
internal class SectionFitLines(
    val lines: List<AnnotatedString>,
    val units: List<IntArray>,
    /** The index of [END_INDICATOR_TEXT] in [lines], or -1 when no indicator row is reserved. */
    val indicator: Int,
) {
    /** The units as lists, so two tables compare by content. */
    private val unitLists: List<List<Int>> = units.map { it.asList() }

    override fun equals(other: Any?): Boolean =
        other is SectionFitLines && other.indicator == indicator && other.lines == lines &&
            other.unitLists == unitLists

    override fun hashCode(): Int = (lines.hashCode() * 31 + unitLists.hashCode()) * 31 + indicator

    companion object {
        fun of(
            sections: List<LyricSection>,
            includeEndIndicator: Boolean,
            styleText: (String) -> AnnotatedString,
        ): SectionFitLines {
            val indexes = LinkedHashMap<AnnotatedString, Int>()
            fun indexOf(text: AnnotatedString): Int = indexes.getOrPut(text) { indexes.size }
            val styled = HashMap<String, Int>()
            val units = LinkedHashSet<List<Int>>()
            sections.forEach { section ->
                // Every language's lines, so the size settled on fits the longest line of whichever
                // language has it -- `allLanguageLines` puts the primary first, then each translation.
                section.allLanguageLines().filter { it.isNotEmpty() }.forEach { lines ->
                    units += lines.map { line -> styled.getOrPut(line) { indexOf(styleText(line)) } }
                }
            }
            val indicator = if (includeEndIndicator) indexOf(AnnotatedString(END_INDICATOR_TEXT)) else -1
            return SectionFitLines(indexes.keys.toList(), units.map { it.toIntArray() }, indicator)
        }
    }
}

/** Answers the search's probes for one table of lines, in one style, in one box. */
internal class SectionFitSearch(
    private val textMeasurer: TextMeasurer,
    private val table: SectionFitLines,
    private val baseStyle: TextStyle,
    private val availableWidth: Int,
    private val effectiveHeight: Int,
) {
    private val referenceDensity = Density(1f)
    private val unconstrained = Constraints()
    private val referenceStyle = baseStyle.copy(fontSize = FIT_REFERENCE_SIZE.sp)
    private val reference: Array<IntSize>

    /** Whether each line's box scales with the size: one row, the face's own height. */
    private val predictable: BooleanArray
    private val measured = HashMap<Long, IntSize>()

    /** How many lines were laid out, the reference pass included. */
    var measureCount: Int = table.lines.size + 1
        private set

    init {
        val faceRow = layout(AnnotatedString(""), referenceStyle).size.height
        val layouts = table.lines.map { layout(it, referenceStyle) }
        reference = Array(layouts.size) { layouts[it].size }
        predictable = BooleanArray(layouts.size) { layouts[it].lineCount == 1 && layouts[it].size.height == faceRow }
    }

    fun result(): Int = autoFitBinarySearch(::fits)

    /** True when every unit fits at [size]: the plain search's answer for that probe. */
    fun fits(size: Int): Boolean {
        val scale = size / FIT_REFERENCE_SIZE.toFloat()
        var unsure: MutableList<IntArray>? = null
        for (unit in table.units) {
            when (predict(unit, scale)) {
                Prediction.FAILS -> return false
                Prediction.FITS -> Unit
                Prediction.UNSURE -> (unsure ?: mutableListOf<IntArray>().also { unsure = it }).add(unit)
            }
        }
        return unsure.orEmpty().all { unitFits(it, size) }
    }

    private enum class Prediction { FITS, FAILS, UNSURE }

    private fun low(referencePx: Int, scale: Float): Float =
        (referencePx - 1) * scale * (1 - PREDICTION_RELATIVE_MARGIN) - PREDICTION_ABSOLUTE_MARGIN_PX

    private fun high(referencePx: Int, scale: Float): Float =
        referencePx * scale * (1 + PREDICTION_RELATIVE_MARGIN) + PREDICTION_ABSOLUTE_MARGIN_PX

    /**
     * What the scaled reference says of [unit]. A line that is not [predictable] settles nothing: it
     * only leaves the unit unsure, and adds nothing to the height it is known to take at least.
     */
    private fun predict(unit: IntArray, scale: Float): Prediction {
        var sure = true
        var heightLow = 0f
        var heightHigh = 0f
        fun add(line: Int) {
            if (!predictable[line]) {
                sure = false
                return
            }
            val size = reference[line]
            if (high(size.width, scale) > availableWidth) sure = false
            heightLow += low(size.height, scale)
            heightHigh += high(size.height, scale)
        }
        for (line in unit) {
            if (predictable[line] && low(reference[line].width, scale) > availableWidth) return Prediction.FAILS
            add(line)
        }
        if (table.indicator >= 0) {
            heightLow += SECTION_INDICATOR_SPACER_PX
            heightHigh += SECTION_INDICATOR_SPACER_PX
            add(table.indicator)
        }
        return when {
            heightLow > effectiveHeight -> Prediction.FAILS
            sure && heightHigh <= effectiveHeight -> Prediction.FITS
            else -> Prediction.UNSURE
        }
    }

    /** The plain test, on real measures: no line wider than the box, and the lines' heights inside it. */
    private fun unitFits(unit: IntArray, size: Int): Boolean {
        var height = 0
        for (line in unit) {
            val box = measureAt(line, size)
            if (box.width > availableWidth) return false
            height += box.height
        }
        if (table.indicator >= 0) height += SECTION_INDICATOR_SPACER_PX + measureAt(table.indicator, size).height
        return height <= effectiveHeight
    }

    private fun measureAt(line: Int, size: Int): IntSize =
        measured.getOrPut(line.toLong() * (MAX_AUTO_FIT_SEARCH_SIZE + 1) + size) {
            measureCount++
            measure(line, size)
        }

    private fun measure(line: Int, size: Int): IntSize =
        layout(table.lines[line], baseStyle.copy(fontSize = size.sp)).size

    private fun layout(text: AnnotatedString, style: TextStyle): TextLayoutResult =
        textMeasurer.measure(text = text, style = style, constraints = unconstrained, density = referenceDensity)
}

/**
 * The answers already worked out, shared by every output and preview: one song in one style in one
 * box is fitted once. Bounded, least recently used first out.
 *
 * Keyed on what the presenter draws -- the styled lines, the style and the room -- and on one line's
 * size at the reference size. Each window brings its own font resolver, and two of them answer alike
 * only when they lay text out alike: the app's all resolve the same system faces, a test's pinned
 * fonts do not, and the probe's size is what tells them apart.
 */
internal object SectionFitCache {
    private const val CAPACITY = 32

    internal data class Key(
        val table: SectionFitLines,
        val baseStyle: TextStyle,
        val availableWidth: Int,
        val effectiveHeight: Int,
        val probe: IntSize,
    )

    private val entries = object : LinkedHashMap<Key, Int>(CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Int>?): Boolean = size > CAPACITY
    }

    fun get(key: Key): Int? = synchronized(entries) { entries[key] }

    fun put(key: Key, value: Int) {
        synchronized(entries) { entries[key] = value }
    }

    /** Forgets every answer: for a test that counts what a fit measures. */
    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private const val LOAD_FACTOR = 0.75f
}

/** The size of [table]'s longest line at the reference size: the cache's view of the font resolver. */
internal fun SectionFitLines.probe(textMeasurer: TextMeasurer, baseStyle: TextStyle): IntSize {
    val longest = lines.maxBy { it.length }
    return textMeasurer.measure(
        text = longest,
        style = baseStyle.copy(fontSize = FIT_REFERENCE_SIZE.sp),
        constraints = Constraints(),
        density = Density(1f),
    ).size
}
