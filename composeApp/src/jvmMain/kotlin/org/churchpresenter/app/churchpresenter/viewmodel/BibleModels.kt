package org.churchpresenter.app.churchpresenter.viewmodel


enum class BibleSearchMode { AUTO, REFERENCE, TEXT }

enum class DetectionSource { EXPLICIT, REVERSE, CONTINUATION, CHAPTER_SCAN, CHAPTER_HISTORY }

enum class DetectionTrack { TRANSCRIPTION, TRANSLATION }

enum class TextMatchLevel { OFF, CONSERVATIVE, BALANCED, AGGRESSIVE }

enum class ContinuationSpeed { BALANCED, FAST }

data class DetectedReference(
    val bookIndex: Int,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    val label: String,
    val key: String,
    val sources: Set<DetectionSource> = emptySet(),
    val tracks: Set<DetectionTrack> = emptySet(),
    val verseText: String? = null,

    val detectedVersion: String? = null,
)

internal fun DetectionSource.toMatchTypeLabel(): String = when (this) {
    DetectionSource.EXPLICIT -> "explicit"
    DetectionSource.CONTINUATION -> "continuation"
    DetectionSource.CHAPTER_SCAN -> "chapter-scan"
    DetectionSource.CHAPTER_HISTORY -> "chapter-history"
    DetectionSource.REVERSE -> "reverse"
}

internal fun DetectedReference.matchTypeLabel(): String? =
    sources.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toMatchTypeLabel() }
