package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.stt.STTSegment
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.STTSettings

/**
 * The character arithmetic behind the STT drip feed (the letter-by-letter caption reveal).
 *
 * The reveal is a single cursor over the WHOLE caption, not a per-segment one. The STT server
 * delivers completed segments a few seconds apart; a per-segment cursor has to reset every time one
 * arrives, which snaps the previous segment to its full text and makes the configured speed
 * irrelevant whenever a segment cannot finish revealing before the next lands. One cursor over the
 * joined caption keeps the reveal continuous across segment boundaries.
 *
 * Kept separate from the composable so the arithmetic is testable without a composition.
 */

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * How much backlog the reveal tolerates before it starts drawing more than one character per tick.
 * At a slow speed the reveal is easily outrun by live speech; without this the caption would drift
 * further behind the room for the rest of the service. Sized so a lag of one full caption line
 * roughly doubles the rate, and a large backlog is consumed briskly without ever jumping.
 */
private const val CATCH_UP_CHARS = 120

/** One segment as it appears in the caption: trimmed, with internal whitespace collapsed. */
internal fun normalizeSegmentText(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

/** The full caption these segments produce — the exact string the presenter would draw. */
internal fun captionText(segments: List<STTSegment>): String =
    segments.mapNotNull { normalizeSegmentText(it.text).takeIf(String::isNotEmpty) }
        .joinToString(" ")

/**
 * The segments trimmed to the first [revealed] characters of the caption, so the caller can render
 * them through the ordinary display path. Segments past the cursor are dropped and the one it lands
 * inside is truncated; the list is returned untouched once the cursor covers the whole caption.
 */
internal fun applyRevealBudget(segments: List<STTSegment>, revealed: Int): List<STTSegment> {
    if (revealed <= 0) return emptyList()
    if (revealed >= captionText(segments).length) return segments
    val revealedSegments = ArrayList<STTSegment>(segments.size)
    var used = 0
    var first = true
    val visible = segments.filter { normalizeSegmentText(it.text).isNotEmpty() }
    var index = 0
    var exhausted = false
    while (index < visible.size && !exhausted) {
        val segment = visible[index]
        val text = normalizeSegmentText(segment.text)
        val separator = if (first) 0 else 1
        val budget = revealed - used - separator
        when {
            budget <= 0 -> exhausted = true
            budget < text.length -> {
                revealedSegments.add(segment.copy(text = text.take(budget)))
                exhausted = true
            }
            else -> {
                revealedSegments.add(segment)
                used += separator + text.length
                first = false
            }
        }
        index++
    }
    return revealedSegments
}

/**
 * Where the cursor belongs after the caption changed from [prevFull] to [newFull].
 *
 * A pure append — the ordinary case, a new segment landing — keeps the cursor exactly where it was
 * so the reveal simply carries on. When the server's rolling window has also dropped text off the
 * front, however much of the already-shown text survived stays shown and the cursor moves back with
 * it. When the window has scrolled past everything that was shown, the reveal starts over on text
 * nobody has read yet. Only a caption rewritten past recognition sends the cursor to the end:
 * showing it all at once is a far smaller fault than re-typing words the room has already read.
 */
internal fun reanchorCursor(prevFull: String, prevRevealed: Int, newFull: String): Int {
    val revealed = prevRevealed.coerceIn(0, prevFull.length)
    val shown = prevFull.take(revealed)
    if (newFull.startsWith(shown)) return revealed

    val carriedOver = overlapLength(shown, newFull)
    return when {
        carriedOver > 0 -> carriedOver
        revealed == 0 -> 0
        // Nothing shown survived. If the new caption still picks up where the old one left off,
        // the window merely scrolled past it and the rest has never been read — reveal it properly.
        overlapLength(prevFull, newFull) > 0 -> 0
        else -> newFull.length
    }
}

/** The largest number of characters by which the tail of [a] and the head of [b] coincide. */
private fun overlapLength(a: String, b: String): Int {
    var length = minOf(a.length, b.length)
    while (length > 0 && !a.endsWith(b.take(length))) length--
    return length
}

/**
 * Characters to reveal on this tick: one at the configured speed when the reveal is keeping up,
 * more as the backlog grows. See [CATCH_UP_CHARS].
 */
internal fun revealStep(revealed: Int, target: Int): Int =
    1 + (target - revealed).coerceAtLeast(0) / CATCH_UP_CHARS

/** What the reveal steps over: a letter, a whole word, or a whole segment. */
internal enum class RevealUnit { LETTER, WORD, SEGMENT }

/** How the caption is revealed: [delayMs] per character, stepping a [unit] at a time. */
internal data class RevealPace(val delayMs: Long, val unit: RevealUnit)

private const val READING_MS_PER_SECOND = 1000L

/**
 * The pace [s] reveals its captions at, or null when they appear all at once.
 *
 * The drip feed types letter by letter at its own speed. The reading-speed limit never lets words
 * arrive faster than that many characters a second: on its own it brings them a word at a time, and
 * with the drip feed it slows the typing down to it when the drip feed is faster. Pop-on never types
 * -- its words arrive in whole segments -- so there the limit holds each segment back instead.
 */
internal fun revealPace(s: STTSettings): RevealPace? {
    val reading = s.reading
    val cps = reading.readingSpeedCps.coerceAtLeast(1)
    val limitMs = if (reading.readingSpeedLimit) READING_MS_PER_SECOND / cps else 0L
    return when {
        reading.style == CAPTION_STYLE_POP_ON -> RevealPace(limitMs, RevealUnit.SEGMENT).takeIf { limitMs > 0 }
        // A ticker adds what is new as one piece, so it types nothing either: the limit paces words
        reading.style == CAPTION_STYLE_TICKER -> RevealPace(limitMs, RevealUnit.WORD).takeIf { limitMs > 0 }
        s.dripFeedEnabled -> RevealPace(maxOf(s.dripFeedSpeed.toLong().coerceAtLeast(1L), limitMs), RevealUnit.LETTER)
        limitMs > 0 -> RevealPace(limitMs, RevealUnit.WORD)
        else -> null
    }
}

/** Where the word after [from] in [text] ends: past any spaces, then up to the next space or the end. */
internal fun nextWordEnd(text: String, from: Int): Int {
    var i = from.coerceIn(0, text.length)
    while (i < text.length && text[i] == ' ') i++
    while (i < text.length && text[i] != ' ') i++
    return i
}

/** Where the segment that runs past [from] ends in the caption [segments] make (see [captionText]). */
internal fun nextSegmentEnd(segments: List<STTSegment>, from: Int): Int {
    var end = -1
    for (segment in segments) {
        val text = normalizeSegmentText(segment.text)
        if (text.isEmpty()) continue
        end += 1 + text.length
        if (end > from) return end
    }
    return end.coerceAtLeast(from)
}
