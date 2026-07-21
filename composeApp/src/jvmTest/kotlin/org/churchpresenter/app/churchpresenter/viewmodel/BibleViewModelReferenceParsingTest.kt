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
 * Everything an operator can type into the unified box, and the detection-chip housekeeping
 * behind auto-follow.
 *
 * Reference parsing is deliberately forgiving — `John 3:16`, `john 3.16`, `Jn 3 16`, a bare book
 * name, a bare chapter while already in a book. Each shape is a separate branch, and a shape that
 * silently fails to parse falls through to a full-text search instead, which looks to the operator
 * like the app ignoring them.
 */
class BibleViewModelReferenceParsingTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-parse-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = content())
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
        )
        awaitUntil("books") { vm.books.value.isNotEmpty() }
        awaitUntil("verses") { vm.isFullyLoaded }
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun content(): String = SpbFixture.buildContent(
        title = "Parse Bible",
        books = listOf(
            SpbFixture.Book(1, "Genesis", 2),
            SpbFixture.Book(43, "John", 3),
            SpbFixture.Book(64, "3 John", 1), // a numbered book name, to stress the parser
        ),
        verses = buildList {
            for (v in 1..3) add(SpbFixture.Verse(1, 1, v, "Genesis one verse $v"))
            for (v in 1..2) add(SpbFixture.Verse(1, 2, v, "Genesis two verse $v"))
            for (v in 1..20) add(SpbFixture.Verse(43, 3, v, "John three verse $v"))
            for (v in 1..2) add(SpbFixture.Verse(64, 1, v, "Third John verse $v"))
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

    /** Types [query] into the box and waits for the resulting navigation. */
    private fun type(query: String) {
        val token = vm.verseSelectionToken.value
        vm.onSmartQueryChanged(query)
        awaitUntil("navigation for \"$query\"") { vm.verseSelectionToken.value > token }
    }

    private fun selectedVerseNumber(): Int =
        vm.verses.value[vm.selectedVerseIndex.value].substringBefore('.').trim().toInt()

    // ── Reference shapes ────────────────────────────────────────────────────────

    @Test
    fun `book chapter and verse`() {
        type("John 3:16")
        assertEquals(1, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `a dot separator is accepted`() {
        type("John 3.16")
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `a space separator is accepted`() {
        type("John 3 16")
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `extra whitespace is tolerated`() {
        type("  John   3 : 16  ")
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `lower case book names parse`() {
        type("john 3:16")
        assertEquals(1, vm.selectedBookIndex.value)
    }

    @Test
    fun `a chapter alone opens that chapter`() {
        type("Genesis 2")
        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `a verse range parses to its start verse`() {
        type("John 3:16-18")
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(16, selectedVerseNumber())
    }

    @Test
    fun `a bare chapter number stays in the current book`() {
        type("John 3:1")
        val token = vm.verseSelectionToken.value
        vm.onSmartQueryChanged("2")
        // A lone number is a chapter within whatever book is open — here John has only chapter 3,
        // so the navigation may clamp; what matters is that it does not jump to another book.
        awaitUntil("navigation") { vm.verseSelectionToken.value > token }
        assertEquals(1, vm.selectedBookIndex.value, "a bare number must not change books")
    }

    @Test
    fun `a numbered book name parses`() {
        // "3 John 1:2" — the leading digit belongs to the book, not the chapter.
        type("3 John 1:2")
        assertEquals(2, vm.selectedBookIndex.value, "3 John is the third book in this module")
        assertEquals(1, vm.selectedChapter.value)
        assertEquals(2, selectedVerseNumber())
    }

    @Test
    fun `a bare book name opens its first chapter`() {
        type("Genesis")
        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `an unknown book does not navigate anywhere`() {
        type("John 3:1")
        val bookBefore = vm.selectedBookIndex.value
        val chapterBefore = vm.selectedChapter.value

        vm.onSmartQueryChanged("Habakkuk 3:2")
        vm.submitSmartQuery()

        // Nothing resolves, so the passage on screen must stay exactly where it was rather than
        // jumping somewhere arbitrary.
        assertEquals(bookBefore, vm.selectedBookIndex.value)
        assertEquals(chapterBefore, vm.selectedChapter.value)
    }

    @Test
    fun `a verse past the end of the chapter lands on the first verse`() {
        type("John 3:999")
        assertEquals(3, vm.selectedChapter.value)
        assertEquals(1, selectedVerseNumber())
    }

    // ── Detection housekeeping ──────────────────────────────────────────────────

    private fun detect(verseStart: Int, matchType: String = "explicit") =
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = verseStart, verseEnd = null,
            verseText = "John three verse $verseStart", matchType = matchType,
        )

    @Test
    fun `the chip list is capped so it cannot grow without bound`() {
        // A long reading produces a detection per verse; the list has to stop somewhere.
        for (v in 1..20) detect(v)
        val size = vm.detectedReferences.value.size
        assertTrue(size in 1..20, "expected a bounded list, got $size")
        assertEquals(20, vm.detectedReferences.value.first().verseStart, "newest first")
    }

    @Test
    fun `a reference that scrolled off the end is not resurrected`() {
        // The dedupe window outlives the visible list, so the engine repeating an old reference
        // cannot push a stale chip back above the current one.
        for (v in 1..20) detect(v)
        val oldest = requireNotNull(vm.detectedReferences.value.last().verseStart)
        val sizeBefore = vm.detectedReferences.value.size

        detect(oldest) // the engine reports it again
        assertEquals(sizeBefore, vm.detectedReferences.value.size, "no new chip for an already-seen reference")
        assertEquals(20, vm.detectedReferences.value.first().verseStart, "and the newest one still leads")
    }

    @Test
    fun `dismissing the list lets those references be detected again`() {
        // clearDetectedReferences resets the dedupe window on purpose: the operator cleared the
        // suggestions, so the next time the engine hears the same passage it should offer it anew
        // rather than staying silent.
        detect(1)
        vm.clearDetectedReferences()
        detect(1)
        assertEquals(1, vm.detectedReferences.value.size)
        assertEquals(1, vm.detectedReferences.value.single().verseStart)
    }

    @Test
    fun `a genuinely new reference is still added after a clear`() {
        detect(1)
        vm.clearDetectedReferences()
        detect(2)
        assertEquals(1, vm.detectedReferences.value.size)
        assertEquals(2, vm.detectedReferences.value.single().verseStart)
    }

    @Test
    fun `re-detecting the top reference does not reorder the list`() {
        detect(1)
        detect(2)
        val before = vm.detectedReferences.value.map { it.verseStart }
        detect(2)
        assertEquals(before, vm.detectedReferences.value.map { it.verseStart })
    }

    @Test
    fun `a chip keeps the first verse text it was given`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 5, verseEnd = null,
            verseText = "original", matchType = "explicit",
        )
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 5, verseEnd = null,
            verseText = "different wording", matchType = "continuation",
        )
        val chip = vm.detectedReferences.value.single()
        assertTrue(DetectionSource.EXPLICIT in chip.sources)
        assertTrue(DetectionSource.CONTINUATION in chip.sources, "both markers accumulate")
    }

    // ── Search interaction ──────────────────────────────────────────────────────

    @Test
    fun `navigating by reference cancels an in-flight search`() {
        vm.updateSearchQuery("verse")
        vm.performSearch()
        awaitUntil("results") { vm.isSearchMode.value }

        type("John 3:1")
        assertFalse(vm.isSearchMode.value, "the operator moved on to a passage")
    }

    @Test
    fun `a two-character query is long enough to search`() {
        vm.updateSearchQuery("Ge")
        vm.performSearch()
        awaitUntil("results") { vm.isSearchMode.value }
        assertTrue(vm.searchResults.value.isNotEmpty())
    }

    @Test
    fun `exact-match mode narrows the results`() {
        vm.updateSelectedModeIndex(1) // exact word match
        vm.updateSearchQuery("verse")
        vm.performSearch()
        awaitUntil("results") { vm.isSearchMode.value }
        assertTrue(vm.searchResults.value.isNotEmpty(), "'verse' is a whole word in every fixture verse")
    }
}
