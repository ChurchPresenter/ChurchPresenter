package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.songchords.ChordTransposer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.LyricSection

private const val SECTION_INDICATOR_SPACER_PX = 4

/**
 * The floor the two searches in this file settle on, in settings units at the 1920×1080 reference
 * resolution. Named rather than repeated at the five places that had it inline.
 *
 * These fit song lyrics, announcements and Q&A text, all of which are authored for the screen: a
 * section that cannot be made to fit at 8sp is one nobody would put up, so stopping here and letting
 * the rest overflow is the useful answer, and it keeps the search cost fixed.
 *
 * Scripture is not fitted through here and deliberately has no equivalent floor — a verse is as long
 * as it is, so `BiblePresenter.binarySearchFitScale` shrinks until the whole of it is inside its
 * frame however small that gets (issue #97). Don't read this constant as an app-wide policy.
 */
const val MIN_AUTO_FIT_FONT_SIZE = 8

/**
 * Binary-searches for the largest font size (in settings units, before scaleFactor)
 * whose rendered text fits within [availableWidth] × [availableHeight] pixels
 * at the 1920×1080 reference resolution (scaleFactor = 1).
 *
 * Measures each line separately and sums their heights to match the
 * presenter layout, which renders each line as a separate Text composable.
 *
 * Uses Density(1f) so that sp values map 1:1 to pixels, matching the
 * reference coordinate system used by the presenter.
 */
fun calculateAutoFitFontSize(
    textMeasurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    availableWidth: Int,
    availableHeight: Int,
): Int {
    if (text.isBlank() || availableWidth <= 0 || availableHeight <= 0) return MIN_AUTO_FIT_FONT_SIZE
    val referenceDensity = Density(1f)
    val lines = text.split("\n")
    val widthConstraints = Constraints(maxWidth = availableWidth)
    var low = MIN_AUTO_FIT_FONT_SIZE
    var high = 300
    while (high - low > 1) {
        val mid = (low + high) / 2
        val style = baseStyle.copy(fontSize = mid.sp)
        val totalHeight = lines.sumOf { line ->
            textMeasurer.measure(
                text = line,
                style = style,
                constraints = widthConstraints,
                density = referenceDensity
            ).size.height
        }
        if (totalHeight <= availableHeight) low = mid else high = mid
    }
    return (low - 1).coerceAtLeast(MIN_AUTO_FIT_FONT_SIZE)
}

/**
 * Finds the largest font size that fits ALL sections of a song without line wrapping.
 * Checks every line in every section against both width (no wrap) and height (fits vertically).
 *
 * [styleText] must turn a stored line into exactly what the presenter draws. A fit measured against
 * anything narrower is not a fit: an uppercase transform, letter spacing or word spacing all widen
 * the line *after* the search has settled, and the presenter draws its lyrics with `softWrap` off,
 * so the size that "fitted" then runs off the side of the screen with the end of every line cut.
 * The default measures the raw string, which is right only for a profile that styles nothing.
 */
fun calculateAutoFitForAllSections(
    textMeasurer: TextMeasurer,
    sections: List<LyricSection>,
    baseStyle: TextStyle,
    availableWidth: Int,
    availableHeight: Int,
    reservedHeight: Int = 0,
    includeEndIndicator: Boolean = false,
    styleText: (String) -> AnnotatedString = { AnnotatedString(it) },
): Int {
    if (sections.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return MIN_AUTO_FIT_FONT_SIZE
    val allLines = sections.flatMap { it.lines }
    if (allLines.all { it.isBlank() }) return MIN_AUTO_FIT_FONT_SIZE

    val effectiveHeight = (availableHeight - reservedHeight).coerceAtLeast(1)
    val referenceDensity = Density(1f)
    // Use unconstrained width to measure natural line width (no wrapping)
    val unconstrainedConstraints = Constraints()

    var low = MIN_AUTO_FIT_FONT_SIZE
    var high = 300
    while (high - low > 1) {
        val mid = (low + high) / 2
        val style = baseStyle.copy(fontSize = mid.sp)
        val fits = sections.withIndex().all { (sectionIdx, section) ->
            sectionFits(
                textMeasurer, section, style, unconstrainedConstraints, referenceDensity,
                availableWidth, effectiveHeight,
                withEndIndicator = includeEndIndicator && sectionIdx == sections.lastIndex,
                styleText = styleText,
            )
        }
        if (fits) low = mid else high = mid
    }
    return (low - 1).coerceAtLeast(MIN_AUTO_FIT_FONT_SIZE)
}

/**
 * The largest size at or below [maxFontSize] at which a chord chart fits the space it is given.
 *
 * Measuring a chart as if it were ordinary text does not work. `ChordChart` stacks a chord row above
 * every row of words — the chord `Text` is composed even where a segment carries no chord, so it
 * takes its line box regardless — and a long line wraps into several such rows, each one costing
 * both heights again. Summing the words alone under-reports all of that, which is how a chart that
 * had already been "fitted" still ran off the bottom of the zone.
 *
 * So height comes from what the chart actually draws, and the measurer is used for the one thing it
 * is needed for: how many rows a line takes once it has wrapped to the width available. Wrapping is
 * allowed — forbidding it would fit the longest line rather than the zone, and shrink the whole
 * chart to suit one line nobody asked to be unbroken.
 *
 * A line of chords with no words under it is a single row, not a stacked pair — that is the intro
 * case, written along the line rather than above it.
 */
fun calculateChordChartFontSize(
    textMeasurer: TextMeasurer,
    lines: List<String>,
    baseStyle: TextStyle,
    availableWidth: Int,
    availableHeight: Int,
    maxFontSize: Int,
    hasInfoLine: Boolean = false,
): Int {
    if (lines.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return maxFontSize
    val referenceDensity = Density(1f)
    val widthConstraints = Constraints(maxWidth = availableWidth)

    // What ChordChart itself lays out with, kept beside the model that depends on them.
    val chordRowHeight = 1.1f
    val lyricRowHeight = 1.5f
    val chordSizeRatio = 0.82f
    val gapBetweenLines = 4f
    val infoRowHeight = 0.5f * 1.4f
    val gapBelowInfo = 6f

    fun rowsFor(text: String, size: Float): Int {
        if (text.isBlank()) return 1
        return textMeasurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = size.sp),
            constraints = widthConstraints,
            density = referenceDensity,
        ).lineCount.coerceAtLeast(1)
    }

    fun fits(size: Int): Boolean {
        var height = if (hasInfoLine) size * infoRowHeight + gapBelowInfo else 0f
        lines.forEachIndexed { index, line ->
            if (index > 0) height += gapBetweenLines
            val words = ChordTransposer.stripChords(line).trim()
            height += if (words.isBlank()) {
                val chords = ChordTransposer.parseLine(line)
                    .mapNotNull { it.chord.ifBlank { null } }
                    .joinToString(" ")
                rowsFor(chords, size * chordSizeRatio) * size * lyricRowHeight
            } else {
                rowsFor(words, size.toFloat()) * size * (chordRowHeight + lyricRowHeight)
            }
            if (height > availableHeight) return false
        }
        return height <= availableHeight
    }

    if (fits(maxFontSize)) return maxFontSize
    var low = MIN_AUTO_FIT_FONT_SIZE
    var high = maxFontSize
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(mid)) low = mid else high = mid - 1
    }
    return low.coerceAtLeast(MIN_AUTO_FIT_FONT_SIZE)
}

/** True when every line of [section] fits on one row and the section fits the height. */
@Suppress("LongParameterList")
private fun sectionFits(
    textMeasurer: TextMeasurer,
    section: LyricSection,
    style: TextStyle,
    constraints: Constraints,
    density: Density,
    availableWidth: Int,
    effectiveHeight: Int,
    withEndIndicator: Boolean,
    styleText: (String) -> AnnotatedString,
): Boolean {
    // Check both primary and secondary lines so bilingual text also fits
    val lineSets = if (section.secondaryLines.isNotEmpty()) {
        listOf(section.lines, section.secondaryLines)
    } else {
        listOf(section.lines)
    }
    return lineSets.filter { it.isNotEmpty() }.all { lines ->
        val measured = lines.map {
            textMeasurer.measure(
                text = styleText(it), style = style, constraints = constraints, density = density,
            ).size
        }
        // Reserve space for end-of-song indicator on the last section: spacer + indicator height
        val indicatorHeight = if (withEndIndicator) {
            SECTION_INDICATOR_SPACER_PX + textMeasurer.measure(
                text = "* * *", style = style, constraints = constraints, density = density
            ).size.height
        } else {
            0
        }
        val sectionHeight = measured.sumOf { it.height } + indicatorHeight
        measured.none { it.width > availableWidth } && sectionHeight <= effectiveHeight
    }
}
