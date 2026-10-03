package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveVerseSplitMarkGuardTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel
    private val longVerse = (1..60).joinToString(" ") { "word$it" }
    private val firstHalf get() = splitAtWordMidpoint(longVerse).first

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-split-mark").toFile()
        SpbFixture.spbFile(
            dir, name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Split Bible",
                books = listOf(SpbFixture.Book(17, "Esther", 1)),
                verses = listOf(SpbFixture.Verse(17, 1, 1, "Short"), SpbFixture.Verse(17, 1, 2, longVerse)),
            ),
        )
        vm = BibleViewModel(
            AppSettings().withBibleEverywhere(
                BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb", splitLongVerses = true),
            ),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.verses.value.isNotEmpty())) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun live(
        bookName: String = "Esther",
        chapter: Int = 1,
        verse: Int = 2,
        text: String = firstHalf,
        range: String = "",
    ) = SelectedVerse(bookName = bookName, chapter = chapter, verseNumber = verse, verseText = text, verseRange = range)

    @Test
    fun `the first half of the long verse on screen is marked`() {
        assertNotNull(vm.liveVerseSplitMark(live()))
    }

    @Test
    fun `nothing is marked for another chapter, a passage, another book or a missing verse`() {
        assertNull(vm.liveVerseSplitMark(live(chapter = 2)))
        assertNull(vm.liveVerseSplitMark(live(range = "1-2")))
        assertNull(vm.liveVerseSplitMark(live(bookName = "Ruth")))
        assertNull(vm.liveVerseSplitMark(live(verse = 9)))
        assertNull(vm.liveVerseSplitMark(live(text = "not either half")))
        assertNull(vm.liveVerseSplitMark(null))
    }

    @Test
    fun `nothing is marked once the module is gone`() {
        vm._primaryBible.value = null

        assertNull(vm.liveVerseSplitMark(live()))
    }
}
