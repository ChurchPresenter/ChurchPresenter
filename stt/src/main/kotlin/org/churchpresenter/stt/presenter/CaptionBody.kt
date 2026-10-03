package org.churchpresenter.stt.presenter

import org.churchpresenter.stt.STTSegment
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CaptionReading
import java.text.BreakIterator

/**
 * One side of the captions as it is drawn: the [text], the [units] the dimming steps over (oldest
 * first), and where the in-progress words start, or null when none are shown.
 */
internal data class CaptionBody(val text: String, val units: List<IntRange>, val inProgressStart: Int?)

/**
 * The caption [segments] and the [inProgress] words after them, shaped the way [reading] asks: each
 * segment or sentence on a line of its own, a blank line between them, wrapped at a character count,
 * and in capitals when [allCaps]. Wrapping and capitals keep every character where it was, so a
 * range found in the result stays true of it.
 */
internal fun captionBody(
    segments: List<STTSegment>,
    inProgress: String?,
    reading: CaptionReading,
    allCaps: Boolean,
): CaptionBody {
    val pieces = segments.mapNotNull { normalizeSegmentText(it.text).takeIf(String::isNotEmpty) }
    val progress = inProgress?.let(::normalizeSegmentText)?.takeIf(String::isNotEmpty)
    val joined = (pieces + listOfNotNull(progress)).joinToString(" ")
    val progressStart = progress?.let { joined.length - it.length }
    val spans: List<IntRange> = when (reading.lineBreaks) {
        CAPTION_BREAK_SENTENCE -> sentenceSpans(joined, progressStart)
        else -> pieceSpans(pieces + listOfNotNull(progress))
    }
    val breaks = reading.lineBreaks == CAPTION_BREAK_SEGMENT || reading.lineBreaks == CAPTION_BREAK_SENTENCE
    val separator = when {
        !breaks -> " "
        reading.blankLineBetween -> "\n\n"
        else -> "\n"
    }
    val text = StringBuilder()
    val units = ArrayList<IntRange>(spans.size)
    var newProgressStart: Int? = null
    spans.forEachIndexed { i, span ->
        if (i > 0) text.append(separator)
        val start = text.length
        if (progressStart != null && newProgressStart == null && span.last >= progressStart) {
            newProgressStart = start + (progressStart - span.first).coerceAtLeast(0)
        }
        text.append(joined, span.first, span.last + 1)
        units.add(start until text.length)
    }
    var shaped = wrapAtChars(text.toString(), reading.maxCharsPerLine)
    if (allCaps) shaped = upperCaseInPlace(shaped)
    return CaptionBody(shaped, units, newProgressStart)
}

/** Each piece's range in the pieces joined by single spaces. */
private fun pieceSpans(pieces: List<String>): List<IntRange> {
    var at = 0
    return pieces.map { piece -> (at until at + piece.length).also { at += piece.length + 1 } }
}

/**
 * The sentences of [text], each without the spaces around it. The in-progress words starting at
 * [progressStart] begin a sentence of their own, so they are never dimmed with the one before.
 */
private fun sentenceSpans(text: String, progressStart: Int?): List<IntRange> {
    val bounds = sortedSetOf(0, text.length)
    val iterator = BreakIterator.getSentenceInstance().apply { setText(text) }
    generateSequence { iterator.next().takeIf { it != BreakIterator.DONE } }.forEach { bounds.add(it) }
    progressStart?.let { bounds.add(it) }
    return bounds.zipWithNext().mapNotNull { (from, to) ->
        var start = from
        var end = to
        while (start < end && text[start] == ' ') start++
        while (end > start && text[end - 1] == ' ') end--
        if (start < end) start until end else null
    }
}

/**
 * [text] with a space turned into a line break wherever a line would otherwise run past [maxChars];
 * 0 or less leaves it alone. A word longer than the limit keeps a line to itself.
 */
internal fun wrapAtChars(text: String, maxChars: Int): String {
    if (maxChars <= 0) return text
    val chars = text.toCharArray()
    var lineStart = 0
    var lastSpace = -1
    for (i in chars.indices) {
        val c = chars[i]
        if (c == '\n') {
            lineStart = i + 1
        } else {
            // A letter that would make the line too long sends it to the last space before it.
            if (c != ' ' && i - lineStart + 1 > maxChars && lastSpace >= lineStart) {
                chars[lastSpace] = '\n'
                lineStart = lastSpace + 1
            }
            if (c == ' ') lastSpace = i
        }
    }
    return String(chars)
}

/** [text] in capitals, one character for one, so every range in it still holds. */
internal fun upperCaseInPlace(text: String): String = String(CharArray(text.length) { text[it].uppercaseChar() })

private const val WHOLE_PERCENT = 100

/**
 * How opaque each of [unitCount] units is, oldest first: the newest whole, each older one
 * [CaptionReading.dimStepPercent] fainter, none below [CaptionReading.dimFloorPercent]. All whole
 * when dimming is off.
 */
internal fun unitAlphas(unitCount: Int, reading: CaptionReading): List<Float> = List(unitCount) { i ->
    if (!reading.dimOlderLines) 1f else {
        val age = unitCount - 1 - i
        val floor = reading.dimFloorPercent.coerceIn(0, WHOLE_PERCENT) / WHOLE_PERCENT.toFloat()
        (1f - age * reading.dimStepPercent.coerceIn(0, WHOLE_PERCENT) / WHOLE_PERCENT.toFloat()).coerceAtLeast(floor)
    }
}
