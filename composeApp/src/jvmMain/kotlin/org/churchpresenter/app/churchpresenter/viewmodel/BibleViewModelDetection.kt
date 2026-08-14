package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.flow.first
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger

/**
 * Speech-driven detection: engine events in, detected references out, and the training-log
 * writes that record what the operator did with them.
 */

internal fun BibleViewModel.canonicalBookIdForDisplayIndex(displayIndex: Int): Int =
    _primaryBible.value?.getBookId(displayIndex) ?: (displayIndex + 1)

internal fun BibleViewModel.canonicalRefForDisplay(displayBookIndex: Int, chapter: Int, verse: Int?): Triple<Int, Int, Int?>? {
    val bible = _primaryBible.value ?: return null
    val bookId = bible.getBookId(displayBookIndex)
    val code = bible.getCodeReference(bookId, chapter, verse ?: 1) ?: return null
    return Triple(code.first, code.second, verse?.let { code.third })
}

internal fun BibleViewModel.displayIndexForBookName(bookName: String): Int =
    _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }

internal fun BibleViewModel.canonicalRefForBookName(bookName: String, chapter: Int, verse: Int): Triple<Int, Int, Int>? {
    val displayIndex = displayIndexForBookName(bookName).takeIf { it >= 0 } ?: return null
    val (book, mappedChapter, mappedVerse) =
        canonicalRefForDisplay(displayIndex, chapter, verse) ?: return null
    return mappedVerse?.let { Triple(book, mappedChapter, it) }
}

internal fun BibleViewModel.logLiveReference(
    displayBookIndex: Int,
    chapter: Int,
    verseStart: Int?,
    verseEnd: Int?,
    source: String,
    autoFollow: Boolean,
    matchType: String? = null,
) {
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

internal fun BibleViewModel.onEngineScripture(
    bookId: Int,
    chapter: Int,
    verseStart: Int,
    verseEnd: Int?,
    verseText: String,
    matchType: String,
    canonicalCodeStart: String? = null,
    canonicalCodeEnd: String? = null,
    segmentId: String? = null,
    sessionId: String? = null,
    tracks: List<String> = emptyList(),
    detectedVersion: String? = null,
) {

    if (segmentId != null) _lastDetectionSegmentId = segmentId

    if (sessionId != null) {
        _lastSessionId = sessionId
        TrainingDataLogger.sessionId = sessionId
    }

    val bible = _primaryBible.value ?: return
    val codeStart = canonicalCodeStart?.let { bible.parseVerseCode(it) }
    val codeBook = codeStart?.first ?: bookId
    val bookIndex = bible.getDisplayIndexForBookId(codeBook).takeIf { it in _books.value.indices } ?: return
    val (dispChapter, dispVerseStart) =
        if (codeStart != null)
            bible.getVerseDetailsByCode(codeStart.first, codeStart.second, codeStart.third)
                ?.let { it.displayChapter to it.displayVerse } ?: (chapter to verseStart)
        else chapter to verseStart
    val dispVerseEnd = canonicalCodeEnd?.let { bible.parseVerseCode(it) }
        ?.let { bible.getVerseDetailsByCode(it.first, it.second, it.third)?.displayVerse }
        ?: verseEnd

    val source = when (matchType) {
        "explicit" -> DetectionSource.EXPLICIT
        "continuation" -> DetectionSource.CONTINUATION
        "chapter-scan" -> DetectionSource.CHAPTER_SCAN
        "chapter-history" -> DetectionSource.CHAPTER_HISTORY
        else -> DetectionSource.REVERSE
    }
    val trackSet = tracks.mapNotNull {
        when (it) {
            "transcription" -> DetectionTrack.TRANSCRIPTION
            "translation" -> DetectionTrack.TRANSLATION
            else -> null
        }
    }.toSet()
    val vEnd = dispVerseEnd?.takeIf { it > dispVerseStart }
    val label = buildDetectionLabel(bookIndex, dispChapter, dispVerseStart, vEnd)
    val key = "$bookIndex|$dispChapter|$dispVerseStart|$vEnd"
    val added = addDetection(
        DetectedReference(
            bookIndex = bookIndex,
            chapter = dispChapter,
            verseStart = dispVerseStart,
            verseEnd = vEnd,
            label = label,
            key = key,
            sources = setOf(source),
            tracks = trackSet,

            verseText = verseTextFor(bookIndex, dispChapter, dispVerseStart) ?: verseText.ifBlank { null },
            detectedVersion = detectedVersion,
        )
    )
    if (added && _autoFollowEnabled.value) {

        val instantGoLive = matchType == "explicit" || matchType == "continuation" ||
            matchType == "chapter-scan"
        navigateToReference(
            SmartReference(bookIndex, dispChapter, dispVerseStart, verseEnd = null),
            goLive = instantGoLive,
            matchType = matchType,
        )
    }
}


internal fun BibleViewModel.addDetection(ref: DetectedReference): Boolean {
    val list = _detectedReferences.value
    val idx = list.indexOfFirst { it.key == ref.key }
    if (idx >= 0) {
        val merged = list[idx].sources + ref.sources

        val mergedTracks = list[idx].tracks + ref.tracks
        val verseText = list[idx].verseText ?: ref.verseText

        val version = ref.detectedVersion ?: list[idx].detectedVersion
        val previous = list[idx]
        val sourcesChanged = merged != previous.sources || mergedTracks != previous.tracks
        val textChanged = verseText != previous.verseText || version != previous.detectedVersion
        if (sourcesChanged || textChanged) {
            _detectedReferences.value = list.toMutableList().also {
                it[idx] = list[idx].copy(
                    sources = merged, tracks = mergedTracks,
                    verseText = verseText, detectedVersion = version,
                )
            }
        }
        return false
    }
    if (recentDetectionKeys.contains(ref.key)) return false
    recentDetectionKeys.addLast(ref.key)
    while (recentDetectionKeys.size > BibleViewModel.DETECTION_DEDUPE_WINDOW) recentDetectionKeys.removeFirst()
    val next = listOf(ref) + list

    next.drop(BibleViewModel.MAX_DETECTED).forEach { evicted ->
        if (evicted.key !in actedDetectionKeys) {
            logDetectionOutcome(evicted, action = "ignored")
        }
        actedDetectionKeys.remove(evicted.key)
    }
    _detectedReferences.value = next.take(BibleViewModel.MAX_DETECTED)
    return true
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

internal fun BibleViewModel.applyDetectedReference(ref: DetectedReference, goLiveSource: String? = null) {
    val matchType = ref.matchTypeLabel()
    actedDetectionKeys.add(ref.key)
    logDetectionOutcome(ref, action = "accepted")

    navigateToReference(
        SmartReference(ref.bookIndex, ref.chapter, ref.verseStart, verseEnd = null),
        goLive = goLiveSource != null,
        goLiveSource = goLiveSource ?: "auto",
        matchType = matchType,
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

internal fun BibleViewModel.clearDetectedReferences(reason: String = "dismissed") {
    _detectedReferences.value.forEach { ref ->
        if (ref.key in actedDetectionKeys) return@forEach
        logDetectionOutcome(ref, action = reason)
    }
    if (_detectedReferences.value.isNotEmpty()) _detectedReferences.value = emptyList()
    recentDetectionKeys.clear()
    actedDetectionKeys.clear()
}

internal fun BibleViewModel.onEngineVersion(version: String?) {
    if (version == null) return
    val list = _detectedReferences.value
    if (list.none { it.detectedVersion == null }) return
    _detectedReferences.value = list.map {
        if (it.detectedVersion == null) it.copy(detectedVersion = version) else it
    }
}

internal fun BibleViewModel.verseTextFor(bookIndex: Int, chapter: Int, verse: Int?): String? {
    if (verse == null) return null
    val bible = _primaryBible.value ?: return null
    return bible.getVerseDetails(bible.getBookId(bookIndex), chapter, verse)?.second
}

internal fun BibleViewModel.buildDetectionLabel(bookIndex: Int, chapter: Int, vs: Int?, ve: Int?): String {
    val bookName = _books.value.getOrNull(bookIndex) ?: return "$chapter"
    val versePart = when {
        vs != null && ve != null && ve > vs -> ":$vs-$ve"
        vs != null -> ":$vs"
        else -> ""
    }
    return "$bookName $chapter$versePart"
}
