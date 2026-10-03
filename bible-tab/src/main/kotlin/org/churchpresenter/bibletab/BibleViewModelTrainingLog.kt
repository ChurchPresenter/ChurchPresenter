package org.churchpresenter.bibletab

import kotlinx.coroutines.flow.first
import org.churchpresenter.sharedui.utils.TrainingDataLogger

fun BibleViewModel.logLiveReference(reference: LiveReference) {
    val displayBookIndex = reference.displayBookIndex
    val chapter = reference.chapter
    val verseStart = reference.verseStart
    val verseEnd = reference.verseEnd
    val source = reference.source
    val autoFollow = reference.autoFollow
    val matchType = reference.matchType
    val canonical = canonicalRefForDisplay(displayBookIndex, chapter, verseStart)
    val canonicalEnd = verseEnd?.let { canonicalRefForDisplay(displayBookIndex, chapter, it)?.third }
    TrainingDataLogger.logLiveReference(
        book              = canonical?.first ?: canonicalBookIdForDisplayIndex(displayBookIndex),
        chapter           = canonical?.second ?: chapter,
        verseStart        = canonical?.third ?: verseStart,
        verseEnd          = canonicalEnd ?: verseEnd,
        source            = source,
        segmentId         = lastDetectionSegmentId,
        autoFollow        = autoFollow,
        matchType         = matchType,
        displayChapter    = chapter,
        displayVerseStart = verseStart,
        displayVerseEnd   = verseEnd,
    )
}

internal fun BibleViewModel.logOperatorFlag(
    kind: String,
    bookName: String? = null,
    chapter: Int? = null,
    verseStart: Int? = null,
    verseEnd: Int? = null,
    matchType: String? = null,
) {
    val displayIndex = bookName?.let { name -> _books.value.indexOfFirst { it.equals(name, ignoreCase = true) } }
        ?.takeIf { it >= 0 }
    val canonical = if (displayIndex != null && chapter != null) {
        canonicalRefForDisplay(displayIndex, chapter, verseStart)
    } else null
    val canonicalEnd = if (displayIndex != null && chapter != null && verseEnd != null) {
        canonicalRefForDisplay(displayIndex, chapter, verseEnd)?.third
    } else null
    TrainingDataLogger.logOperatorFlag(
        kind              = kind,
        book              = canonical?.first ?: displayIndex?.let { canonicalBookIdForDisplayIndex(it) },
        chapter           = canonical?.second ?: chapter,
        verseStart        = canonical?.third ?: verseStart,
        verseEnd          = canonicalEnd ?: verseEnd,
        segmentId         = lastDetectionSegmentId,
        matchType         = matchType,
        displayChapter    = chapter,
        displayVerseStart = verseStart,
        displayVerseEnd   = verseEnd,
    )
}

internal fun BibleViewModel.logDetectionOutcome(ref: DetectedReference, action: String, correctedRef: String? = null) {
    val canonical = canonicalRefForDisplay(ref.bookIndex, ref.chapter, ref.verseStart)
    TrainingDataLogger.logSuggestionOutcome(
        suggestedBook    = canonical?.first ?: canonicalBookIdForDisplayIndex(ref.bookIndex),
        suggestedChapter = canonical?.second ?: ref.chapter,
        suggestedVerse   = canonical?.third ?: ref.verseStart,
        action           = action,
        correctedRef     = correctedRef,
        matchType        = ref.matchTypeLabel(),
        displayChapter   = ref.chapter,
        displayVerse     = ref.verseStart,
    )
}

internal fun BibleViewModel.logGoLiveCorrection(shownBookIndex: Int, shownChapter: Int, shownVerse: Int?) {
    val top = _detectedReferences.value.firstOrNull() ?: return
    val matches = top.bookIndex == shownBookIndex && top.chapter == shownChapter && top.verseStart == shownVerse
    if (matches) return
    actedDetectionKeys.add(top.key)
    logDetectionOutcome(
        top,
        action = "corrected",
        correctedRef = buildDetectionLabel(shownBookIndex, shownChapter, shownVerse, null),
    )
}

/** A verse going live, as the training log records it. */
data class LiveReference(
    val displayBookIndex: Int,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    val source: String,
    val autoFollow: Boolean,
    val matchType: String? = null,
)
