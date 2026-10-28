package org.churchpresenter.stt.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.churchpresenter.core.models.stt.STTSegment

/**
 * One side's captions as this output keeps them, rather than as the server's window has them.
 *
 * The server sends its newest captions whole every time, and drops the oldest from the front as new
 * ones come: re-deriving the paragraph from that window moves the text's line breaks every time it
 * loses its front. So the window is merged into a history keyed by segment id instead, which only
 * grows at its end, and is cut at its front only where the layout says a line started ([cutAt]) --
 * the lines after a former line start wrap exactly as they did.
 *
 * Within one epoch a window only adds segments at its end, loses its oldest from the front, or has
 * one segment corrected in place. Anything else -- a new epoch (a new session, or the whole list
 * sent again after a connect), an empty window, ids out of order, a segment missing in the middle,
 * more than one corrected at once -- starts the history afresh, and bumps [generation].
 */
internal class CaptionHistory {

    /** The segments kept, oldest first. The first may be drawn from part-way in -- see [cutFront]. */
    var segments: List<STTSegment> = emptyList()
        private set

    /** Bumped each time the history starts afresh. */
    var generation = 0
        private set

    private var epoch: Int? = null
    private var lastWindow: List<STTSegment>? = null

    // Where the kept text is drawn from: so far into the words of the segment with this id
    private var cut: SegmentPoint? = null

    /** Merges the server's [window] of the epoch [epoch] in, and returns the segments kept. */
    fun merge(window: List<STTSegment>, epoch: Int): List<STTSegment> {
        if (epoch == this.epoch && window == lastWindow) return segments
        val copy = window.toList()
        lastWindow = copy
        val extended = if (epoch == this.epoch) extend(copy) else null
        this.epoch = epoch
        if (extended == null) {
            startAfresh(copy)
        } else {
            segments = extended
        }
        return segments
    }

    /** The kept segments extended by [window], or null when it does not extend them. */
    private fun extend(window: List<STTSegment>): List<STTSegment>? {
        val ordered = window.isNotEmpty() && window.zipWithNext().none { (a, b) -> b.id <= a.id }
        return when {
            !ordered -> null
            // Captions starting while this output looked on: they are new, not a backlog
            segments.isEmpty() -> window
            else -> extendKept(window)
        }
    }

    /** [extend] for a history with something in it. */
    private fun extendKept(window: List<STTSegment>): List<STTSegment>? {
        val lastKept = segments.last().id
        // The window's segments from the first one kept on: older ones were cut here already
        val overlap = window.filter { it.id >= segments.first().id }
        val known = overlap.takeWhile { it.id <= lastKept }
        val from = known.firstOrNull()?.let { head -> segments.indexOfFirst { it.id == head.id } } ?: -1
        val kept = if (from < 0) emptyList() else segments.subList(from, segments.size)
        val changed = kept.zip(known).filter { (was, now) -> was.text != now.text }.map { it.first.id }
        if (from < 0 || kept.map { it.id } != known.map { it.id } || changed.size > 1) return null
        // The cut was a line start in the words as they were; in new words it is the segment's start
        cut = cut?.let { point -> if (point.id in changed) SegmentPoint(point.id, 0) else point }
        return segments.subList(0, from) + known + overlap.drop(known.size)
    }

    private fun startAfresh(window: List<STTSegment>) {
        segments = window
        cut = null
        generation++
    }

    /**
     * Drops everything before [offset] characters into the words of the segment [id] -- a line start
     * the layout reported, which is never drawn again. A point no longer kept is ignored.
     */
    fun cutAt(id: Int, offset: Int) {
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return
        segments = segments.subList(index, segments.size)
        cut = SegmentPoint(id, offset.coerceAtLeast(0))
    }

    /** How far into the words of the segment [id] the kept text starts: 0 unless it is the one cut. */
    fun offsetIn(id: Int): Int = cut?.takeIf { it.id == id }?.offset ?: 0

    /**
     * [shown] -- the kept segments, or the first of them as far as a reveal has got -- drawn from the
     * cut, and how many characters that took off the front of their words.
     */
    fun cutFront(shown: List<STTSegment>): Pair<List<STTSegment>, Int> {
        val point = cut ?: return shown to 0
        val first = shown.firstOrNull()?.takeIf { it.id == point.id } ?: return shown to 0
        val words = normalizeSegmentText(first.text)
        val dropped = point.offset.coerceAtMost(words.length)
        return (listOf(first.copy(text = words.substring(dropped))) + shown.drop(1)) to dropped
    }
}

/** This output's [CaptionHistory] of [window], merged as it changes. */
@Composable
internal fun rememberCaptionHistory(window: List<STTSegment>, epoch: Int): CaptionHistory {
    val history = remember { CaptionHistory() }
    history.merge(window, epoch)
    return history
}
