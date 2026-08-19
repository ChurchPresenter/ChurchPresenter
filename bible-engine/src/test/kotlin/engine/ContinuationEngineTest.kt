package engine

import engine.Config
import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import engine.detection.ContinuationEngine
import engine.engine.UtteranceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContinuationEngineTest {

    // A tiny synthetic translation with distinguishable verse texts, so checkChapterScope can be
    // tested hermetically without needing real Bible files installed (unlike ReverseLookupTest, which
    // needs the real KJV/RST data to test BM25 quality against actual scripture).
    private fun fixture(verses: List<EngineVerse>): EngineTranslation {
        val byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) }
        val byChapter = verses.groupBy { it.bookNum to it.chapter }
        val byCode = verses.associateBy { it.code }
        return EngineTranslation(
            id = "TEST", title = "Test", abbreviation = "TST", language = "ENG", numbering = "standard",
            books = listOf(EngineBook(9, "1 Samuel", 31)),
            byBCV = byBCV, byChapter = byChapter, byCode = byCode,
        )
    }

    private fun stateWithSticky(book: Int?, chapter: Int?, transcript: String, translation: String = "",
                                expiresAt: Long = System.currentTimeMillis() + 60_000L): UtteranceState {
        val state = UtteranceState(id = "test")
        state.watchBook = book
        state.watchChapter = chapter
        state.watchExpiresAt = expiresAt
        state.transcript = transcript
        state.translation = translation
        return state
    }

    @Test fun `resolves the verse whose text clearly matches what was spoken`() {
        val t = fixture(listOf(
            EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma delta epsilon zeta", false),
            EngineVerse("9-15-23", 9, 15, 23, "eta theta iota kappa lambda mu", false),
        ))
        val state = stateWithSticky(9, 15, "alpha beta gamma delta epsilon zeta")
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertNotNull(result, "expected a chapter-scoped match")
        assertEquals(22, result.verse.verse)
    }

    @Test fun `stays silent when two verses in the chapter score too close together`() {
        // Both verses share the exact same overlap with the query — an ambiguous tie the margin
        // gate must catch rather than guess.
        val t = fixture(listOf(
            EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma delta", false),
            EngineVerse("9-15-23", 9, 15, 23, "alpha beta gamma epsilon", false),
        ))
        val state = stateWithSticky(9, 15, "alpha beta gamma")
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertNull(result, "expected no resolution for an ambiguous tie, got $result")
    }

    @Test fun `no sticky book or chapter returns null`() {
        val t = fixture(listOf(EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma", false)))
        val state = stateWithSticky(null, null, "alpha beta gamma")
        assertNull(ContinuationEngine.checkChapterScope(state, t))
    }

    @Test fun `expired sticky returns null`() {
        val t = fixture(listOf(EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma", false)))
        val state = stateWithSticky(9, 15, "alpha beta gamma", expiresAt = System.currentTimeMillis() - 1_000L)
        assertNull(ContinuationEngine.checkChapterScope(state, t))
    }

    @Test fun `query too short returns null`() {
        val t = fixture(listOf(EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma", false)))
        val state = stateWithSticky(9, 15, "alpha")
        assertNull(ContinuationEngine.checkChapterScope(state, t))
    }

    // ── Chapter history (revisiting an earlier chapter without restating book/chapter) ──────────
    //
    // Retired (`Config.chapterHistoryEnabled`, default false): across ten recorded
    // services the operator never accepted a `chapter-history` suggestion, and the tier produced one
    // true positive across the eight replayable ones. The mechanism is kept behind the flag, so these
    // tests enable it explicitly — and [chapter history is not consulted by default] guards the
    // default that actually ships.

    /** Runs [body] with the retired chapter-history pool switched back on, then restores the flag. */
    private fun withChapterHistory(body: () -> Unit) {
        val previous = Config.chapterHistoryEnabled
        Config.chapterHistoryEnabled = true
        try { body() } finally { Config.chapterHistoryEnabled = previous }
    }

    @Test fun `chapter history is not consulted by default`() {
        val t = fixture(listOf(
            EngineVerse("9-15-1", 9, 15, 1, "gamma delta", false),
            EngineVerse("9-10-1", 9, 10, 1, "alpha beta gamma delta epsilon zeta", false),
        ))
        val state = stateWithSticky(9, 15, "alpha beta gamma delta epsilon zeta")
        state.touchChapterHistory(9, 10)
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertTrue(
            result == null || result.verse.chapter == 15,
            "with the tier retired, only the current sticky chapter may resolve, got $result",
        )
    }

    @Test fun `resolves an earlier chapter from history when it matches far better than the current sticky`() = withChapterHistory {
        val t = fixture(listOf(
            EngineVerse("9-15-1", 9, 15, 1, "gamma delta", false),
            EngineVerse("9-10-1", 9, 10, 1, "alpha beta gamma delta epsilon zeta", false),
        ))
        val state = stateWithSticky(9, 15, "alpha beta gamma delta epsilon zeta")
        state.touchChapterHistory(9, 10)
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertNotNull(result, "expected a match from chapter history")
        assertEquals(9, result.verse.bookNum)
        assertEquals(10, result.verse.chapter)
    }

    @Test fun `stays silent when the current sticky and a historical chapter score equally well`() = withChapterHistory {
        val t = fixture(listOf(
            EngineVerse("9-15-1", 9, 15, 1, "alpha beta gamma", false),
            EngineVerse("9-10-1", 9, 10, 1, "alpha beta gamma", false),
        ))
        val state = stateWithSticky(9, 15, "alpha beta gamma")
        state.touchChapterHistory(9, 10)
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertNull(result, "expected no resolution for an ambiguous cross-chapter tie, got $result")
    }

    @Test fun `no history still resolves against the current sticky alone`() {
        // Regression guard: an empty chapterHistory (the common case) must behave exactly like Part 4.
        val t = fixture(listOf(EngineVerse("9-15-22", 9, 15, 22, "alpha beta gamma delta", false)))
        val state = stateWithSticky(9, 15, "alpha beta gamma delta")
        assertEquals(0, state.chapterHistory.size)
        val result = ContinuationEngine.checkChapterScope(state, t)
        assertNotNull(result)
        assertEquals(22, result.verse.verse)
    }

    @Test fun `touchChapterHistory dedups and moves the touched entry to most-recent`() {
        val state = UtteranceState(id = "test")
        state.touchChapterHistory(9, 15)
        state.touchChapterHistory(40, 3)
        state.touchChapterHistory(9, 15) // touch again — must move to the end, not duplicate
        assertEquals(listOf(40 to 3, 9 to 15), state.chapterHistory.toList())
    }

    // ── Sequential check: verse-side coverage ──────────

    private fun stateWithLastDetected(t: EngineTranslation, book: Int, chapter: Int, verse: Int,
                                      transcript: String, now: Long): UtteranceState {
        val state = UtteranceState(id = "test")
        state.lastDetected = engine.engine.UtteranceLastRef(book, chapter, verse)
        state.lastDetectedAt = now
        state.lastTranslationId = t.id
        state.transcript = transcript
        return state
    }

    @Test fun `verse fully read inside a padded window matches sequentially`() {
        // The old query-normalized overlap scored ~0.43 here (6 verse words / 14 window words)
        // and failed the 0.35... actually passed 0.35 — use a wider window to model the real
        // failure: 6/18 ≈ 0.33 < 0.35 failed, while verse-side coverage is 1.0.
        val t = fixture(listOf(
            EngineVerse("9-15-22", 9, 15, 22, "previous verse words here now", false),
            EngineVerse("9-15-23", 9, 15, 23, "alpha beta gamma delta epsilon zeta", false),
        ))
        val now = 1_000_000L
        val window = "one two three four five six seven eight nine ten eleven twelve " +
            "alpha beta gamma delta epsilon zeta"
        val state = stateWithLastDetected(t, 9, 15, 22, window, now)
        val result = ContinuationEngine.check(state, listOf(t), now + 5_000)
        assertNotNull(result, "verse fully present in a padded window must match")
        assertEquals(23, result.verse.verse)
    }

    @Test fun `short verses require full coverage`() {
        // 3 distinct scoring words -> floor is 1.0; two of three present must NOT match.
        val t = fixture(listOf(
            EngineVerse("9-15-22", 9, 15, 22, "previous verse words here now", false),
            EngineVerse("9-15-23", 9, 15, 23, "alpha beta gamma", false),
        ))
        val now = 1_000_000L
        val state = stateWithLastDetected(t, 9, 15, 22, "some prose alpha beta only", now)
        assertNull(ContinuationEngine.check(state, listOf(t), now + 5_000))
    }

    // ── "Verse speed" user knob: Config.applyContinuationSpeed ──────────────────

    @Test fun `applyContinuationSpeed changes only continuationMinCoverage`() {
        try {
            Config.applyContinuationSpeed("balanced")
            assertEquals(0.5, Config.continuationMinCoverage)
            assertEquals("balanced", Config.continuationSpeed)

            Config.applyContinuationSpeed("fast")
            assertEquals(0.45, Config.continuationMinCoverage)
            assertEquals("fast", Config.continuationSpeed)

            // Unrecognized name is a silent no-op, matching applyLevel's existing behavior.
            Config.applyContinuationSpeed("warpspeed")
            assertEquals(0.45, Config.continuationMinCoverage, "unrecognized preset must not change the floor")
        } finally {
            Config.applyContinuationSpeed("balanced")
        }
    }

    @Test fun `a verse that fails the balanced floor passes at the fast preset`() {
        // 20 distinct >=3-char verse words (>=4, so the floor isn't forced to 1.0), 9 of them
        // present in the window: coverage = 9/20 = 0.45 exactly — below balanced's 0.5, at
        // fast's 0.45 floor (>= is inclusive).
        val verseWords = "aaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo ppp qqq rrr sss ttt"
        val t = fixture(listOf(
            EngineVerse("9-15-22", 9, 15, 22, "previous verse words here now", false),
            EngineVerse("9-15-23", 9, 15, 23, verseWords, false),
        ))
        val now = 1_000_000L
        val window = "aaa bbb ccc ddd eee fff ggg hhh iii filler1 filler2 filler3"
        val state = stateWithLastDetected(t, 9, 15, 22, window, now)
        try {
            Config.applyContinuationSpeed("balanced")
            assertNull(ContinuationEngine.check(state, listOf(t), now + 5_000),
                "0.45 coverage must fail the balanced (0.5) floor")

            Config.applyContinuationSpeed("fast")
            val result = ContinuationEngine.check(state, listOf(t), now + 5_000)
            assertNotNull(result, "0.45 coverage must pass the fast (0.45) floor")
            assertEquals(23, result.verse.verse)
        } finally {
            Config.applyContinuationSpeed("balanced")
        }
    }
}
