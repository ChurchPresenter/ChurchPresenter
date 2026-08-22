package org.churchpresenter.dictionary

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * The index behind the dictionary tab's "where does this word appear?" panel.
 *
 * Two bundled files — 4 MB of Greek and 8 MB of Hebrew — are read once and turned into several
 * lookup tables: by Strong's number, by book, by chapter and by verse. Everything the tab offers is
 * a read from one of those tables, and which table gets consulted is decided by the shape of what
 * was asked: a number beginning with G goes to the Greek side, and the passage filters try Greek
 * first and fall back to Hebrew.
 */
class InterlinearRepositoryTest {

    private val files = RecordingFiles()

    /** A repository with both testaments indexed. */
    private fun loaded() = files.repository().also {
        runBlocking {
            it.ensureGreekLoaded()
            it.ensureHebrewLoaded()
        }
    }

    private fun InterlinearRepository.refsFor(number: String) = getVersesForEntry(number).map { it.ref }

    // ── Before anything is loaded ───────────────────────────────────────────────

    @Test
    fun `nothing is known until the data is read`() {
        val repository = files.repository()

        assertTrue(repository.getVersesForEntry("G26").isEmpty())
        assertTrue(repository.getBooksWithGreekData().isEmpty())
        assertTrue(repository.getBooksWithHebrewData().isEmpty())
        assertTrue(repository.getChaptersForBook(43).isEmpty())
        assertTrue(repository.getVersesInChapter(43, 3).isEmpty())
        assertTrue(repository.getStrongsForBookChapter(43, null).isEmpty())
        assertEquals(0, files.greekReads, "construction alone must not read anything")
        assertEquals(0, files.hebrewReads)
    }

    // ── Finding a word ──────────────────────────────────────────────────────────

    @Test
    fun `a greek word lists every verse it appears in`() {
        assertEquals(listOf("043003016", "040005003"), loaded().refsFor("G26"))
    }

    @Test
    fun `a hebrew word lists every verse it appears in`() {
        assertEquals(listOf("001001001", "019023001"), loaded().refsFor("H430"))
    }

    @Test
    fun `the testament is chosen by the number's own prefix`() {
        val repository = loaded()

        assertTrue(repository.refsFor("G26").all { it.startsWith("04") }, "G numbers are New Testament")
        assertTrue(repository.refsFor("H430").none { it.startsWith("04") }, "H numbers are Old Testament")
    }

    @Test
    fun `a word that appears nowhere lists nothing`() {
        val repository = loaded()

        assertTrue(repository.getVersesForEntry("G9999").isEmpty())
        assertTrue(repository.getVersesForEntry("H9999").isEmpty())
    }

    @Test
    fun `a verse is listed once per word, with its words attached`() {
        val john316 = loaded().getVersesForEntry("G26").first { it.ref == "043003016" }

        assertEquals(listOf("G26", "G2316"), john316.words.map { it.strongsNumber })
        assertEquals(43, john316.bookId)
        assertEquals(3, john316.chapter)
        assertEquals(16, john316.verseNumber)
    }

    // ── The passage filters ─────────────────────────────────────────────────────

    @Test
    fun `the books with data are listed in canonical order`() {
        val repository = loaded()

        assertEquals(listOf(40, 43), repository.getBooksWithGreekData())
        assertEquals(listOf(1, 19), repository.getBooksWithHebrewData())
    }

    @Test
    fun `the chapters of a book are listed`() {
        val repository = loaded()

        assertEquals(listOf(3), repository.getChaptersForBook(43), "a New Testament book reads from the Greek side")
        assertEquals(listOf(1), repository.getChaptersForBook(1), "an Old Testament book falls through to Hebrew")
    }

    @Test
    fun `a book with no data has no chapters`() {
        assertTrue(loaded().getChaptersForBook(66).isEmpty())
    }

    @Test
    fun `the verses of a chapter are listed in order`() {
        assertEquals(listOf(16, 17), loaded().getVersesInChapter(43, 3))
    }

    @Test
    fun `a chapter with no data has no verses`() {
        assertTrue(loaded().getVersesInChapter(43, 99).isEmpty())
    }

    // ── Narrowing the word list to a passage ────────────────────────────────────

    @Test
    fun `a whole book offers every word used in it`() {
        val repository = loaded()

        assertEquals(setOf("G26", "G2316"), repository.getStrongsForBookChapter(43, null))
        assertEquals(setOf("H430", "H7225"), repository.getStrongsForBookChapter(1, null))
    }

    @Test
    fun `a chapter narrows the word list`() {
        assertEquals(setOf("G26"), loaded().getStrongsForBookChapter(40, 5))
    }

    @Test
    fun `a single verse narrows it further`() {
        val repository = loaded()

        assertEquals(setOf("G26", "G2316"), repository.getStrongsForBookChapter(43, 3, 16))
        assertEquals(setOf("G2316"), repository.getStrongsForBookChapter(43, 3, 17), "verse 17 does not use G26")
    }

    @Test
    fun `a passage with no data offers no words`() {
        val repository = loaded()

        assertTrue(repository.getStrongsForBookChapter(66, null).isEmpty())
        assertTrue(repository.getStrongsForBookChapter(43, 99).isEmpty())
        assertTrue(repository.getStrongsForBookChapter(43, 3, 99).isEmpty())
    }

    // ── Reading the files once ──────────────────────────────────────────────────

    @Test
    fun `each testament is read only once`() {
        val repository = files.repository()

        runBlocking {
            repository.ensureGreekLoaded()
            repository.ensureGreekLoaded()
            repository.ensureGreekLoaded()
        }

        assertEquals(1, files.greekReads)
    }

    @Test
    fun `loading one testament does not load the other`() {
        val repository = files.repository()

        runBlocking { repository.ensureGreekLoaded() }

        assertTrue(repository.getBooksWithGreekData().isNotEmpty())
        assertTrue(
            repository.getBooksWithHebrewData().isEmpty(),
            "the Hebrew file is 8 MB — a Greek-only lookup must not pay for it",
        )
        assertEquals(0, files.hebrewReads)
    }

    @Test
    fun `two repositories keep their own indexes`() {
        val first = loaded()
        val second = files.repository()

        assertTrue(first.getBooksWithGreekData().isNotEmpty())
        assertTrue(second.getBooksWithGreekData().isEmpty())
    }

    /**
     * Documents a KNOWN GAP: the "already loading" flag is set before the read and never cleared if
     * that read fails, so one transient failure disables the interlinear data for the rest of the
     * session — every later call returns immediately, reporting success while the index stays empty.
     * The dictionary tab then shows a word with no verses at all, and restarting is the only cure.
     *
     * The same flag makes a *concurrent* second caller return before the first has finished, which
     * is the same symptom from a different direction. Clearing the flag in a `finally`, and having
     * later callers wait rather than return, closes both.
     */
    @Test
    fun `a failed read disables the data for the whole session -- known gap`() {
        val repository = files.repository()
        files.greekFailure = IllegalStateException("disk gone")

        assertFailsWith<IllegalStateException> { runBlocking { repository.ensureGreekLoaded() } }

        // The file is readable again, but the repository will not try.
        files.greekFailure = null
        runBlocking { repository.ensureGreekLoaded() }

        assertEquals(1, files.greekReads, "current behaviour: the retry returns without reading")
        assertTrue(
            repository.getVersesForEntry("G26").isEmpty(),
            "current behaviour: the index stays empty",
        )
    }

    /**
     * The caller must not be handed the index's own list.
     *
     * The index stores `MutableList`s and loading appends to them, so returning one directly gave
     * the caller a live view of a list this class goes on mutating. `DictionaryViewModel` holds the
     * result in `interlinearVerses` and `cardAvailableBooks` iterates it during composition, so a
     * load completing mid-recomposition threw `ConcurrentModificationException` out of the
     * composition and took the Dictionary tab down with it. Seen on CI 2026-08-07.
     *
     * Asserting on identity rather than contents is the point: equal contents held either before or
     * after the fix, and it is the sharing that does the damage.
     */
    @Test
    fun `the verses for an entry are a copy, not the index's own list`() {
        val repository = loaded()

        val first = repository.getVersesForEntry("G26")
        val second = repository.getVersesForEntry("G26")

        assertEquals(first, second, "the same entry must still answer with the same verses")
        assertNotSame(first, second, "callers were handed the index's own mutable list")
    }
}
