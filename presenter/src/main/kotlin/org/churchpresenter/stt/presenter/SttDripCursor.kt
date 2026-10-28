package org.churchpresenter.stt.presenter

import org.churchpresenter.core.models.stt.STTSegment

/*
 * The drip feed's cursor followed by segment id rather than by text. The reveal itself still counts
 * characters into the joined caption (see `SttDripFeed.kt`); these say where that count belongs once
 * the segments under it have changed.
 */

/**
 * Where the cursor belongs after the caption's segments changed from [prev] to [new]: it keeps its
 * place in its own segment wherever that now is. Segments before it are shown whole, so one corrected
 * above it is swapped in at once; its own segment corrected keeps the part of the old words it still
 * begins with. When its segment has left the front, everything left is newer than it and is revealed
 * from the start.
 *
 * Without unique ids the segments cannot be followed, and the text is compared instead.
 */
internal fun reanchorCursor(prev: List<STTSegment>, prevRevealed: Int, new: List<STTSegment>): Int {
    val before = wordedPieces(prev)
    val after = wordedPieces(new)
    if (!before.uniqueIds() || !after.uniqueIds()) {
        return reanchorCursor(captionText(prev), prevRevealed, captionText(new))
    }
    val point = cursorPoint(before, prevRevealed.coerceIn(0, captionText(prev).length))
    return if (point == null) 0 else placeIn(after, point, before.first { it.first == point.id }.second)
}

/** Where [point] -- in a segment whose words were [was] -- is in [pieces] joined by spaces. */
private fun placeIn(pieces: List<Pair<Int, String>>, point: SegmentPoint, was: String): Int {
    // Up to its own segment, or to the first one past where the cursor was -- nothing of which has
    // been read -- every segment is shown whole
    val reached = pieces.indexOfFirst { it.first >= point.id }.let { if (it < 0) pieces.size else it }
    val before = pieces.take(reached).sumOf { it.second.length } + (reached - 1).coerceAtLeast(0)
    val piece = pieces.getOrNull(reached)
    return when {
        piece == null -> before
        piece.first > point.id -> before
        else -> {
            // Its own segment, as far as its words still run the same
            val kept = if (was == piece.second) point.offset else commonPrefix(was.take(point.offset), piece.second)
            (if (reached > 0) before + 1 else 0) + kept.coerceAtMost(piece.second.length)
        }
    }
}

/** The segments with words, as (id, words) pairs. */
private fun wordedPieces(segments: List<STTSegment>): List<Pair<Int, String>> = segments.mapNotNull { segment ->
    normalizeSegmentText(segment.text).takeIf(String::isNotEmpty)?.let { segment.id to it }
}

private fun List<Pair<Int, String>>.uniqueIds(): Boolean = map { it.first }.toSet().size == size

/**
 * Which segment the cursor [revealed] characters into [pieces] joined by spaces is in, and how far.
 * On the space between two it is at the start of the second. Null when nothing is revealed.
 */
private fun cursorPoint(pieces: List<Pair<Int, String>>, revealed: Int): SegmentPoint? {
    if (revealed <= 0 || pieces.isEmpty()) return null
    var at = 0
    for ((index, piece) in pieces.withIndex()) {
        val start = at + if (index > 0) 1 else 0
        if (revealed <= start + piece.second.length) {
            return SegmentPoint(piece.first, (revealed - start).coerceAtLeast(0))
        }
        at = start + piece.second.length
    }
    return pieces.last().let { SegmentPoint(it.first, it.second.length) }
}

private fun commonPrefix(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}

/**
 * The segments up to and including the one the cursor [revealed] characters in is typing, each
 * whole: what the shown text will have grown into once that segment is out, and so where its lines
 * break. Everything when the cursor has reached the end.
 */
internal fun throughCursor(segments: List<STTSegment>, revealed: Int): List<STTSegment> {
    var at = -1
    return segments.takeWhile { segment ->
        val words = normalizeSegmentText(segment.text)
        // A segment with no words takes no room; one starting past the cursor has not begun
        val started = words.isEmpty() || at + 1 < revealed
        if (words.isNotEmpty() && started) at += 1 + words.length
        started
    }
}
