package org.churchpresenter.bibleengine.detection

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.BibleIndex
import org.churchpresenter.bibleengine.bible.EngineTranslation

object ReverseLookup {

    /** Fewer distinct terms than this in the window is too little to look a verse up by. */
    private const val MIN_QUERY_TERMS = 3

    data class ReverseResult(
        val translationId: String,
        val bookNum: Int,
        val chapter: Int,
        val verse: Int,
        val text: String,
        val score: Double,
        val confidence: Double,
        // Top-1 / top-2 BM25 score ratio (the margin over the runner-up). Logged for tuning the
        // reverseMinScoreRatio gate. Double.MAX_VALUE when there was no runner-up.
        val ratio: Double = Double.MAX_VALUE,
    )

    fun search(
        query: String,
        index: BibleIndex,
        translations: List<EngineTranslation>,
        topK: Int = Config.reverseTopK,
    ): ReverseResult? {
        val window = query.split(Regex("\\s+")).takeLast(Config.reverseWindowWords).joinToString(" ")
        val queryTerms = index.tokenize(window).toSet()
        if (queryTerms.size < MIN_QUERY_TERMS) return null

        val (rawCandidates, threshold) = candidatePool(index, window, topK) ?: return null

        // Collapse the same verse appearing in multiple translations to a single entry (keeping the
        // best score). Otherwise the top-1/top-2 ratio gate sees two copies of the SAME verse scoring
        // near-identically (ratio ~1.0) and wrongly suppresses a correct detection.
        val candidates = rawCandidates
            .groupBy { Triple(it.verse.bookNum, it.verse.chapter, it.verse.verse) }
            .map { (_, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
        return candidates.firstOrNull()?.let { top -> resultFor(top, candidates, threshold, translations) }
    }

    /**
     * The results to rank and the ratio the winner must clear: first only verses that contain ALL
     * query tokens (precise match) -- every one has every query term, so the BM25 ranking is
     * trusted with no ratio gate -- and failing that a partial match held to a strict ratio to
     * avoid false positives. Null when even the partial match has nothing to compare.
     */
    private fun candidatePool(
        index: BibleIndex,
        window: String,
        topK: Int,
    ): Pair<List<BibleIndex.SearchResult>, Double>? {
        val fullResults = index.searchAllTerms(window, topK)
        if (fullResults.isNotEmpty()) return fullResults to 0.0
        val allResults = index.search(window, topK)
        return if (allResults.size < 2) null else allResults to Config.reverseMinScoreRatio
    }

    /** [top] as the answer, when it clears [threshold] over its competitor in [candidates]. */
    internal fun resultFor(
        top: BibleIndex.SearchResult,
        candidates: List<BibleIndex.SearchResult>,
        threshold: Double,
        translations: List<EngineTranslation>,
    ): ReverseResult? {
        // The ambiguity gate's competitor must be a DIFFERENT passage: when the sliding window
        // straddles two adjacent verses, the neighbor scoring close is evidence FOR the passage,
        // not against it (real case: a window covering Matthew 11:28-29 scored 11:29 at ratio
        // 1.77 and wrongly suppressed 11:28). Same-chapter candidates are skipped when picking
        // the competitor — the same principle as the cross-translation collapse above.
        val competitor = candidates.drop(1).firstOrNull {
            it.verse.bookNum != top.verse.bookNum || it.verse.chapter != top.verse.chapter
        }
        val ratio = if (competitor != null && competitor.score > 0) {
            top.score / competitor.score
        } else {
            Double.MAX_VALUE
        }

        if (ratio < threshold || translations.none { it.id == top.translationId }) return null

        val confidence = when {
            ratio >= 10.0 -> 0.90
            ratio >= 5.0 -> 0.80
            ratio >= 3.0 -> 0.70
            else -> 0.60
        }
        return ReverseResult(
            translationId = top.translationId,
            bookNum = top.verse.bookNum,
            chapter = top.verse.chapter,
            verse = top.verse.verse,
            text = top.verse.text,
            score = top.score,
            confidence = confidence,
            ratio = ratio,
        )
    }
}
