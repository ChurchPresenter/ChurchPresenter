package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.stt.HighlightedWord
import org.churchpresenter.settings.CaptionReading

/** What a side's caption is painted with: its colour, the words to highlight, and its word spacing. */
internal class CaptionInk(val baseColor: Color, val highlights: List<HighlightedWord>, val spaceTrackingEm: Float?)

internal fun buildDisplayText(
    body: CaptionBody,
    hasSegments: Boolean,
    reading: CaptionReading,
    ink: CaptionInk,
): AnnotatedString {
    val baseColor = ink.baseColor
    val fullText = body.text
    if (fullText.isEmpty()) return AnnotatedString("")

    // Build per-character color array then construct contiguous runs
    val colors = Array(fullText.length) { baseColor }

    // Older units fainter; the in-progress words, which are no unit's age, stay out of the count
    val progressFrom = body.inProgressStart ?: fullText.length
    val settled = body.units.filter { it.first < progressFrom }
    unitAlphas(settled.size, reading).forEachIndexed { i, alpha ->
        if (alpha < 1f) for (j in settled[i]) colors[j] = baseColor.copy(alpha = baseColor.alpha * alpha)
    }

    // Dim in-progress text
    if (body.inProgressStart != null && hasSegments) {
        for (j in body.inProgressStart until fullText.length) colors[j] = baseColor.copy(alpha = 0.6f)
    }

    // Apply word highlighting with Unicode word boundaries
    ink.highlights.forEach { applyHighlight(it, fullText, colors) }

    return runsOf(fullText, colors, ink.spaceTrackingEm)
}

/** Paints every match of one highlighted word into [colors]. A pattern that won't compile is skipped. */
private fun applyHighlight(hw: HighlightedWord, fullText: String, colors: Array<Color>) {
    if (hw.word.isBlank()) return
    try {
        val highlightColor = parseHexColor(hw.color)
        val wb = "(?<![\\p{L}\\p{N}])"
        val we = "(?![\\p{L}\\p{N}])"
        val rawPattern = if (hw.isRegex) "$wb(?:${hw.word})$we" else "$wb${Regex.escape(hw.word)}$we"
        var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
        if (!hw.caseSensitive) {
            flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
        }
        java.util.regex.Pattern.compile(rawPattern, flags).toRegex().findAll(fullText).forEach { match ->
            for (j in match.range) colors[j] = highlightColor
        }
    } catch (_: Exception) {}
}

/**
 * The per-character colours collapsed into contiguous styled runs, each space widened by
 * [spaceTrackingEm] when word spacing is set (see `styledDisplayText`).
 */
private fun runsOf(fullText: String, colors: Array<Color>, spaceTrackingEm: Float?): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < fullText.length) {
            val color = colors[i]
            val start = i
            while (i < fullText.length && colors[i] == color) i++
            withStyle(SpanStyle(color = color)) {
                append(fullText.substring(start, i))
            }
        }
        if (spaceTrackingEm != null) {
            fullText.forEachIndexed { index, c ->
                if (c == ' ') addStyle(SpanStyle(letterSpacing = spaceTrackingEm.em), index, index + 1)
            }
        }
    }
