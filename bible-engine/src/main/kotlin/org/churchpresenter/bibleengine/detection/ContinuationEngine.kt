package org.churchpresenter.bibleengine.detection

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.engine.AgreementScorer
import org.churchpresenter.bibleengine.engine.UtteranceState

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
        val candidates = chapterCandidates(state, stickyValid)
        val query = "${state.transcript} ${state.translation}".trim()
        if (candidates.isEmpty() || wordCount(query) < MIN_QUERY_WORDS) return null

        val top = unambiguousTop(candidates, translation, state) ?: return null

        // Verse-side coverage floor (same metric as the sequential check): the winning verse
        // must be substantially present in the window. Stricter when it comes from a chapter
        // OTHER than the current sticky (chapter-history) than from the expected one.
        val isCurrentSticky = stickyValid &&
            top.first.bookNum == state.watchBook && top.first.chapter == state.watchChapter
        val coverage = AgreementScorer.coverage(top.first.text, query)
        val floor = if (isCurrentSticky) Config.chapterScopeMinCoverage else Config.chapterHistoryMinCoverage
        return if (coverage < floor) {
            null
        } else {
            ContinuationResult(top.first, translation, top.second.coerceIn(SCOPE_MIN_CONFIDENCE, SCOPE_MAX_CONFIDENCE))
        }
    }

    /** The chapters [checkChapterScope] scores: the live sticky's, then the recently visited ones. */
    private fun chapterCandidates(state: UtteranceState, stickyValid: Boolean): Set<Pair<Int, Int>> = buildSet {
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

    /**
     * The best-agreeing verse in [candidates] with its score, or null when nothing agrees enough
     * or the runner-up scores too close to it to tell them apart — ambiguous, so stay silent.
     */
    private fun unambiguousTop(
        candidates: Set<Pair<Int, Int>>,
        translation: EngineTranslation,
        state: UtteranceState,
    ): Pair<EngineVerse, Double>? {
        val allVerses = candidates.flatMap { translation.byChapter[it]?.filter { v -> !v.isHeader } ?: emptyList() }
        val scored = allVerses
            .map { it to AgreementScorer.score(it.text, state.transcript, state.translation) }
            .filter { it.second >= Config.chapterScopeMinAgreement }
            .sortedByDescending { it.second }
        val top = scored.getOrNull(0) ?: return null
        val runnerUp = scored.getOrNull(1)
        val ratio = if (runnerUp != null && runnerUp.second > 0) top.second / runnerUp.second else Double.MAX_VALUE
        return top.takeUnless { runnerUp != null && ratio < Config.chapterScopeMinRatio }
    }

    fun check(
        state: UtteranceState,
        translations: List<EngineTranslation>,
        now: Long = System.currentTimeMillis(),
    ): ContinuationResult? {
        val lastRef = state.lastDetected ?: return null
        if (now - state.lastDetectedAt > Config.continuationTimeoutMs) return null

        val query = "${state.transcript} ${state.translation}".trim()
        val t = translations.find { it.id == state.lastTranslationId }
        val lastVerse = t?.lookupVerse(lastRef.bookNum, lastRef.chapter, lastRef.verseStart)
        return if (t == null || lastVerse == null || wordCount(query) < MIN_QUERY_WORDS) {
            null
        } else {
            nextCoveredVerse(t, lastVerse, query)
        }
    }

    /**
     * The first of the [NEXT_VERSES_LOOKED_AT] verses after [lastVerse] substantially present in
     * [query]. Scored by VERSE-side coverage (how much of the candidate verse is present in the
     * window) — see Config.continuationMinCoverage for why query-side overlap systematically
     * under-scored verbatim verse-by-verse reading.
     */
    private fun nextCoveredVerse(t: EngineTranslation, lastVerse: EngineVerse, query: String): ContinuationResult? =
        generateSequence(t.nextVerse(lastVerse)) { t.nextVerse(it) }
            .take(NEXT_VERSES_LOOKED_AT)
            .map { c -> c to AgreementScorer.coverage(c.text, query) }
            .firstOrNull { (c, coverage) ->
                val floor = if (distinctScoringWords(c.text) >= SHORT_VERSE_DISTINCT_WORDS) {
                    Config.continuationMinCoverage
                } else {
                    1.0
                }
                coverage >= floor
            }
            ?.let { (c, coverage) ->
                ContinuationResult(c, t, coverage.coerceIn(NEXT_MIN_CONFIDENCE, NEXT_MAX_CONFIDENCE))
            }

    private fun wordCount(query: String): Int = query.split(Regex("\\s+")).size

    /** Distinct words the coverage metric would score for [text] — the short-verse guard input. */
    private fun distinctScoringWords(text: String): Int =
        text.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+")).filter { it.length >= MIN_SCORED_WORD_LENGTH }
            .toSet().size

    /** Fewer words than this in the window is too little to score a verse against. */
    private const val MIN_QUERY_WORDS = 3

    /** How many verses past the last detected one the sequential check looks at. */
    private const val NEXT_VERSES_LOOKED_AT = 3

    /** A verse with fewer distinct scored words must be present in full, not just mostly. */
    private const val SHORT_VERSE_DISTINCT_WORDS = 4

    /** Words shorter than this are not scored. */
    private const val MIN_SCORED_WORD_LENGTH = 3

    private const val SCOPE_MIN_CONFIDENCE = 0.55
    private const val SCOPE_MAX_CONFIDENCE = 0.85
    private const val NEXT_MIN_CONFIDENCE = 0.5
    private const val NEXT_MAX_CONFIDENCE = 0.88
}
