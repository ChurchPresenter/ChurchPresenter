package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleViewModelMultiVerseSelectionTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-multiverse-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        try {
            runBlocking { withTimeout(timeoutMs) { while (!condition()) yield() } }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
        }
    }

    private fun writeBible(name: String, title: String, bookName: String, prefix: String, books: Int) {
        val bookList = buildList {
            add(SpbFixture.Book(43, bookName, 2))
            if (books > 1) add(SpbFixture.Book(44, "$bookName II", 1))
        }
        val verses = buildList {
            addAll((1..4).map { SpbFixture.Verse(43, 1, it, "$prefix 1:$it") })
            addAll((1..2).map { SpbFixture.Verse(43, 2, it, "$prefix 2:$it") })
            if (books > 1) add(SpbFixture.Verse(44, 1, 1, "$prefix next-book"))
        }
        SpbFixture.spbFile(dir, name = name, content = SpbFixture.buildContent(title, bookList, verses))
    }

    private fun loaded(vararg files: String): BibleViewModel {
        val model = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    files.map { BibleTranslationSettings(fileName = it) },
                ),
            ),
        )
        runBlocking { model.isFullyLoadedFlow.first { it } }
        return model
    }

    private fun BibleViewModel.openChapter(bookIndex: Int, chapter: Int) {
        val token = verseSelectionToken.value
        loadChapter(bookIndex, chapter)
        awaitUntil("chapter $bookIndex:$chapter") { verseSelectionToken.value > token }
    }

    private fun oneEnglishBible(books: Int = 1): BibleViewModel {
        writeBible("p.spb", "Primary", "John", "English", books)
        return loaded("p.spb").also { it.openChapter(0, 1) }
    }

    private fun twoBibles(): BibleViewModel {
        writeBible("p.spb", "Primary", "John", "English", 1)
        writeBible("s.spb", "Secondary", "Иоанна", "Русский", 1)
        return loaded("p.spb", "s.spb").also { it.openChapter(0, 1) }
    }

    @Test
    fun `ctrl-selected verses are joined into one entry`() {
        val model = oneEnglishBible()
        model.selectVerse(0)
        model.ctrlClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals(1, selected.size)
        assertEquals("English 1:1 English 1:2", selected[0].verseText)
        assertEquals(1, selected[0].verseNumber)
        assertEquals("1-2", selected[0].verseRange)
    }

    @Test
    fun `a non-contiguous selection keeps both numbers in the range`() {
        val model = oneEnglishBible()
        model.selectVerse(0)
        model.ctrlClickVerse(2)

        assertEquals("1,3", model.getSelectedVerses()[0].verseRange)
    }

    @Test
    fun `a shift-selected run is joined in verse order however it was clicked`() {
        val model = oneEnglishBible()
        model.selectVerse(3)
        model.shiftClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals("English 1:2 English 1:3 English 1:4", selected[0].verseText)
        assertEquals("2-4", selected[0].verseRange)
    }

    @Test
    fun `a multi-verse selection carries every translation`() {
        val model = twoBibles()
        model.selectVerse(0)
        model.ctrlClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals(2, selected.size)
        assertEquals("English 1:1 English 1:2", selected[0].verseText)
        assertEquals("Русский 1:1 Русский 1:2", selected[1].verseText)
        assertEquals("1-2", selected[1].verseRange)
        assertEquals("Иоанна", selected[1].bookName)
    }

    @Test
    fun `a multi-verse selection names each translation`() {
        val model = twoBibles()
        model.selectVerse(0)
        model.ctrlClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals("p.spb", selected[0].translationFileName)
        assertEquals("s.spb", selected[1].translationFileName)
        assertEquals("Primary", selected[0].bibleName)
        assertEquals("Secondary", selected[1].bibleName)
    }

    @Test
    fun `an empty secondary contributes nothing to a multi-verse selection`() {
        writeBible("p.spb", "Primary", "John", "English", 1)
        SpbFixture.spbFile(
            dir, name = "s.spb",
            content = SpbFixture.buildContent(
                "Secondary", listOf(SpbFixture.Book(43, "Иоанна", 2)), emptyList(),
            ),
        )
        val model = loaded("p.spb", "s.spb").also { it.openChapter(0, 1) }
        model.selectVerse(0)
        model.ctrlClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals(1, selected.size)
        assertEquals("English 1:1 English 1:2", selected[0].verseText)
    }

    @Test
    fun `the look-ahead is the next verse in the chapter`() {
        val model = oneEnglishBible()
        model.selectVerse(0)

        assertEquals("English 1:2", model.getNextVerses()[0].verseText)
    }

    @Test
    fun `the look-ahead after a multi-verse selection follows its last verse`() {
        val model = oneEnglishBible()
        model.selectVerse(0)
        model.ctrlClickVerse(2)

        assertEquals("English 1:4", model.getNextVerses()[0].verseText)
    }

    @Test
    fun `the look-ahead rolls into the next chapter at the end of one`() {
        val model = oneEnglishBible()
        model.selectVerse(3)

        val next = model.getNextVerses()

        assertEquals("English 2:1", next[0].verseText)
        assertEquals(2, next[0].chapter)
        assertEquals(1, next[0].verseNumber)
    }

    @Test
    fun `the look-ahead rolls into the next book at the end of one`() {
        val model = oneEnglishBible(books = 2)
        model.openChapter(0, 2)
        model.selectVerse(1)

        val next = model.getNextVerses()

        assertEquals("English next-book", next[0].verseText)
        assertEquals(1, next[0].chapter)
    }

    @Test
    fun `there is no look-ahead past the last verse of the last book`() {
        val model = oneEnglishBible()
        model.openChapter(0, 2)
        model.selectVerse(1)

        assertTrue(model.getNextVerses().isEmpty())
    }

    @Test
    fun `the look-ahead carries every translation`() {
        val model = twoBibles()
        model.selectVerse(0)

        val next = model.getNextVerses()

        assertEquals(2, next.size)
        assertEquals("English 1:2", next[0].verseText)
        assertEquals("Русский 1:2", next[1].verseText)
        assertEquals("s.spb", next[1].translationFileName)
    }

    @Test
    fun `nothing is selected when no bible is configured at all`() {
        val model = loaded()

        assertTrue(model.getSelectedVerses().isEmpty())
        assertTrue(model.getNextVerses().isEmpty())
    }

    private fun partialSecondary(secondaryVerses: List<Int>): BibleViewModel {
        writeBible("p.spb", "Primary", "John", "English", 1)
        SpbFixture.spbFile(
            dir, name = "s.spb",
            content = SpbFixture.buildContent(
                "Secondary",
                listOf(SpbFixture.Book(43, "\u0418\u043e\u0430\u043d\u043d\u0430", 2)),
                secondaryVerses.map { SpbFixture.Verse(43, 1, it, "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 1:$it") },
            ),
        )
        return loaded("p.spb", "s.spb").also { it.openChapter(0, 1) }
    }

    @Test
    fun `a verse the secondary translation skips comes back in the primary alone`() {
        val model = partialSecondary(listOf(1, 3))
        model.selectVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals(1, selected.size)
        assertEquals("English 1:2", selected[0].verseText)
    }

    @Test
    fun `a multi-verse selection drops only the verses the secondary skips`() {
        val model = partialSecondary(listOf(1, 3))
        model.selectVerse(0)
        model.ctrlClickVerse(1)

        val selected = model.getSelectedVerses()

        assertEquals(2, selected.size)
        assertEquals("English 1:1 English 1:2", selected[0].verseText)
        assertEquals(
            "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 1:1",
            selected[1].verseText,
            "the secondary carries only what it actually has",
        )
    }

    @Test
    fun `a look-ahead verse the secondary skips comes back in the primary alone`() {
        val model = partialSecondary(listOf(1, 3))
        model.selectVerse(0)

        val next = model.getNextVerses()

        assertEquals(1, next.size)
        assertEquals("English 1:2", next[0].verseText)
    }

    @Test
    fun `an empty secondary contributes nothing to a single selection`() {
        writeBible("p.spb", "Primary", "John", "English", 1)
        SpbFixture.spbFile(
            dir, name = "s.spb",
            content = SpbFixture.buildContent(
                "Secondary", listOf(SpbFixture.Book(43, "\u0418\u043e\u0430\u043d\u043d\u0430", 2)), emptyList(),
            ),
        )
        val model = loaded("p.spb", "s.spb").also { it.openChapter(0, 1) }
        model.selectVerse(0)

        assertEquals(1, model.getSelectedVerses().size)
    }

    @Test
    fun `an empty secondary contributes nothing to the look-ahead`() {
        writeBible("p.spb", "Primary", "John", "English", 1)
        SpbFixture.spbFile(
            dir, name = "s.spb",
            content = SpbFixture.buildContent(
                "Secondary", listOf(SpbFixture.Book(43, "\u0418\u043e\u0430\u043d\u043d\u0430", 2)), emptyList(),
            ),
        )
        val model = loaded("p.spb", "s.spb").also { it.openChapter(0, 1) }
        model.selectVerse(0)

        assertEquals(1, model.getNextVerses().size)
    }
}
