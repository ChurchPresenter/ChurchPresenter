package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BibleViewModelSplitVerseStepTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-split-step").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Long",
            books = listOf(SpbFixture.Book(1, "Genesis", 2), SpbFixture.Book(43, "John", 1)),
            verses = listOf(
                SpbFixture.Verse(1, 1, 1, LONG_VERSE),
                SpbFixture.Verse(1, 1, 2, "And the earth was without form, and void."),
                SpbFixture.Verse(1, 1, 3, "And God said, Let there be light."),
                SpbFixture.Verse(1, 2, 1, "Thus the heavens and the earth were finished."),
            ),
        ))
        vm = BibleViewModel(
            AppSettings().withBibleEverywhere(
                BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "test.spb",
                    splitLongVerses = true,
                    longVerseWordCount = 25,
                ),
            ),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.verses.value.isNotEmpty())) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
        vm.selectVerse(0)
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    @Test
    fun `after a split verse's first half the next thing up is its second half`() {
        val next = vm.getNextVerses().first()

        assertEquals(1, next.verseNumber)
        assertNotEquals(LONG_VERSE, next.verseText)
        assertTrue(LONG_VERSE.endsWith(next.verseText.trim()))
    }

    @Test
    fun `down steps to a split verse's second half before the next verse`() {
        assertTrue(vm.navigateNextVerse())
        assertEquals(0, vm.selectedVerseIndex.value, "still verse one, now its second half")

        assertTrue(vm.navigateNextVerse())
        assertEquals(1, vm.selectedVerseIndex.value)
    }

    @Test
    fun `up from a verse lands on the previous verse's second half`() {
        vm.selectVerse(1)

        assertTrue(vm.navigatePreviousVerse())
        assertEquals(0, vm.selectedVerseIndex.value)
        assertTrue(vm.navigatePreviousVerse(), "the first half is still above")
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `after a passage ending the chapter the next verse opens the next chapter`() {
        vm.selectVerse(1)
        vm.ctrlClickVerse(2)

        val next = vm.getNextVerses().first()
        assertEquals(2, next.chapter)
        assertEquals(1, next.verseNumber)
    }

    private companion object {
        val LONG_VERSE = List(60) { "word$it" }.joinToString(" ")
    }
}
