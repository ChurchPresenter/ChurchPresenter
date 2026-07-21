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
 * Sequential browsing and the unified smart search box.
 *
 * Verse/chapter stepping is what an operator uses live — arrow keys and a clicker — so rolling
 * off the end of a chapter or a book has to keep working rather than silently stopping. The
 * look-ahead (`getNextVerses`) feeds the stage monitor's "next verse" panel and rolls over the
 * same way.
 */
class BibleViewModelBrowsingTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-browse-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = content())
        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
            ),
        )
        awaitUntil("books") { vm.books.value.isNotEmpty() }
        awaitUntil("verses") { vm.isFullyLoaded }
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Genesis 1-2 and John 3, with contiguous chapters so rollover is exercisable. */
    private fun content(): String = SpbFixture.buildContent(
        title = "Browse Bible",
        books = listOf(
            SpbFixture.Book(1, "Genesis", 2),
            SpbFixture.Book(43, "John", 3),
        ),
        verses = buildList {
            for (v in 1..3) add(SpbFixture.Verse(1, 1, v, "Genesis one verse $v"))
            for (v in 1..2) add(SpbFixture.Verse(1, 2, v, "Genesis two verse $v"))
            for (v in 1..3) add(SpbFixture.Verse(43, 3, v, "John three verse $v"))
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

    private fun openChapter(bookIndex: Int, chapter: Int) {
        val token = vm.verseSelectionToken.value
        vm.loadChapter(bookIndex, chapter)
        awaitUntil("book $bookIndex chapter $chapter") { vm.verseSelectionToken.value > token }
    }

    private fun selectedVerseNumber(): Int =
        vm.verses.value[vm.selectedVerseIndex.value].substringBefore('.').trim().toInt()

    // ── Verse stepping ──────────────────────────────────────────────────────────

    @Test
    fun `next and previous step through the chapter`() {
        openChapter(0, 1)
        assertTrue(vm.navigateNextVerse())
        assertEquals(2, selectedVerseNumber())
        assertTrue(vm.navigateNextVerse())
        assertEquals(3, selectedVerseNumber())
        assertTrue(vm.navigatePreviousVerse())
        assertEquals(2, selectedVerseNumber())
    }

    @Test
    fun `previous stops at the first verse of the chapter`() {
        openChapter(0, 1)
        assertFalse(vm.navigatePreviousVerse(), "nothing before verse 1")
        assertEquals(1, selectedVerseNumber())
    }

    @Test
    fun `stepping past the last verse rolls into the next chapter`() {
        openChapter(0, 1) // Genesis 1 has 3 verses
        repeat(2) { vm.navigateNextVerse() }
        assertEquals(3, selectedVerseNumber())

        val token = vm.verseSelectionToken.value
        assertTrue(vm.navigateNextVerse(), "the end of a chapter is not the end of the reading")
        awaitUntil("chapter rollover") { vm.verseSelectionToken.value > token }
        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `stepping past the last chapter rolls into the next book`() {
        openChapter(0, 2) // Genesis 2, the book's last chapter
        vm.navigateNextVerse() // to verse 2, the last

        val token = vm.verseSelectionToken.value
        assertTrue(vm.navigateNextVerse())
        awaitUntil("book rollover") { vm.verseSelectionToken.value > token }
        assertEquals(1, vm.selectedBookIndex.value, "Genesis rolls into John")
    }

    @Test
    fun `stepping clears any multi-selection`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.shiftClickVerse(2)
        assertTrue(vm.multiVerseEnabled.value)

        vm.navigateNextVerse()
        assertFalse(vm.multiVerseEnabled.value, "stepping is a single-verse action")
        assertTrue(vm.selectedVerseIndices.isEmpty())
    }

    @Test
    fun `stepping with nothing loaded is a no-op`() {
        val empty = BibleViewModel(AppSettings())
        assertFalse(empty.navigateNextVerse())
        assertFalse(empty.navigatePreviousVerse())
    }

    // ── Chapter stepping ────────────────────────────────────────────────────────

    @Test
    fun `next chapter advances within the book`() {
        openChapter(0, 1)
        assertTrue(vm.navigateNextChapter())
        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `next chapter stops at the end of the book`() {
        openChapter(0, 2) // Genesis' last chapter
        assertFalse(vm.navigateNextChapter(), "chapter stepping does not cross books")
    }

    @Test
    fun `a sequential chapter advance is flagged once and then consumed`() {
        // The stage monitor uses this to tell an automatic advance from a manual jump.
        openChapter(0, 1)
        vm.navigateNextChapter()
        assertTrue(vm.consumeSequentialChapterAdvance())
        assertFalse(vm.consumeSequentialChapterAdvance(), "the flag is one-shot")
    }

    // ── Look-ahead ──────────────────────────────────────────────────────────────

    @Test
    fun `the look-ahead shows the following verse`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        val next = vm.getNextVerses()
        assertTrue(next.isNotEmpty())
        assertEquals(2, next.first().verseNumber)
        assertTrue(next.first().verseText.contains("verse 2"))
    }

    @Test
    fun `the look-ahead rolls into the next chapter at the end of one`() {
        openChapter(0, 1)
        vm.selectVerse(2) // last verse of Genesis 1
        val next = vm.getNextVerses()
        assertTrue(next.isNotEmpty(), "the platform should see what is coming, even across a chapter break")
        assertEquals(1, next.first().verseNumber)
        assertEquals(2, next.first().chapter)
    }

    @Test
    fun `the look-ahead follows the last verse of a multi-selection`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.shiftClickVerse(1) // showing verses 1-2
        val next = vm.getNextVerses()
        assertEquals(3, next.first().verseNumber, "what comes after the whole block, not after its start")
    }

    @Test
    fun `the look-ahead is empty with nothing loaded`() {
        assertTrue(BibleViewModel(AppSettings()).getNextVerses().isEmpty())
    }

    // ── Smart query box ─────────────────────────────────────────────────────────

    @Test
    fun `typing a reference navigates instead of searching`() {
        val token = vm.verseSelectionToken.value
        vm.onSmartQueryChanged("John 3:2")
        awaitUntil("navigation") { vm.verseSelectionToken.value > token }

        assertEquals(1, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(2, selectedVerseNumber())
        assertFalse(vm.isSearchMode.value, "a reference is a jump, not a search")
    }

    @Test
    fun `a chapter-only reference opens that chapter`() {
        val token = vm.verseSelectionToken.value
        vm.onSmartQueryChanged("John 3")
        awaitUntil("navigation") { vm.verseSelectionToken.value > token }
        assertEquals(3, vm.selectedChapter.value)
    }

    @Test
    fun `free text falls through to a search`() {
        vm.onSmartQueryChanged("verse")
        vm.submitSmartQuery()
        awaitUntil("search results") { vm.isSearchMode.value }
        assertTrue(vm.searchResults.value.isNotEmpty())
    }

    @Test
    fun `clearing the box leaves search mode`() {
        vm.onSmartQueryChanged("verse")
        vm.submitSmartQuery()
        awaitUntil("search results") { vm.isSearchMode.value }

        vm.onSmartQueryChanged("")
        assertFalse(vm.isSearchMode.value)
        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun `reference mode never runs a text search`() {
        vm.cycleSearchMode() // AUTO -> REFERENCE
        assertEquals(BibleSearchMode.REFERENCE, vm.searchMode.value)

        vm.onSmartQueryChanged("beginning") // not a reference
        vm.submitSmartQuery()
        assertFalse(vm.isSearchMode.value, "the operator asked for references only")
    }

    @Test
    fun `text mode searches even for something shaped like a reference`() {
        vm.cycleSearchMode()
        vm.cycleSearchMode() // AUTO -> REFERENCE -> TEXT
        assertEquals(BibleSearchMode.TEXT, vm.searchMode.value)

        val bookIndex = vm.selectedBookIndex.value
        vm.onSmartQueryChanged("John 3")
        vm.submitSmartQuery()
        assertEquals(bookIndex, vm.selectedBookIndex.value, "text mode must not navigate")
    }

    // ── Search result selection ─────────────────────────────────────────────────

    @Test
    fun `picking a search result navigates to that verse`() {
        vm.updateSearchQuery("Genesis one")
        vm.performSearch()
        awaitUntil("search results") { vm.isSearchMode.value && vm.searchResults.value.isNotEmpty() }

        val token = vm.verseSelectionToken.value
        vm.selectSearchResult(vm.searchResults.value.first())
        awaitUntil("navigation") { vm.verseSelectionToken.value > token }

        assertEquals(0, vm.selectedBookIndex.value, "that phrase only occurs in Genesis 1")
        assertEquals(1, vm.selectedChapter.value)
    }

    // ── Adding to the schedule ──────────────────────────────────────────────────

    @Test
    fun `the current verse can be added to the schedule`() {
        openChapter(1, 3) // John 3
        vm.selectVerse(1)

        var captured: Triple<String, Int, Int>? = null
        val added = vm.addCurrentVerseToSchedule { bookName, chapter, verseNumber, _, _, _ ->
            captured = Triple(bookName, chapter, verseNumber)
        }

        assertTrue(added)
        assertEquals(Triple("John", 3, 2), captured)
    }

    @Test
    fun `adding a multi-verse selection carries the range and clears it`() {
        openChapter(1, 3)
        vm.selectVerse(0)
        vm.shiftClickVerse(2)

        var range = ""
        assertTrue(vm.addCurrentVerseToSchedule { _, _, _, _, verseRange, _ -> range = verseRange })
        assertEquals("1-3", range)
        assertFalse(vm.multiVerseEnabled.value, "the next pick should start clean")
    }

    @Test
    fun `adding with nothing loaded reports failure`() {
        var called = false
        val added = BibleViewModel(AppSettings()).addCurrentVerseToSchedule { _, _, _, _, _, _ -> called = true }
        assertFalse(added)
        assertFalse(called, "an empty Bible must not add a blank scripture item")
    }
}
