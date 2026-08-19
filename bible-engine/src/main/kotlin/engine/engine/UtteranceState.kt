package engine.engine

import engine.detection.ReferenceWatcher

data class UtteranceLastRef(
    val bookNum: Int,
    val chapter: Int,
    val verseStart: Int,
)

data class UtteranceState(
    val id: String,
    var transcript: String = "",
    var translation: String = "",
    var lastDetected: UtteranceLastRef? = null,
    var lastDetectedAt: Long = 0L,
    var lastTranslationId: String = "",
    var lastConfidence: Double = 0.0,
    var updatedAt: Long = System.currentTimeMillis(),
    // Most recent STT speech_type for this stream (e.g. "Speaking"/"Quiet"/"Music"); drives the
    // music precision gate in ReferenceWatcher. Null = unknown (treated as not music).
    var speechType: String? = null,
    // Most recent STT segment_id + session-relative start_time that fed this utterance. Stamped onto
    // every emitted detection so transcripts, detections, and displays all reference the same STT
    // segment — clock-free cross-machine correlation (no NTP/wall-clock needed). Null until the STT
    // stream provides them.
    var segmentId: String? = null,
    var sttStartTime: Double? = null,
    // Stable per-service session id from STT (db base name or UUID). Stamped onto every emitted
    // detection so all three artifacts (STT db, detection-log, live-references) share an exact join
    // key, and used to key the detection-log filename. Null until the STT stream provides it.
    var sessionId: String? = null,
    // ── Sticky reference context (ReferenceWatcher) ──
    // The most recently announced book + chapter, carried across utterances so a later bare
    // "N стих" (verse-by-verse reading) resolves against it. Expires after Config.stickyTtlMs.
    override var watchBook: Int? = null,
    override var watchChapter: Int? = null,
    override var watchExpiresAt: Long = 0L,
    // Every distinct (book, chapter) visited this service (announced or confirmed), most-recently-
    // touched last, deduplicated — lets a later verse mention resolve against ANY chapter visited
    // this session (see ContinuationEngine.checkChapterScope), not just the current sticky. Same-
    // service only (cleared only by a process restart); intentionally unbounded — a sermon touching
    // 20-30 chapters is trivial memory — unlike the sticky above, entries here have no TTL.
    val chapterHistory: LinkedHashSet<Pair<Int, Int>> = LinkedHashSet(),
) : ReferenceWatcher.Sticky {
    fun touchChapterHistory(book: Int, chapter: Int) {
        val key = book to chapter
        chapterHistory.remove(key) // re-insert at the end so it's most-recent, not duplicated
        chapterHistory.add(key)
    }
}
