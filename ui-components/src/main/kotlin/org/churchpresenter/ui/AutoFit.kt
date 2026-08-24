package org.churchpresenter.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

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

