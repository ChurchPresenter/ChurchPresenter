package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BibleViewModel]'s reference navigation and search, against a real loaded module.
 *
 * `selectVerseByDetails` is how a schedule click, a Companion API call and an auto-follow
 * detection all reach a verse — it resolves a book by name (or canonical id), loads the chapter,
 * and finds the verse by its printed number. That last step is the fiddly one: verses arrive as
 * `"16. For God so loved…"` strings, so matching has to distinguish verse 3 from verse 13 and 23.
 */
class BibleViewModelNavigationTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-nav-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = richContent())

        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
            ),
        )
        awaitUntil("books to load") { vm.books.value.isNotEmpty() }
        awaitUntil("verse data to load") { vm.isFullyLoaded }
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /**
     * A module with enough verses in one chapter that verse 3 / 13 / 23 all exist — the case a
     * naive `startsWith("3.")` match gets wrong.
     */
    private fun richContent(): String = SpbFixture.buildContent(
        title = "Nav Test Bible",
        books = listOf(
            SpbFixture.Book(1, "Genesis", 1),
            SpbFixture.Book(19, "Psalms", 1),
            SpbFixture.Book(43, "John", 1),
        ),
        verses = buildList {
            add(SpbFixture.Verse(1, 1, 1, "In the beginning God created the heaven and the earth."))
            add(SpbFixture.Verse(19, 23, 1, "The LORD is my shepherd; I shall not want."))
            // John 3, verses 1..25 so 3, 13 and 23 all exist.
            for (v in 1..25) {
                add(SpbFixture.Verse(43, 3, v, "John three verse $v text about light and truth."))
            }
        },
    )

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun awaitToken(before: Int) =
        awaitUntil("the verse selection to settle") { vm.verseSelectionToken.value > before }

    /** The verse number the UI currently has selected, parsed back off the displayed string. */
    private fun selectedVerseNumber(): Int =
        vm.verses.value[vm.selectedVerseIndex.value].substringBefore('.').trim().toInt()

    // ── selectVerseByDetails ────────────────────────────────────────────────────

    @Test
    fun `navigating by book name loads the chapter and lands on the verse`() {
        val token = vm.verseSelectionToken.value
        assertTrue(vm.selectVerseByDetails("John", 3, 16))
        awaitToken(token)

        assertEquals(2, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `the book name match is case-insensitive`() {
        val token = vm.verseSelectionToken.value
        assertTrue(vm.selectVerseByDetails("jOhN", 3, 1))
        awaitToken(token)
        assertEquals(2, vm.selectedBookIndex.value)
    }

    @Test
    fun `an unknown book name reports failure immediately`() {
        assertFalse(vm.selectVerseByDetails("Habakkuk", 3, 2), "a book not in this module cannot be shown")
    }

    @Test
    fun `verse matching distinguishes 3 from 13 and 23`() {
        // The reason the implementation matches on "N. " with a trailing space.
        for (target in listOf(3, 13, 23)) {
            val token = vm.verseSelectionToken.value
            assertTrue(vm.selectVerseByDetails("John", 3, target))
            awaitToken(token)
            assertEquals(target, selectedVerseNumber(), "asked for verse $target")
        }
    }

    @Test
    fun `a verse that does not exist falls back to the first verse of the chapter`() {
        val token = vm.verseSelectionToken.value
        assertTrue(vm.selectVerseByDetails("John", 3, 999))
        awaitToken(token)
        assertEquals(1, selectedVerseNumber(), "better the top of the chapter than nothing")
    }

    @Test
    fun `navigating by canonical book id wins over the name`() {
        // Instance Link and the Companion API send the canonical id; the name may be in another
        // language entirely.
        val token = vm.verseSelectionToken.value
        assertTrue(vm.selectVerseByDetails(bookName = "not-a-book", chapter = 23, verseNumber = 1, bookId = 19))
        awaitToken(token)
        assertEquals(1, vm.selectedBookIndex.value, "book id 19 is Psalms, at display index 1")
    }

    @Test
    fun `navigating clears a stale multi-selection from another chapter`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 1)
        awaitToken(token)
        vm.selectVerse(0)
        vm.shiftClickVerse(4)
        assertTrue(vm.multiVerseEnabled.value)

        val token2 = vm.verseSelectionToken.value
        vm.selectVerseByDetails("Genesis", 1, 1)
        awaitToken(token2)
        assertTrue(vm.selectedVerseIndices.isEmpty(), "indices from another chapter would highlight wrong verses")
        assertFalse(vm.multiVerseEnabled.value)
    }

    @Test
    fun `a verse range restores the multi-selection`() {
        val token = vm.verseSelectionToken.value
        assertTrue(vm.selectVerseByDetails("John", 3, 16, verseRange = "16-18"))
        awaitToken(token)

        assertTrue(vm.multiVerseEnabled.value)
        val numbers = vm.selectedVerseIndices.sorted().map { vm.verses.value[it].substringBefore('.').trim().toInt() }
        assertEquals(listOf(16, 17, 18), numbers)
    }

    @Test
    fun `a comma-separated range restores exactly those verses`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 16, verseRange = "16,18,20")
        awaitToken(token)

        val numbers = vm.selectedVerseIndices.sorted().map { vm.verses.value[it].substringBefore('.').trim().toInt() }
        assertEquals(listOf(16, 18, 20), numbers)
    }

    @Test
    fun `a single-verse range does not turn on multi-select`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 16, verseRange = "16")
        awaitToken(token)
        assertFalse(vm.multiVerseEnabled.value, "one verse is not a range")
    }

    @Test
    fun `a go-live source fires the deferred token only after the verse is resolved`() {
        // A caller going live synchronously would race the load and read verse 1; the token is
        // the signal that the right verse is actually selected.
        val liveToken = vm.autoFollowLiveToken.value
        vm.selectVerseByDetails("John", 3, 16, goLiveSource = "schedule")
        awaitUntil("the go-live token") { vm.autoFollowLiveToken.value > liveToken }

        assertEquals(16, selectedVerseNumber(), "the verse must be right by the time go-live fires")
        assertEquals("schedule", vm.autoFollowLiveSource.value)
        assertEquals(null, vm.autoFollowLiveMatchType.value, "a schedule click is not a detection")
    }

    @Test
    fun `no go-live token fires when no source is given`() {
        val liveToken = vm.autoFollowLiveToken.value
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 16)
        awaitToken(token)
        assertEquals(liveToken, vm.autoFollowLiveToken.value, "browsing must not put anything on screen")
    }

    // ── selectVerseByBookId ─────────────────────────────────────────────────────

    @Test
    fun `navigating by book id alone lands on the verse`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByBookId(bookId = 43, chapter = 3, verseNumber = 16)
        awaitToken(token)

        assertEquals(2, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `an unknown book id is ignored`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 5)
        awaitToken(token)

        vm.selectVerseByBookId(bookId = 66, chapter = 1, verseNumber = 1) // not in this module
        assertEquals(2, vm.selectedBookIndex.value, "the current passage must not move")
    }

    @Test
    fun `navigating by book id leaves search mode`() {
        vm.updateSearchQuery("shepherd")
        vm.performSearch()
        awaitUntil("search results") { vm.isSearchMode.value }

        vm.selectVerseByBookId(bookId = 43, chapter = 3, verseNumber = 1)
        assertFalse(vm.isSearchMode.value, "picking a passage should return to browsing")
        assertTrue(vm.searchResults.value.isEmpty())
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    @Test
    fun `a text search finds matching verses across the whole bible`() {
        vm.updateSearchQuery("shepherd")
        vm.performSearch()
        awaitUntil("search results") { vm.isSearchMode.value }

        assertTrue(vm.searchResults.value.isNotEmpty())
        assertTrue(vm.isSearchMode.value)
    }

    @Test
    fun `a one-character query is ignored`() {
        // Otherwise a single letter returns most of the Bible.
        vm.updateSearchQuery("a")
        vm.performSearch()
        assertFalse(vm.isSearchMode.value)
        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun `clearing the query leaves search mode`() {
        vm.updateSearchQuery("shepherd")
        vm.performSearch()
        awaitUntil("search results") { vm.isSearchMode.value }

        vm.updateSearchQuery("")
        vm.performSearch()
        assertFalse(vm.isSearchMode.value)
    }

    @Test
    fun `search can be scoped to the current book`() {
        // Scope 1 = current book. "shepherd" only appears in Psalms.
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 1)
        awaitToken(token)

        vm.updateSelectedScopeIndex(1)
        vm.updateSearchQuery("shepherd")
        vm.performSearch()
        awaitUntil("scoped search") { vm.isSearchMode.value }

        assertTrue(vm.searchResults.value.isEmpty(), "the term is not in John, the current book")
    }

    @Test
    fun `search mode cycles through its three settings`() {
        assertEquals(BibleSearchMode.AUTO, vm.searchMode.value)
        vm.cycleSearchMode()
        assertEquals(BibleSearchMode.REFERENCE, vm.searchMode.value)
        vm.cycleSearchMode()
        assertEquals(BibleSearchMode.TEXT, vm.searchMode.value)
        vm.cycleSearchMode()
        assertEquals(BibleSearchMode.AUTO, vm.searchMode.value, "and back round")
    }

    // ── Picker filtering ────────────────────────────────────────────────────────

    @Test
    fun `an empty book filter returns every book`() {
        assertEquals(vm.books.value, vm.getFilteredBooks())
    }

    @Test
    fun `books filter on a substring of the name`() {
        vm.updateBookSearchQuery("psal")
        assertEquals(listOf("Psalms"), vm.getFilteredBooks())
    }

    @Test
    fun `a book filter matching nothing returns nothing`() {
        vm.updateBookSearchQuery("zzzz")
        assertTrue(vm.getFilteredBooks().isEmpty())
    }

    @Test
    fun `chapters and verses filter on their displayed text`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 1)
        awaitToken(token)

        // The picker lists 1..highest-chapter-present, so a module holding only John 3 still
        // offers chapters 1 and 2 (both empty). Harmless on a complete translation, where the
        // chapters are contiguous from 1 — see BibleTest for the same derivation.
        assertEquals(listOf("1", "2", "3"), vm.getFilteredChapters())

        vm.updateChapterSearchQuery("3")
        assertEquals(listOf("3"), vm.getFilteredChapters(), "the chapter box filters the list")
        vm.updateChapterSearchQuery("")

        vm.updateVerseSearchQuery("verse 7")
        val filtered = vm.getFilteredVerses()
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.contains("verse 7", ignoreCase = true) })
    }

    @Test
    fun `an empty verse filter returns the whole chapter`() {
        val token = vm.verseSelectionToken.value
        vm.selectVerseByDetails("John", 3, 1)
        awaitToken(token)
        vm.updateVerseSearchQuery("")
        assertEquals(vm.verses.value, vm.getFilteredVerses())
    }
}
