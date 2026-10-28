package org.churchpresenter.stt.presenter

import androidx.compose.ui.text.AnnotatedString
import org.churchpresenter.settings.CaptionReading

/** One side's captions as this output keeps them ([history]), and what its reveal has put on screen. */
internal class RevealedSide(val history: CaptionHistory, val drip: RevealedCaption)

/**
 * One side's caption as it is drawn. A paragraph is laid out from the history, so the lines on screen
 * keep their words; a [ticker] (or an RSVP flash) only ever shows the newest words, kept as before.
 */
internal fun shownCaption(
    revealed: RevealedSide,
    side: CaptionSide,
    reading: CaptionReading,
    maxSegments: Int,
    ticker: Boolean,
): CaptionShown = if (ticker) {
    CaptionShown(captionText(side, revealed.drip.flashWords, reading))
} else {
    paragraphCaption(revealed.history, revealed.drip, side, reading, maxSegments)
}

/**
 * One side's caption as a paragraph laid out from its [history]: drawn from the history's cut, the
 * newest [maxSegments] kept (0 keeps all) by where they start rather than by cutting the rest out,
 * and -- while a reveal types it out -- the shown part with the text it will grow into, so its lines
 * break where they will. Each line start the layout leaves behind is handed back to the history.
 */
private fun paragraphCaption(
    history: CaptionHistory,
    drip: RevealedCaption,
    side: CaptionSide,
    reading: CaptionReading,
    maxSegments: Int,
): CaptionShown {
    val (segments, dropped) = history.cutFront(drip.through)
    val body = captionBody(segments, side.inProgress, reading, side.allCaps)
    val full = withReadingAids(buildDisplayText(body, segments.isNotEmpty(), reading, side.ink), 0, reading)
    val onSafeCut: (Int) -> Unit = { lineStart ->
        body.pointAt(lineStart)?.let { history.cutAt(it.id, it.offset + history.offsetIn(it.id)) }
    }
    val keepFrom = body.newestStart(maxSegments)
    val revealedEnd = drip.revealed?.let { body.shapedAt((it - dropped).coerceAtLeast(0)) }
    if (revealedEnd == null || revealedEnd >= full.length) return CaptionShown(full, null, keepFrom, onSafeCut)
    return CaptionShown(full.subSequence(0, revealedEnd), full, keepFrom, onSafeCut)
}

/**
 * One side's caption as [CaptionLines] draws it: its [text], and for a paragraph the [layoutText] it
 * will grow into, where its [keepFrom] newest segments start, and who to tell of a line start it
 * leaves behind (see `BottomAlignedText`).
 */
internal class CaptionShown(
    val text: AnnotatedString,
    val layoutText: AnnotatedString? = null,
    val keepFrom: Int = 0,
    val onSafeCut: ((Int) -> Unit)? = null,
) {
    fun isNotEmpty(): Boolean = text.isNotEmpty()
}
