package org.churchpresenter.app.churchpresenter.viewmodel

import churchpresenter.composeapp.generated.resources.Res
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Looking a word up. Everything the left-hand entry list shows is derived by
 * [DictionaryViewModel.searchResults] from three inputs the operator sets independently — the query
 * box, the Hebrew/Greek toggle and the book/chapter/verse passage filter — so the interesting cases
 * are the combinations.
 *
 * The bundled dictionary JSON is replaced by [DictionaryFixture]'s four-entry corpus, and
 * `InterlinearRepository` is stubbed with `mockkConstructor`, so no data file is read and no
 * background work escapes the test.
 */
class DictionaryViewModelSearchTest {

    private val created = mutableListOf<DictionaryViewModel>()

    @BeforeTest
    fun stubData() {
        DictionaryFixture.stubResources()
        mockkConstructor(InterlinearRepository::class)
        coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
        coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getBooksWithGreekData() } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getBooksWithHebrewData() } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getChaptersForBook(any()) } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getVersesInChapter(any(), any()) } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } returns emptySet()
    }

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(InterlinearRepository::class)
        unmockkObject(Res)
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** A view model with the fixture dictionary already loaded. */
    private fun loaded(): DictionaryViewModel {
        val d = DictionaryViewModel().also { created.add(it) }
        d.load()
        awaitUntil("the dictionary to finish loading") { d.entries.isNotEmpty() && !d.isLoading }
        return d
    }

    private fun DictionaryViewModel.search(query: String): List<String> {
        searchQuery = query
        return searchResults.map { it.number }
    }

    // ── Loading ─────────────────────────────────────────────────────────────────

    @Test
    fun `loading brings in both alphabets, Hebrew first and each in number order`() {
        val d = loaded()
        assertEquals(
            listOf("H430", "H7225", "G26", "G5485"),
            d.entries.map { it.number },
            "the list must read H1…H8674 then G1…G5624, not file order"
        )
    }

    @Test
    fun `loading twice does not duplicate the dictionary`() {
        val d = loaded()
        d.load()
        awaitUntil("the second load to settle") { !d.isLoading }
        assertEquals(4, d.entries.size, "re-entering the tab must not stack a second copy of every word")
    }

    @Test
    fun `an empty query lists the whole dictionary`() {
        val d = loaded()
        assertEquals(4, d.search("").size)
    }

    @Test
    fun `a whitespace-only query is treated as no query at all`() {
        val d = loaded()
        assertEquals(4, d.search("   ").size)
    }

    // ── Which field matched ─────────────────────────────────────────────────────

    @Test
    fun `a query matches the Strong's number`() {
        val d = loaded()
        assertEquals(listOf("G5485"), d.search("G5485"))
    }

    @Test
    fun `a partial number matches as a prefix of the list`() {
        val d = loaded()
        assertEquals(listOf("H7225"), d.search("722"))
    }

    @Test
    fun `number lookup ignores case`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("g26"), "nobody types the G in caps")
    }

    @Test
    fun `a query matches the transliteration`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("agape"))
    }

    @Test
    fun `a query matches the pronunciation`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("ag-ah'-pay"))
    }

    @Test
    fun `a query matches inside the definition`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("benevolence"))
    }

    @Test
    fun `matching is case-insensitive across every latin field`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("AGAPE"))
        assertEquals(listOf("G26"), d.search("BeNeVoLeNcE"))
        assertEquals(listOf("H430"), d.search("EL-O-HEEM'"))
    }

    @Test
    fun `surrounding whitespace is trimmed off the query`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("  agape  "))
    }

    @Test
    fun `a query matches the original-language word itself`() {
        val d = loaded()
        assertEquals(listOf("G26"), d.search("ἀγάπη"))
        assertEquals(listOf("H430"), d.search("אֱלֹהִים"))
    }

    @Test
    fun `the original-language word is matched exactly, not case-folded`() {
        // Every other field is lowercased on both sides; the Greek/Hebrew word is compared as typed.
        // Uppercased Greek therefore finds nothing — which is correct for a script whose lexicon
        // form is always lowercase, but means the query has to be pasted rather than retyped.
        val d = loaded()
        assertTrue(d.search("ἈΓΆΠΗ").isEmpty())
    }

    @Test
    fun `the kjv usage column is not searched`() {
        // "charity" appears only in G26's kjvUsage. Documents the current field list; if usage is
        // ever added to the search, this expectation flips to listOf("G26").
        val d = loaded()
        assertTrue(d.search("charity").isEmpty())
    }

    @Test
    fun `a query that matches nothing returns an empty list rather than everything`() {
        val d = loaded()
        assertTrue(d.search("qqqq").isEmpty())
    }

    @Test
    fun `one query can match several words`() {
        // "j" hits H430's "judges" and G5485's "joy" — different fields, different alphabets.
        val d = loaded()
        assertEquals(listOf("H430", "G5485"), d.search("j"), "results keep dictionary order, not match order")
    }

    // ── Language filter ─────────────────────────────────────────────────────────

    @Test
    fun `the Hebrew filter hides the Greek half of the dictionary`() {
        val d = loaded()
        d.setLanguageFilter(DictionaryLanguageFilter.HEBREW)
        assertEquals(listOf("H430", "H7225"), d.searchResults.map { it.number })
    }

    @Test
    fun `the Greek filter hides the Hebrew half`() {
        val d = loaded()
        d.setLanguageFilter(DictionaryLanguageFilter.GREEK)
        assertEquals(listOf("G26", "G5485"), d.searchResults.map { it.number })
    }

    @Test
    fun `switching back to all restores the full dictionary`() {
        val d = loaded()
        d.setLanguageFilter(DictionaryLanguageFilter.GREEK)
        d.setLanguageFilter(DictionaryLanguageFilter.ALL)
        assertEquals(4, d.searchResults.size)
    }

    @Test
    fun `the language filter and the query narrow together`() {
        val d = loaded()
        d.searchQuery = "g" // matches H430 (definition "gods") and G5485 (number)
        d.setLanguageFilter(DictionaryLanguageFilter.HEBREW)
        assertEquals(listOf("H430"), d.searchResults.map { it.number })
    }

    @Test
    fun `the query survives a language filter change`() {
        val d = loaded()
        d.searchQuery = "agape"
        d.setLanguageFilter(DictionaryLanguageFilter.GREEK)
        assertEquals("agape", d.searchQuery, "retyping the query after every toggle would be tedious")
        assertEquals(listOf("G26"), d.searchResults.map { it.number })
    }

    // ── Passage filter ──────────────────────────────────────────────────────────

    @Test
    fun `a passage filter narrows the list to the words used in that passage`() {
        val d = loaded()
        every {
            anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null)
        } returns setOf("G26", "H430")

        d.filterEntryListByBook(43)

        assertEquals(listOf("H430", "G26"), d.searchResults.map { it.number })
    }

    @Test
    fun `the passage filter tightens as chapter and verse are picked`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null) } returns setOf("G26", "G5485")
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, 3, null) } returns setOf("G26")
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, 3, 16) } returns setOf("G26")

        d.filterEntryListByBook(43)
        assertEquals(listOf("G26", "G5485"), d.searchResults.map { it.number })

        d.filterEntryListByChapter(3)
        assertEquals(listOf("G26"), d.searchResults.map { it.number })

        d.filterEntryListByVerse(16)
        assertEquals(listOf("G26"), d.searchResults.map { it.number })
    }

    @Test
    fun `a passage with no dictionary words shows an empty list, not the whole dictionary`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } returns emptySet()
        d.filterEntryListByBook(43)
        assertTrue(d.searchResults.isEmpty())
    }

    @Test
    fun `clearing the passage filter brings the whole dictionary back`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } returns setOf("G26")
        d.filterEntryListByBook(43)
        assertEquals(1, d.searchResults.size)

        d.filterEntryListByBook(null)
        assertEquals(4, d.searchResults.size)
    }

    @Test
    fun `the passage filter, language filter and query all apply together`() {
        val d = loaded()
        every {
            anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null)
        } returns setOf("G26", "G5485", "H430")

        d.filterEntryListByBook(43)
        d.setLanguageFilter(DictionaryLanguageFilter.GREEK) // NB: this clears the passage filter
        assertNull(d.entryBookFilter)

        d.filterEntryListByBook(43)
        d.searchQuery = "grace"
        assertEquals(listOf("G5485"), d.searchResults.map { it.number })
    }

    // ── Selection follows the filters ───────────────────────────────────────────

    @Test
    fun `filtering to a passage that still contains the current word keeps it selected`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null) } returns setOf("G26", "G5485")
        d.onEntrySelected(DictionaryFixture.agape)

        d.filterEntryListByBook(43)

        assertEquals("G26", d.selectedEntry?.number, "the word being read must not jump out from under the reader")
    }

    @Test
    fun `filtering to a passage without the current word jumps to the first visible one`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null) } returns setOf("G5485")
        d.onEntrySelected(DictionaryFixture.agape)

        d.filterEntryListByBook(43)

        assertEquals("G5485", d.selectedEntry?.number, "an empty detail pane next to a full list reads as broken")
    }

    @Test
    fun `filtering to an empty passage leaves the current word up`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } returns emptySet()
        d.onEntrySelected(DictionaryFixture.agape)

        d.filterEntryListByBook(43)

        assertEquals("G26", d.selectedEntry?.number, "with nothing to fall back to, blanking the pane helps nobody")
    }

    // ── Passage-filter dropdowns ────────────────────────────────────────────────

    @Test
    fun `no books are offered until the interlinear data is in`() {
        val d = DictionaryViewModel().also { created.add(it) }
        assertTrue(
            d.entryAvailableBooks.isEmpty(),
            "a book list built before the data lands would be empty and look like there is nothing to filter by"
        )
    }

    @Test
    fun `the book list follows the language filter`() {
        every { anyConstructed<InterlinearRepository>().getBooksWithHebrewData() } returns listOf(1, 19)
        every { anyConstructed<InterlinearRepository>().getBooksWithGreekData() } returns listOf(40, 43)
        val d = loaded()
        awaitUntil("the interlinear preload to finish") { d.isInterlinearDataLoaded }

        assertEquals(listOf(1, 19, 40, 43), d.entryAvailableBooks, "all: both testaments, in canonical order")

        d.setLanguageFilter(DictionaryLanguageFilter.HEBREW)
        assertEquals(listOf(1, 19), d.entryAvailableBooks)

        d.setLanguageFilter(DictionaryLanguageFilter.GREEK)
        assertEquals(listOf(40, 43), d.entryAvailableBooks)
    }

    @Test
    fun `chapters and verses are only offered once the level above is picked`() {
        every { anyConstructed<InterlinearRepository>().getChaptersForBook(43) } returns listOf(1, 2, 3)
        every { anyConstructed<InterlinearRepository>().getVersesInChapter(43, 3) } returns listOf(16, 17)
        val d = loaded()

        assertTrue(d.entryAvailableChapters.isEmpty(), "no book picked yet")
        assertTrue(d.entryAvailableVerses.isEmpty())

        d.filterEntryListByBook(43)
        assertEquals(listOf(1, 2, 3), d.entryAvailableChapters)
        assertTrue(d.entryAvailableVerses.isEmpty(), "no chapter picked yet")

        d.filterEntryListByChapter(3)
        assertEquals(listOf(16, 17), d.entryAvailableVerses)
    }

    // ── Dictionary language ─────────────────────────────────────────────────────

    @Test
    fun `the dictionary language toggles between english and russian`() {
        val d = loaded()
        assertEquals("en", d.dictLanguage)

        d.toggleDictLanguage()
        assertEquals("ru", d.dictLanguage)

        d.toggleDictLanguage()
        assertEquals("en", d.dictLanguage)
    }

    @Test
    fun `toggling the language reloads the definitions in that language`() {
        val d = loaded()
        assertEquals("brotherly love, affection, benevolence", d.entries.single { it.number == "G26" }.definition)

        d.toggleDictLanguage()
        awaitUntil("the russian dictionary to load") {
            d.entries.isNotEmpty() && d.entries.single { it.number == "G26" }.definition == "любовь"
        }
        assertEquals(4, d.entries.size, "a language swap replaces the dictionary rather than appending to it")
    }

    @Test
    fun `the word being read stays on screen across a language swap`() {
        val d = loaded()
        d.onEntrySelected(DictionaryFixture.agape)

        d.toggleDictLanguage()

        awaitUntil("the selection to be re-resolved in russian") { d.selectedEntry?.definition == "любовь" }
        assertEquals("G26", d.selectedEntry?.number, "the reader was mid-word; only the language should change")
    }

    @Test
    fun `a reload with nothing selected leaves nothing selected`() {
        val d = loaded()
        d.reload()
        awaitUntil("the reload to finish") { d.entries.isNotEmpty() }
        assertNull(d.selectedEntry)
    }
}
