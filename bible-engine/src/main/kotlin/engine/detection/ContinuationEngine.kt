package engine.detection

import engine.Config
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import engine.engine.AgreementScorer
import engine.engine.UtteranceState

object ContinuationEngine {

    data class ContinuationResult(
        val verse: EngineVerse,
        val translation: EngineTranslation,
        val confidence: Double,
    )

    /**
     * Once a book+chapter is known (the sticky), score every verse in that chapter — plus every
     * other chapter visited earlier this service ([UtteranceState.chapterHistory]) — against what
     * was spoken, instead of requiring an explicit verse citation or a prior confirmed verse to
     * advance from (unlike [check], this doesn't need [UtteranceState.lastDetected]). This fills the
     * silence [ReferenceWatcher.emit] leaves when a book+chapter is announced but no verse has ever
     * been read yet, handles jumping more than 3 verses ahead within the same chapter, and — via the
     * history — lets a preacher revisit an earlier passage without restating its book/chapter at all.
     * A margin-over-runner-up gate (mirroring [ReverseLookup]'s ratio gate) keeps this silent rather
     * than guessing when two candidate verses (in the same or different chapters) score too close
     * together; widening the candidate pool to the whole history raises that ambiguity risk, which is
     * exactly why the gate matters here, not less.
     */
    fun checkChapterScope(
        state: UtteranceState,
        translation: EngineTranslation,
        now: Long = System.currentTimeMillis(),
    ): ContinuationResult? {
        val stickyValid = state.watchExpiresAt == 0L || now <= state.watchExpiresAt
        val candidates = buildSet {
            if (stickyValid) {
                val book = state.watchBook
                val chapter = state.watchChapter
                if (book != null && chapter != null) add(book to chapter)
            }
            // Only the most recently visited chapters — the sermon's ACTIVE context. Scanning
            // every chapter touched all service produced two orders of magnitude more junk than
            // hits on real data (94 emissions / 0 TPs in an earlier replay). Off by default —
            // see Config.chapterHistoryEnabled for the evidence.
            if (Config.chapterHistoryEnabled) {
                addAll(state.chapterHistory.toList().takeLast(Config.chapterHistoryMaxCandidates))
            }
        }
        if (candidates.isEmpty()) return null

        val query = "${state.transcript} ${state.translation}".trim()
        if (query.split(Regex("\\s+")).size < 3) return null

        val allVerses = candidates.flatMap { translation.byChapter[it]?.filter { v -> !v.isHeader } ?: emptyList() }
        val scored = allVerses
            .map { it to AgreementScorer.score(it.text, state.transcript, state.translation) }
            .filter { it.second >= Config.chapterScopeMinAgreement }
            .sortedByDescending { it.second }
        val top = scored.getOrNull(0) ?: return null
        val runnerUp = scored.getOrNull(1)
        val ratio = if (runnerUp != null && runnerUp.second > 0) top.second / runnerUp.second else Double.MAX_VALUE
        if (runnerUp != null && ratio < Config.chapterScopeMinRatio) return null // ambiguous — stay silent

        // Verse-side coverage floor (same metric as the sequential check): the winning verse
        // must be substantially present in the window. Stricter when it comes from a chapter
        // OTHER than the current sticky (chapter-history) than from the expected one.
        val isCurrentSticky = stickyValid &&
            top.first.bookNum == state.watchBook && top.first.chapter == state.watchChapter
        val coverage = AgreementScorer.coverage(top.first.text, query)
        val floor = if (isCurrentSticky) Config.chapterScopeMinCoverage else Config.chapterHistoryMinCoverage
        if (coverage < floor) return null

        return ContinuationResult(top.first, translation, top.second.coerceIn(0.55, 0.85))
    }

    fun check(
        state: UtteranceState,
        translations: List<EngineTranslation>,
        now: Long = System.currentTimeMillis(),
    ): ContinuationResult? {
        val lastRef = state.lastDetected ?: return null
        if (now - state.lastDetectedAt > Config.continuationTimeoutMs) return null

        val query = "${state.transcript} ${state.translation}".trim()
        if (query.split(Regex("\\s+")).size < 3) return null

        val t = translations.find { it.id == state.lastTranslationId } ?: return null
        val lastVerse = t.lookupVerse(lastRef.bookNum, lastRef.chapter, lastRef.verseStart)
            ?: return null

        // Look up the next 3 candidate verses. Scored by VERSE-side coverage (how much of the
        // candidate verse is present in the window) — see Config.continuationMinCoverage for why
        // query-side overlap systematically under-scored verbatim verse-by-verse reading.
        var candidate: EngineVerse? = t.nextVerse(lastVerse)
        repeat(3) {
            val c = candidate ?: return@repeat
            val coverage = AgreementScorer.coverage(c.text, query)
            val distinctVerseWords = distinctScoringWords(c.text)
            val floor = if (distinctVerseWords >= 4) Config.continuationMinCoverage else 1.0
            if (coverage >= floor) {
                return ContinuationResult(c, t, coverage.coerceIn(0.5, 0.88))
            }
            candidate = t.nextVerse(c)
        }
        return null
    }

    /** Distinct words the coverage metric would score for [text] — the short-verse guard input. */
    private fun distinctScoringWords(text: String): Int =
        text.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }.toSet().size

}
