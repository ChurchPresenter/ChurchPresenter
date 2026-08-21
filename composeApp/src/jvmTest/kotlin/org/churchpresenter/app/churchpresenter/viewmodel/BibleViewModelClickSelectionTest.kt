package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ctrl- and shift-clicking the verse list, and the book filter's English fallback.
 *
 * These are what an operator does with the mouse mid-service, so the edges matter: a click on the
 * verse that is already current must not select it twice, and a click index the list does not have
 * — a stale row from a chapter that has since changed under it — must leave the selection alone
 * rather than throw or silently select the wrong verse.
 */
class BibleViewModelClickSelectionTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-click-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Click Bible",
            books = listOf(SpbFixture.Book(1, "Бытие", 1), SpbFixture.Book(43, "Иоанна", 1)),
            verses = buildList {
                for (v in 1..5) add(SpbFixture.Verse(1, 1, v, "Бытие 1:$v"))
                for (v in 1..3) add(SpbFixture.Verse(43, 1, v, "Иоанна 1:$v"))
            },
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
        )
        awaitUntil("books") { vm.books.value.isNotEmpty() }
        awaitUntil("verses") { vm.isFullyLoaded && vm.verses.value.size == 5 }
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

    // ── ctrlClickVerse ──────────────────────────────────────────────────────────

    @Test
    fun `ctrl-clicking the verse already showing selects only that verse`() {
        vm.selectVerse(2)

        vm.ctrlClickVerse(2)

        assertEquals(
            listOf(2),
            vm.selectedVerseIndices.sorted(),
            "the anchor is the same verse, so it is not added twice",
        )
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `ctrl-clicking a second verse brings the current one along as the anchor`() {
        vm.selectVerse(1)

        vm.ctrlClickVerse(3)

        assertEquals(listOf(1, 3), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `ctrl-clicking a selected verse again removes it`() {
        vm.selectVerse(1)
        vm.ctrlClickVerse(3)

        vm.ctrlClickVerse(3)

        assertEquals(listOf(1), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `ctrl-clicking a row the chapter does not have is ignored`() {
        vm.selectVerse(0)

        vm.ctrlClickVerse(99)
        vm.ctrlClickVerse(-1)

        assertTrue(vm.selectedVerseIndices.isEmpty())
        assertEquals(0, vm.selectedVerseIndex.value, "the showing verse is untouched")
    }

    // ── shiftClickVerse ─────────────────────────────────────────────────────────

    @Test
    fun `shift-clicking selects the run from the current verse to the clicked one`() {
        vm.selectVerse(1)

        vm.shiftClickVerse(3)

        assertEquals(listOf(1, 2, 3), vm.selectedVerseIndices.sorted())
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `shift-clicking backwards selects the same run`() {
        vm.selectVerse(3)

        vm.shiftClickVerse(1)

        assertEquals(listOf(1, 2, 3), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `shift-clicking the current verse selects only it and is not a multi-selection`() {
        vm.selectVerse(2)

        vm.shiftClickVerse(2)

        assertEquals(listOf(2), vm.selectedVerseIndices.sorted())
        assertFalse(vm.multiVerseEnabled.value, "a run of one is not a range")
    }

    @Test
    fun `shift-clicking a row the chapter does not have is ignored`() {
        vm.selectVerse(1)
        vm.shiftClickVerse(3)

        vm.shiftClickVerse(99)
        vm.shiftClickVerse(-1)

        assertEquals(
            listOf(1, 2, 3),
            vm.selectedVerseIndices.sorted(),
            "the existing run survives an out-of-range click",
        )
    }

    // ── The book filter's English fallback ──────────────────────────────────────

    @Test
    fun `an empty book query lists every book`() {
        vm.updateBookSearchQuery("")
        assertEquals(vm.books.value, vm.getFilteredBooks())
    }

    @Test
    fun `a book query matching the module's own names filters on those`() {
        vm.updateBookSearchQuery("Быт")
        assertEquals(listOf("Бытие"), vm.getFilteredBooks())
    }

    @Test
    fun `an english name finds the book in a module that does not use english`() {
        vm.updateBookSearchQuery("John")
        assertEquals(listOf("Иоанна"), vm.getFilteredBooks())
    }

    @Test
    fun `a query with a digit in it is not tried as an english book name`() {
        // The English fallback is only worth attempting for a plain ASCII word; anything else
        // cannot be a book name and the operator sees an empty list rather than a wrong book.
        vm.updateBookSearchQuery("John3")
        assertTrue(vm.getFilteredBooks().isEmpty())
    }

    @Test
    fun `a non-ascii query that matches no local book falls back to nothing`() {
        vm.updateBookSearchQuery("Псалом")
        assertTrue(vm.getFilteredBooks().isEmpty())
    }
}
