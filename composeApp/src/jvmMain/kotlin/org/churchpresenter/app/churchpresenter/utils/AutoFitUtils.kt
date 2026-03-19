package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.models.LyricSection

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
    if (text.isBlank() || availableWidth <= 0 || availableHeight <= 0) return 8
    val referenceDensity = Density(1f)
    val lines = text.split("\n")
    val widthConstraints = Constraints(maxWidth = availableWidth)
    var low = 8
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
    return (low - 1).coerceAtLeast(8)
}

/**
 * Finds the largest font size that fits ALL sections of a song without line wrapping.
 * Checks every line in every section against both width (no wrap) and height (fits vertically).
 */
fun calculateAutoFitForAllSections(
    textMeasurer: TextMeasurer,
    sections: List<LyricSection>,
    baseStyle: TextStyle,
    availableWidth: Int,
    availableHeight: Int,
): Int {
    if (sections.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return 8
    val allLines = sections.flatMap { it.lines }
    if (allLines.all { it.isBlank() }) return 8

    val referenceDensity = Density(1f)
    // Use unconstrained width to measure natural line width (no wrapping)
    val unconstrainedConstraints = Constraints()
    val widthConstraints = Constraints(maxWidth = availableWidth)

    var low = 8
    var high = 300
    while (high - low > 1) {
        val mid = (low + high) / 2
        val style = baseStyle.copy(fontSize = mid.sp)
        var fits = true

        for (section in sections) {
            // Check both primary and secondary lines so bilingual text also fits
            val lineSets = if (section.secondaryLines.isNotEmpty())
                listOf(section.lines, section.secondaryLines) else listOf(section.lines)
            for (lines in lineSets) {
                if (lines.isEmpty()) continue
                var sectionHeight = 0
                for (line in lines) {
                    val result = textMeasurer.measure(
                        text = line,
                        style = style,
                        constraints = unconstrainedConstraints,
                        density = referenceDensity
                    )
                    // Check width: line must fit without wrapping
                    if (result.size.width > availableWidth) {
                        fits = false
                        break
                    }
                    sectionHeight += result.size.height
                }
                if (!fits) break
                // Check height: all lines of this section must fit
                if (sectionHeight > availableHeight) {
                    fits = false
                    break
                }
            }
            if (!fits) break
        }
        if (fits) low = mid else high = mid
    }
    return (low - 1).coerceAtLeast(8)
}
