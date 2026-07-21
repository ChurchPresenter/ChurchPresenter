package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Strong's dictionary's browse state: back/forward history through looked-up words, and the
 * cascading passage filters.
 *
 * Selecting an entry kicks off an interlinear load from `InterlinearRepository`, which reads
 * bundled data files. `mockkConstructor` neutralises that so these tests exercise only the
 * synchronous navigation state, with no data files and no background work.
 */
class DictionaryViewModelTest {

    private val created = mutableListOf<DictionaryViewModel>()

    @BeforeTest
    fun stubRepository() {
        mockkConstructor(InterlinearRepository::class)
        coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
        coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getBooksWithGreekData() } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getBooksWithHebrewData() } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } returns emptySet()
    }

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(InterlinearRepository::class)
    }

    private fun vm(): DictionaryViewModel = DictionaryViewModel().also { created.add(it) }

    private fun entry(number: String) = StrongsEntry(
        number = number,
        word = "word-$number",
        transliteration = "translit",
        pronunciation = "pron",
        definition = "definition of $number",
    )

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `nothing is selected initially and there is no history`() {
        val d = vm()
        assertNull(d.selectedEntry)
        assertFalse(d.canGoBack)
        assertFalse(d.canGoForward)
    }

    @Test
    fun `selecting an entry makes it current`() {
        val d = vm()
        val g26 = entry("G26")
        d.onEntrySelected(g26)
        assertEquals(g26, d.selectedEntry)
    }

    @Test
    fun `clearing the selection does not create a history entry`() {
        val d = vm()
        d.onEntrySelected(null)
        assertNull(d.selectedEntry)
        assertFalse(d.canGoBack)
    }

    @Test
    fun `selecting requests a scroll only when asked`() {
        val d = vm()
        val before = d.scrollRequestToken

        d.onEntrySelected(entry("G26"))
        assertEquals(before + 1, d.scrollRequestToken, "the list should scroll to the picked word")

        d.onEntrySelected(entry("G27"), scrollToEntry = false)
        assertEquals(before + 1, d.scrollRequestToken, "a programmatic select must not yank the list")
    }

    @Test
    fun `clearing the selection never requests a scroll`() {
        val d = vm()
        val before = d.scrollRequestToken
        d.onEntrySelected(null)
        assertEquals(before, d.scrollRequestToken)
    }

    // ── History ─────────────────────────────────────────────────────────────────

    @Test
    fun `a single entry leaves nothing to go back to`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        assertFalse(d.canGoBack, "the first word is the start of history")
        assertFalse(d.canGoForward)
    }

    @Test
    fun `back and forward walk the lookup history`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.onEntrySelected(entry("G27"))
        assertTrue(d.canGoBack)
        assertFalse(d.canGoForward)

        d.goBack()
        assertFalse(d.canGoBack, "back at the first entry")
        assertTrue(d.canGoForward)

        d.goForward()
        assertTrue(d.canGoBack)
        assertFalse(d.canGoForward)
    }

    @Test
    fun `history goes as deep as the words looked up`() {
        val d = vm()
        listOf("G1", "G2", "G3", "G4").forEach { d.onEntrySelected(entry(it)) }

        var steps = 0
        while (d.canGoBack) { d.goBack(); steps++ }
        assertEquals(3, steps, "4 entries means 3 steps back")
        assertTrue(d.canGoForward)
    }

    @Test
    fun `going back past the start is a harmless no-op`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.goBack()
        d.goBack()
        assertFalse(d.canGoBack)
    }

    @Test
    fun `going forward past the end is a harmless no-op`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.goForward()
        assertFalse(d.canGoForward)
    }

    @Test
    fun `selecting a new word after going back discards the forward branch`() {
        val d = vm()
        d.onEntrySelected(entry("G1"))
        d.onEntrySelected(entry("G2"))
        d.onEntrySelected(entry("G3"))
        d.goBack()
        d.goBack() // at G1, with G2/G3 ahead
        assertTrue(d.canGoForward)

        d.onEntrySelected(entry("G99")) // diverge
        assertFalse(d.canGoForward, "the abandoned branch must not be reachable")
        assertTrue(d.canGoBack)
    }

    @Test
    fun `navigating history does not append to it`() {
        val d = vm()
        d.onEntrySelected(entry("G1"))
        d.onEntrySelected(entry("G2"))
        d.goBack()
        d.goForward()
        d.goBack()
        // Still exactly two entries: one step back available, one forward.
        assertFalse(d.canGoBack)
        assertTrue(d.canGoForward)
    }

    // ── Card filters (detail pane) ──────────────────────────────────────────────

    @Test
    fun `choosing a card book clears any chapter already chosen`() {
        val d = vm()
        d.filterCardsByBook(40)
        d.filterCardsByChapter(5)
        assertEquals(40, d.cardBookFilter)
        assertEquals(5, d.cardChapterFilter)

        d.filterCardsByBook(41)
        assertEquals(41, d.cardBookFilter)
        assertNull(d.cardChapterFilter, "a chapter from the previous book would filter to nothing")
    }

    @Test
    fun `card filters reset when a different word is selected`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.filterCardsByBook(40)
        d.filterCardsByChapter(5)

        d.onEntrySelected(entry("G27"))
        assertNull(d.cardBookFilter, "filters belong to the word being viewed")
        assertNull(d.cardChapterFilter)
    }

    // ── Entry-list passage filter (left pane) ───────────────────────────────────

    @Test
    fun `choosing a book clears the chapter and verse below it`() {
        val d = vm()
        d.filterEntryListByBook(40)
        d.filterEntryListByChapter(5)
        d.filterEntryListByVerse(3)
        assertEquals(40, d.entryBookFilter)
        assertEquals(5, d.entryChapterFilter)
        assertEquals(3, d.entryVerseFilter)

        d.filterEntryListByBook(41)
        assertEquals(41, d.entryBookFilter)
        assertNull(d.entryChapterFilter, "a stale chapter would not exist in the new book")
        assertNull(d.entryVerseFilter)
    }

    @Test
    fun `clearing the book filter clears the whole cascade`() {
        val d = vm()
        d.filterEntryListByBook(40)
        d.filterEntryListByChapter(5)
        d.filterEntryListByVerse(3)

        d.filterEntryListByBook(null)
        assertNull(d.entryBookFilter)
        assertNull(d.entryChapterFilter)
        assertNull(d.entryVerseFilter)
    }

    @Test
    fun `changing the language filter clears the passage filter and the selection`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.filterEntryListByBook(40)

        d.setLanguageFilter(DictionaryLanguageFilter.HEBREW)

        assertEquals(DictionaryLanguageFilter.HEBREW, d.filterLanguage)
        assertNull(d.entryBookFilter, "a Greek passage filter is meaningless under a Hebrew filter")
        assertNull(d.selectedEntry, "the selected Greek word is no longer in the list")
    }

    // ── Interlinear paging ──────────────────────────────────────────────────────

    @Test
    fun `showing more raises the display limit`() {
        val d = vm()
        val first = d.interlinearDisplayLimit
        d.showMoreInterlinear()
        assertTrue(d.interlinearDisplayLimit > first)
        val second = d.interlinearDisplayLimit
        d.showMoreInterlinear()
        assertEquals(second + (second - first), d.interlinearDisplayLimit, "each click adds one page")
    }

    @Test
    fun `the display limit resets when a different word is selected`() {
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.showMoreInterlinear()
        d.showMoreInterlinear()
        val expanded = d.interlinearDisplayLimit

        d.onEntrySelected(entry("G27"))
        assertTrue(d.interlinearDisplayLimit < expanded, "a new word starts at the first page again")
    }

    // ── Lookup by number ────────────────────────────────────────────────────────

    @Test
    fun `selecting an unknown number leaves the selection alone`() {
        // No entries are loaded here, so every lookup misses; it must not clear or crash.
        val d = vm()
        d.onEntrySelected(entry("G26"))
        d.selectByNumber("G9999")
        assertEquals("G26", d.selectedEntry?.number)
    }

    @Test
    fun `an empty lookup is ignored`() {
        val d = vm()
        d.selectByNumber("")
        assertNull(d.selectedEntry)
    }
}
