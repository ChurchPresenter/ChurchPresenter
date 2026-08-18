package engine

import engine.bible.BibleIndex
import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import engine.detection.ReverseLookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleIndexTest {

    private fun fixture(verses: List<EngineVerse>): EngineTranslation {
        val byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) }
        val byChapter = verses.groupBy { it.bookNum to it.chapter }
        val byCode = verses.associateBy { it.code }
        return EngineTranslation(
            id = "TEST", title = "Test", abbreviation = "TST", language = "ENG", numbering = "standard",
            books = listOf(EngineBook(40, "Matthew", 28)),
            byBCV = byBCV, byChapter = byChapter, byCode = byCode,
        )
    }

    private val index = BibleIndex(listOf(fixture(listOf(
        EngineVerse("40-11-28", 40, 11, 28, "придите ко мне все труждающиеся и обремененные и я успокою вас", false),
        EngineVerse("40-11-29", 40, 11, 29, "возьмите иго мое на себя и научитесь от меня", false),
        EngineVerse("40-11-30", 40, 11, 30, "ибо иго мое благо и бремя мое легко", false),
    ))))

    @Test fun `exact query finds the right verse`() {
        val top = index.search("труждающиеся и обремененные я успокою").firstOrNull()
        assertEquals(28, top?.verse?.verse)
    }

    @Test fun `garbled stt token is rescued via stem fuzzy expansion`() {
        // "туждающие" is the real STT garble of "труждающиеся" from an earlier session —
        // 3 raw edits apart (unreachable by plain distance-1) but exactly one edit on the stems.
        val top = index.search("придите ко мне все туждающие обремененные успокою").firstOrNull()
        assertEquals(28, top?.verse?.verse, "garbled туждающие should still find Matthew 11:28")
    }

    @Test fun `fuzzy layer does not change exact-match results`() {
        // Every token exact-matches -> the weighted term groups collapse to the old scoring.
        val exact = index.search("возьмите иго мое на себя и научитесь")
        assertEquals(29, exact.firstOrNull()?.verse?.verse)
        assertTrue(exact.first().score > 0.0)
    }

    @Test fun `all-terms search fails closed when a token matches nothing at all`() {
        assertTrue(index.searchAllTerms("придите ксенобиология обремененные").isEmpty())
    }

    @Test fun `window straddling adjacent verses is not treated as ambiguous`() {
        // The runner-up here is the NEXT verse of the same passage (the window covers both) —
        // the ratio gate must pick a different-chapter competitor instead of suppressing.
        val t = fixture(listOf(
            EngineVerse("40-11-28", 40, 11, 28, "придите ко мне все труждающиеся и обремененные и я успокою вас", false),
            EngineVerse("40-11-29", 40, 11, 29, "возьмите иго мое на себя и научитесь от меня", false),
            EngineVerse("40-5-3", 40, 5, 3, "блаженны нищие духом ибо их есть царство небесное", false),
        ))
        val idx = BibleIndex(listOf(t))
        val window = "христос говорит придите ко мне все туждающие обремененные я успокою вас возьмите иго"
        val result = ReverseLookup.search(window, idx, listOf(t))
        assertEquals(28, result?.verse, "straddled passage must resolve to its first verse, got $result")
    }
}
