package org.churchpresenter.app.churchpresenter.viewmodel

import churchpresenter.composeapp.generated.resources.Res
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The detail pane: every verse where the selected word appears in the original, and the two
 * independent filters over it — the left-pane passage filter, which floats matching verses to the
 * top, and the card filter, which hides everything else.
 *
 * `InterlinearRepository` is stubbed with `mockkConstructor` (the real one reads 4–8 MB of bundled
 * JSON), and the dictionary itself comes from [DictionaryFixture].
 */
class DictionaryViewModelInterlinearTest {

    private val created = mutableListOf<DictionaryViewModel>()

    /** John 3:16, John 3:17, Matthew 5:3, Matthew 22:39 — deliberately not in canonical order. */
    private val johnThreeSixteen = DictionaryFixture.verse(43, 3, 16)
    private val johnThreeSeventeen = DictionaryFixture.verse(43, 3, 17)
    private val matthewFiveThree = DictionaryFixture.verse(40, 5, 3)
    private val matthewTwentyTwo = DictionaryFixture.verse(40, 22, 39)
    private val agapeVerses = listOf(johnThreeSixteen, matthewFiveThree, johnThreeSeventeen, matthewTwentyTwo)

    @BeforeTest
    fun stubData() {
        DictionaryFixture.stubResources()
        mockkConstructor(InterlinearRepository::class)
        coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
        coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
        every { anyConstructed<InterlinearRepository>().getVersesForEntry("G26") } returns agapeVerses
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

    private fun vm(): DictionaryViewModel = DictionaryViewModel().also { created.add(it) }

    /** A view model with the fixture dictionary loaded. */
    private fun loaded(): DictionaryViewModel {
        val d = vm()
        d.load()
        awaitUntil("the dictionary to finish loading") { d.entries.isNotEmpty() && !d.isLoading }
        return d
    }

    /** Selects [number]'s entry and waits for its verses to arrive. */
    private fun DictionaryViewModel.selectAgape() {
        onEntrySelected(DictionaryFixture.agape)
        awaitUntil("the interlinear verses to load") { interlinearVerses.isNotEmpty() && !isInterlinearLoading }
    }

    private fun List<InterlinearVerse>.refs(): List<String> = map { "${it.bookId}:${it.chapter}:${it.verseNumber}" }

    // ── Loading verses for a word ───────────────────────────────────────────────

    @Test
    fun `selecting a word pulls in every verse it appears in`() {
        val d = vm()
        d.selectAgape()
        assertEquals(
            listOf("43:3:16", "40:5:3", "43:3:17", "40:22:39"),
            d.interlinearVerses.refs(),
            "verses arrive in repository order; the view model does not re-sort them"
        )
    }

    @Test
    fun `a word with no occurrences leaves the pane empty rather than loading forever`() {
        val d = vm()
        d.onEntrySelected(DictionaryFixture.charis) // stubbed to no verses
        awaitUntil("the empty load to settle") { !d.isInterlinearLoading }
        assertTrue(d.interlinearVerses.isEmpty())
    }

    @Test
    fun `a Greek word only pulls the Greek side of the interlinear`() {
        val d = vm()
        d.selectAgape()
        coVerify(timeout = 5_000) { anyConstructed<InterlinearRepository>().ensureGreekLoaded() }
        coVerify(exactly = 0) { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() }
    }

    @Test
    fun `a Hebrew word only pulls the Hebrew side of the interlinear`() {
        val d = vm()
        d.onEntrySelected(DictionaryFixture.elohim)
        coVerify(timeout = 5_000) { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() }
        coVerify(exactly = 0) { anyConstructed<InterlinearRepository>().ensureGreekLoaded() }
    }

    @Test
    fun `switching words replaces the verses rather than appending`() {
        val d = vm()
        d.selectAgape()

        d.onEntrySelected(DictionaryFixture.charis)
        awaitUntil("the previous word's verses to clear") { d.interlinearVerses.isEmpty() }
    }

    @Test
    fun `clearing the selection clears the verses`() {
        val d = vm()
        d.selectAgape()
        d.onEntrySelected(null)
        assertTrue(d.interlinearVerses.isEmpty(), "verses from the last word would look like they belong to no word")
    }

    // ── Card filter (detail pane) ───────────────────────────────────────────────

    @Test
    fun `the card book dropdown offers each book once, in canonical order`() {
        val d = vm()
        d.selectAgape()
        assertEquals(listOf(40, 43), d.cardAvailableBooks)
    }

    @Test
    fun `no chapters are offered until a book is picked`() {
        val d = vm()
        d.selectAgape()
        assertTrue(d.cardAvailableChapters.isEmpty())
    }

    @Test
    fun `the card chapter dropdown only offers chapters from the chosen book`() {
        val d = vm()
        d.selectAgape()

        d.filterCardsByBook(40)
        assertEquals(listOf(5, 22), d.cardAvailableChapters)

        d.filterCardsByBook(43)
        assertEquals(listOf(3), d.cardAvailableChapters)
    }

    @Test
    fun `with no card filter every verse is shown`() {
        val d = vm()
        d.selectAgape()
        assertEquals(d.interlinearVerses.refs(), d.filteredSortedInterlinearVerses.refs())
    }

    @Test
    fun `a card book filter hides the other books`() {
        val d = vm()
        d.selectAgape()
        d.filterCardsByBook(43)
        assertEquals(listOf("43:3:16", "43:3:17"), d.filteredSortedInterlinearVerses.refs())
    }

    @Test
    fun `a card chapter filter narrows within the book`() {
        val d = vm()
        d.selectAgape()
        d.filterCardsByBook(40)
        d.filterCardsByChapter(22)
        assertEquals(listOf("40:22:39"), d.filteredSortedInterlinearVerses.refs())
    }

    @Test
    fun `clearing the card book filter brings every verse back`() {
        val d = vm()
        d.selectAgape()
        d.filterCardsByBook(43)
        d.filterCardsByBook(null)
        assertEquals(4, d.filteredSortedInterlinearVerses.size)
    }

    // ── Passage filter floats matching verses to the top ────────────────────────

    @Test
    fun `with no passage filter the verses keep repository order`() {
        val d = vm()
        d.selectAgape()
        assertEquals(d.interlinearVerses.refs(), d.sortedInterlinearVerses.refs())
    }

    @Test
    fun `a book passage filter floats that book's verses to the top`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(40)
        assertEquals(
            listOf("40:5:3", "40:22:39", "43:3:16", "43:3:17"),
            d.sortedInterlinearVerses.refs(),
            "the verses the operator filtered to should be the ones they see first"
        )
    }

    @Test
    fun `a chapter passage filter floats only that chapter`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(40)
        d.filterEntryListByChapter(22)
        assertEquals(listOf("40:22:39", "43:3:16", "40:5:3", "43:3:17"), d.sortedInterlinearVerses.refs())
    }

    @Test
    fun `a verse passage filter floats the single verse`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(43)
        d.filterEntryListByChapter(3)
        d.filterEntryListByVerse(17)
        assertEquals(listOf("43:3:17", "43:3:16", "40:5:3", "40:22:39"), d.sortedInterlinearVerses.refs())
    }

    @Test
    fun `floating never drops a verse`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(40)
        assertEquals(
            d.interlinearVerses.refs().sorted(),
            d.sortedInterlinearVerses.refs().sorted(),
            "the passage filter reorders the pane; hiding is the card filter's job"
        )
    }

    @Test
    fun `a passage filter matching no verse leaves the order alone`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(19) // Psalms — this word appears nowhere in it
        assertEquals(d.interlinearVerses.refs(), d.sortedInterlinearVerses.refs())
    }

    @Test
    fun `the passage and card filters stack`() {
        val d = vm()
        d.selectAgape()
        d.filterEntryListByBook(40) // float Matthew
        d.filterCardsByBook(43)     // but show only John
        assertEquals(listOf("43:3:16", "43:3:17"), d.filteredSortedInterlinearVerses.refs())
    }

    // ── Lookup by number ────────────────────────────────────────────────────────

    @Test
    fun `a number lookup finds the word whatever case it is typed in`() {
        val d = loaded()
        d.selectByNumber("g26")
        assertEquals("G26", d.selectedEntry?.number)
    }

    @Test
    fun `a number lookup does not yank the entry list`() {
        val d = loaded()
        val before = d.scrollRequestToken
        d.selectByNumber("G26")
        assertEquals(before, d.scrollRequestToken, "the list scrolls itself; a cross-reference jump must not fight it")
    }

    @Test
    fun `a number lookup is recorded in the back history`() {
        val d = loaded()
        d.selectByNumber("G26")
        d.selectByNumber("H430")
        assertTrue(d.canGoBack)

        d.goBack()
        assertEquals("G26", d.selectedEntry?.number, "following a cross-reference must be reversible")
    }

    @Test
    fun `a number lookup outside the current passage filter drops the filter`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null) } returns setOf("G5485")
        d.filterEntryListByBook(43)

        d.selectByNumber("G26") // not used in the filtered passage

        assertEquals("G26", d.selectedEntry?.number)
        assertNull(d.entryBookFilter, "leaving the filter on would select a word the list cannot show")
    }

    @Test
    fun `a number lookup inside the current passage filter keeps the filter`() {
        val d = loaded()
        every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(43, null, null) } returns setOf("G26", "G5485")
        d.filterEntryListByBook(43)

        d.selectByNumber("G26")

        assertEquals("G26", d.selectedEntry?.number)
        assertEquals(43, d.entryBookFilter, "the filter still shows the word, so the operator keeps their place")
    }

    @Test
    fun `a lookup before the dictionary is loaded is applied once it arrives`() {
        val d = vm()
        d.selectByNumber("G26") // e.g. a schedule item opened straight into the dictionary tab
        assertNull(d.selectedEntry, "nothing to select yet")

        d.load()
        awaitUntil("the deferred selection to be applied") { d.selectedEntry != null }
        assertEquals("G26", d.selectedEntry?.number)
    }

    @Test
    fun `a deferred lookup does not create a history entry to go back to`() {
        val d = vm()
        d.selectByNumber("G26")
        d.load()
        awaitUntil("the deferred selection to be applied") { d.selectedEntry != null }
        assertTrue(!d.canGoBack, "arriving at the first word is not a navigation step")
    }
}
