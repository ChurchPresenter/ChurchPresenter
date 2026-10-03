package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.utils.withBibleEverywhere
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleViewModelSelectionBranchCoverageTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-select-branch").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun settings(split: Boolean, file: String) =
        BibleSettings(storageDirectory = dir.absolutePath, primaryBible = file, splitLongVerses = split)

    private fun loaded(split: Boolean = false, file: String = "test.spb"): BibleViewModel {
        val model = BibleViewModel(AppSettings().withBibleEverywhere(settings(split, file)))
        awaitUntil { model.books.value.isNotEmpty() && model.isFullyLoaded && model.verses.value.isNotEmpty() }
        return model
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun BibleViewModel.open(bookIndex: Int, chapter: Int) {
        val token = verseSelectionToken.value
        loadChapter(bookIndex, chapter)
        awaitUntil { verseSelectionToken.value > token }
    }

    @Test
    fun `a single-verse shift selection is read as one verse`() {
        val vm = loaded()
        vm.selectVerse(1)
        vm.shiftClickVerse(1)
        assertEquals(listOf(2), vm.getSelectedVerses().map { it.verseNumber })
        assertEquals(listOf(3), vm.getNextVerses().map { it.verseNumber })
    }

    @Test
    fun `ctrl-clicking the only selected verse off falls back to a single selection`() {
        val vm = loaded()
        vm.selectVerse(0)
        vm.ctrlClickVerse(0)
        vm.ctrlClickVerse(0)
        assertEquals(listOf(1), vm.getSelectedVerses().map { it.verseNumber })
    }

    @Test
    fun `a multi-verse look-ahead follows the highest selected verse`() {
        val vm = loaded()
        vm.selectVerse(0)
        vm.ctrlClickVerse(1)
        assertEquals("1-2", vm.getSelectedVerses().single().verseRange)
        assertEquals(listOf(3), vm.getNextVerses().map { it.verseNumber })
    }

    @Test
    fun `a split verse looks ahead to its own second half first`() {
        val long = (1..80).joinToString(" ") { "word$it" }
        SpbFixture.spbFile(
            dir, name = "long.spb",
            content = SpbFixture.buildContent(
                title = "Long",
                books = listOf(SpbFixture.Book(1, "Genesis", 1)),
                verses = listOf(SpbFixture.Verse(1, 1, 1, long), SpbFixture.Verse(1, 1, 2, "short one")),
            ),
        )
        val vm = loaded(split = true, file = "long.spb")
        vm.selectVerse(0)
        val next = vm.getNextVerses()
        assertEquals(listOf(1), next.map { it.verseNumber }, "the second half of verse one")
        assertTrue(vm.stepVersePage(vm.getSelectedVerses().single().verseText, forward = true))
        assertEquals(listOf(2), vm.getNextVerses().map { it.verseNumber }, "then the next verse")
    }

    @Test
    fun `with splitting off the look-ahead is the next verse`() {
        val vm = loaded(split = false)
        vm.selectVerse(0)
        assertEquals(listOf(2), vm.getNextVerses().map { it.verseNumber })
    }

    @Test
    fun `the end of a book whose next has no verses there looks ahead to nothing`() {
        val vm = loaded()
        vm.open(1, 23)
        vm.selectVerse(vm.verses.value.lastIndex)
        assertTrue(vm.getNextVerses().isEmpty())
    }

    @Test
    fun `the last chapter of a book rolls on into the next book's first chapter`() {
        val vm = loaded()
        vm.open(0, 2)
        vm.selectVerse(0)
        assertTrue(vm.getNextVerses().isEmpty(), "Psalms has no verses in chapter 1")
    }
}
