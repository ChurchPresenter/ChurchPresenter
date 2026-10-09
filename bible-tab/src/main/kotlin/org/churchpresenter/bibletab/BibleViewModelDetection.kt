package org.churchpresenter.bibletab

import org.churchpresenter.sharedui.utils.UsageEvent
import kotlinx.coroutines.flow.first
import org.churchpresenter.bible.Bible
import org.churchpresenter.sharedui.utils.TrainingDataLogger

/**
 * Speech-driven detection: engine events in, detected references out, and the training-log
 * writes that record what the operator did with them.
 */

fun BibleViewModel.onEngineScripture(scripture: EngineScripture) {
    val bookId = scripture.bookId
    val chapter = scripture.chapter
    val verseStart = scripture.verseStart
    val verseEnd = scripture.verseEnd
    val verseText = scripture.verseText
    val matchType = scripture.matchType
    val canonicalCodeStart = scripture.canonicalCodeStart
    val canonicalCodeEnd = scripture.canonicalCodeEnd
    val segmentId = scripture.segmentId
    val sessionId = scripture.sessionId
    val tracks = scripture.tracks
    val detectedVersion = scripture.detectedVersion

    if (segmentId != null) _lastDetectionSegmentId = segmentId

    if (sessionId != null) {
        _lastSessionId = sessionId
        TrainingDataLogger.sessionId = sessionId
    }

    val bible = _primaryBible.value ?: return
    val codeStart = canonicalCodeStart?.let { bible.parseVerseCode(it) }
    val codeBook = codeStart?.first ?: bookId
    val bookIndex = bible.getDisplayIndexForBookId(codeBook).takeIf { it in _books.value.indices } ?: return
    val (dispChapter, dispVerseStart) = bible.displayPositionOf(codeStart, chapter, verseStart)
    val dispVerseEnd = canonicalCodeEnd?.let { bible.parseVerseCode(it) }
        ?.let { bible.getVerseDetailsByCode(it.first, it.second, it.third)?.displayVerse }
        ?: verseEnd

    val source = detectionSourceOf(matchType)
    val trackSet = detectionTracksOf(tracks)
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
    if (added) usage.record(UsageEvent.BIBLE_REFERENCE_DETECTED)
    if (added && _autoFollowEnabled.value) {
        usage.record(UsageEvent.BIBLE_REFERENCE_AUTO_FOLLOWED)

        val instantGoLive = detectionSourceOf(matchType) in INSTANT_GO_LIVE_SOURCES
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

internal fun BibleViewModel.applyDetectedReference(ref: DetectedReference, goLiveSource: String? = null) {
    val matchType = ref.matchTypeLabel()
    actedDetectionKeys.add(ref.key)
    logDetectionOutcome(ref, action = "accepted")
    usage.record(UsageEvent.BIBLE_REFERENCE_ACCEPTED)

    navigateToReference(
        SmartReference(ref.bookIndex, ref.chapter, ref.verseStart, verseEnd = null),
        goLive = goLiveSource != null,
        goLiveSource = goLiveSource ?: "auto",
        matchType = matchType,
    )
}

fun BibleViewModel.clearDetectedReferences(reason: String = "dismissed") {
    _detectedReferences.value.forEach { ref ->
        if (ref.key in actedDetectionKeys) return@forEach
        logDetectionOutcome(ref, action = reason)
    }
    if (_detectedReferences.value.isNotEmpty()) _detectedReferences.value = emptyList()
    recentDetectionKeys.clear()
    actedDetectionKeys.clear()
}

fun BibleViewModel.onEngineVersion(version: String?) {
    if (version == null) return
    val list = _detectedReferences.value
    if (list.none { it.detectedVersion == null }) return
    _detectedReferences.value = list.map {
        if (it.detectedVersion == null) it.copy(detectedVersion = version) else it
    }
}

/** The detections clear enough to go straight to the screen rather than only being staged. */
private val INSTANT_GO_LIVE_SOURCES = setOf(
    DetectionSource.EXPLICIT,
    DetectionSource.CONTINUATION,
    DetectionSource.CHAPTER_SCAN,
)

/** The engine's matchType as the enum the UI tiers confidence by. */
private fun detectionSourceOf(matchType: String): DetectionSource = when (matchType) {
    "explicit" -> DetectionSource.EXPLICIT
    "continuation" -> DetectionSource.CONTINUATION
    "chapter-scan" -> DetectionSource.CHAPTER_SCAN
    "chapter-history" -> DetectionSource.CHAPTER_HISTORY
    else -> DetectionSource.REVERSE
}

private fun detectionTracksOf(tracks: List<String>): Set<DetectionTrack> = tracks.mapNotNull {
    when (it) {
        "transcription" -> DetectionTrack.TRANSCRIPTION
        "translation" -> DetectionTrack.TRANSLATION
        else -> null
    }
}.toSet()

/** The module's own chapter/verse for a canonical code, falling back to what the engine sent. */
private fun Bible.displayPositionOf(
    codeStart: Triple<Int, Int, Int>?,
    chapter: Int,
    verseStart: Int,
): Pair<Int, Int> {
    if (codeStart == null) return chapter to verseStart
    return getVerseDetailsByCode(codeStart.first, codeStart.second, codeStart.third)
        ?.let { it.displayChapter to it.displayVerse }
        ?: (chapter to verseStart)
}

/**
 * One `scripture.*` event from the Bible Lookup Engine, decoded.
 *
 * A data class rather than a positional parameter list because the payload carries several adjacent
 * nullable strings the compiler cannot tell apart — [canonicalCodeStart], [canonicalCodeEnd],
 * [segmentId], [sessionId], [detectedVersion]. A transposition among them would compile cleanly and
 * silently corrupt the training-log join keys.
 *
 * [canonicalCodeStart]/[canonicalCodeEnd] are the engine's numbering-independent internal codes
 * (`BXXXCXXXVXXX`), forwarded so the CP side can land the reference in the primary Bible's own
 * display numbering (book order + Psalm numbering). [segmentId] is the STT segment that triggered the
 * detection (clock-free correlation key), or null when the STT stream didn't provide one. [sessionId]
 * is the stable per-service session id from STT — the exact join key that ties the STT db, the engine
 * detection-log and the CP live-references log, and keys the live-references filename. [tracks] is
 * the subset of {"transcription","translation"} that corroborated the detection.
 *
 * [detectedVersion] is which translation the engine believes is being *read aloud* (a label such as
 * "NASB", scored across every bible in the folder) — informational only, frequently not one of the
 * two bibles CP has loaded, and never the source of [verseText].
 */
data class EngineScripture(
    val bookId: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val verseText: String,
    val matchType: String,
    val canonicalCodeStart: String? = null,
    val canonicalCodeEnd: String? = null,
    val segmentId: String? = null,
    val sessionId: String? = null,
    val tracks: List<String> = emptyList(),
    val detectedVersion: String? = null,
)
