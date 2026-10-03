package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

class BibleViewModelParallelTranslationTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-parallel").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        SpbFixture.spbFile(
            dir, name = "second.spb",
            content = SpbFixture.buildContent(
                title = "Second Bible",
                books = listOf(SpbFixture.Book(1, "Genesis", 2), SpbFixture.Book(43, "John", 1)),
                verses = listOf(
                    SpbFixture.Verse(1, 1, 1, "Im Anfang"),
                    SpbFixture.Verse(1, 1, 2, "Und die Erde"),
                    SpbFixture.Verse(43, 3, 16, "Also hat Gott"),
                ),
            ),
        )
        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "test.spb",
                    translations = listOf(
                        BibleTranslationSettings(fileName = "test.spb"),
                        BibleTranslationSettings(fileName = "second.spb"),
                    ),
                ),
            ),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.loadedTranslations.value.size == 2 && vm.verses.value.isNotEmpty())) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    @Test
    fun `a verse fetched for display carries every translation that has it`() = runBlocking {
        val verses = vm.getVersesForDisplay("John", 3, 16)

        assertEquals(listOf("For God so loved the world.", "Also hat Gott"), verses.map { it.verseText })
        assertEquals("second.spb", verses[1].translationFileName)
    }

    @Test
    fun `a verse only one translation has is fetched in that one alone`() = runBlocking {
        assertEquals(1, vm.getVersesForDisplay("Genesis", 1, 3).size)
    }

    @Test
    fun `a passage joins each translation's verses into one entry apiece`() {
        vm._multiVerseEnabled.value = true
        vm._selectedVerseIndices.addAll(listOf(0, 1))

        val passage = vm.getSelectedVerses()

        assertEquals(2, passage.size)
        assertEquals("Im Anfang Und die Erde", passage[1].verseText)
        assertEquals("1-2", passage[1].verseRange)
    }
}
