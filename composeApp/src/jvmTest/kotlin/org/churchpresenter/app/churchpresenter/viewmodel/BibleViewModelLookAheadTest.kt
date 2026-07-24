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
import kotlin.test.assertTrue

/**
 * The Stage-Monitor "next verse" look-ahead ([BibleViewModel.getNextVerses]) as it rolls off the
 * end of a chapter, a book, and the whole bible.
 *
 * The existing look-ahead tests live inside a single chapter, so the roll-over paths — into the
 * next chapter, into the next book when that was the last chapter, and off the end of the last book
 * — never run, nor does the multi-selection case that looks ahead from the *last* selected verse.
 * A multi-book fixture exercises all of them with real bible data (no mocking).
 */
class BibleViewModelLookAheadTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-lookahead-test").toFile()
        // Genesis: 2 chapters x 2 verses; Exodus: 1 chapter x 2 verses.
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Look-ahead Bible",
            books = listOf(SpbFixture.Book(1, "Genesis", 2), SpbFixture.Book(2, "Exodus", 1)),
            verses = buildList {
                add(SpbFixture.Verse(1, 1, 1, "Gen one one"))
                add(SpbFixture.Verse(1, 1, 2, "Gen one two"))
                add(SpbFixture.Verse(1, 2, 1, "Gen two one"))
                add(SpbFixture.Verse(1, 2, 2, "Gen two two"))
                add(SpbFixture.Verse(2, 1, 1, "Exo one one"))
                add(SpbFixture.Verse(2, 1, 2, "Exo one two"))
            },
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
        )
        awaitUntil("load") { vm.books.value.isNotEmpty() && vm.isFullyLoaded }
    }

    @AfterTest
    fun cleanUp() {
        runCatching { vm.dispose() }
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** Loads [bookIndex]/[chapter] and waits for its verses to be live. */
    private fun openChapter(bookIndex: Int, chapter: Int) {
        val token = vm.verseSelectionToken.value
        vm.loadChapter(bookIndex, chapter)
        awaitUntil("chapter $bookIndex:$chapter") { vm.verseSelectionToken.value > token }
    }

    @Test
    fun `mid-chapter, the look-ahead is simply the next verse`() {
        openChapter(0, 1)
        vm.selectVerse(0) // Genesis 1:1

        val next = vm.getNextVerses()

        assertEquals("Gen one two", next.single().verseText)
        assertEquals(2, next.single().verseNumber)
    }

    @Test
    fun `at the end of a chapter, the look-ahead rolls into the next chapter`() {
        openChapter(0, 1)
        vm.selectVerse(1) // Genesis 1:2, the last verse of the chapter

        val next = vm.getNextVerses()

        assertEquals("Gen two one", next.single().verseText, "the next verse is the first of the following chapter")
        assertEquals(2, next.single().chapter)
        assertEquals(1, next.single().verseNumber)
    }

    @Test
    fun `at the end of the last chapter, the look-ahead rolls into the next book`() {
        openChapter(0, 2)
        vm.selectVerse(1) // Genesis 2:2, the last verse of the last chapter of Genesis

        val next = vm.getNextVerses()

        assertEquals("Exo one one", next.single().verseText, "past the last chapter it crosses into the next book")
    }

    @Test
    fun `at the very end of the bible, the look-ahead is empty`() {
        openChapter(1, 1)
        vm.selectVerse(1) // Exodus 1:2, the last verse of the last book

        assertTrue(vm.getNextVerses().isEmpty(), "there is nothing after the last verse of the last book")
    }

    @Test
    fun `a multi-selection looks ahead from the last selected verse`() {
        openChapter(0, 1)
        vm.ctrlClickVerse(0)
        vm.ctrlClickVerse(1) // both verses of Genesis 1 selected

        val next = vm.getNextVerses()

        assertEquals(
            "Gen two one",
            next.single().verseText,
            "the look-ahead follows the highest selected verse, not the anchor",
        )
    }
}
