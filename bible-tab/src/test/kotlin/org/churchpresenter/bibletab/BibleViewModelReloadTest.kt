package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
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

class BibleViewModelReloadTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-load-branch").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun module(file: String, text: String, bookId: Int = 43, bookName: String = "John", folder: File = dir) {
        SpbFixture.spbFile(
            folder, name = file,
            content = SpbFixture.buildContent(
                title = file,
                books = listOf(SpbFixture.Book(bookId, bookName, 1)),
                verses = listOf(SpbFixture.Verse(bookId, 1, 1, text), SpbFixture.Verse(bookId, 1, 2, "$text two")),
            ),
        )
    }

    private fun stack(vararg files: String, folder: File = dir) = BibleSettings(storageDirectory = folder.absolutePath)
        .withTranslations(files.map { BibleTranslationSettings(fileName = it) })

    private fun viewModel(
        settings: BibleSettings,
        loaded: MutableList<String> = mutableListOf(),
        secondaryPaths: MutableList<String> = mutableListOf(),
    ) = BibleViewModel(
        AppSettings(bibleSettings = settings),
        onBibleLoaded = { _, translation -> loaded += translation },
        onSecondaryBibleFilePathChanged = { secondaryPaths += it },
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    ).also { it.awaitLoaded() }

    private fun BibleViewModel.awaitLoaded() {
        val deadline = System.currentTimeMillis() + 5_000
        while (!isFullyLoaded) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out loading")
            Thread.sleep(10)
        }
    }

    private fun BibleViewModel.update(settings: AppSettings) {
        updateSettings(settings)
        awaitLoaded()
    }

    @Test
    fun `a secondary translation reports its path and the primary its name`() {
        module("p.spb", "English")
        module("s.spb", "Русский")
        val loaded = mutableListOf<String>()
        val secondary = mutableListOf<String>()
        viewModel(stack("p.spb", "s.spb"), loaded, secondary)
        assertEquals(listOf("p.spb"), loaded)
        assertEquals(listOf(File(dir, "s.spb").absolutePath), secondary)
    }

    @Test
    fun `a single translation reports no secondary path`() {
        module("p.spb", "English")
        val secondary = mutableListOf<String>()
        val m = viewModel(stack("p.spb"), secondaryPaths = secondary)
        assertEquals(listOf("John"), m.books.value)
        assertTrue(secondary.isEmpty())
    }

    @Test
    fun `turning long-verse splitting on re-cuts what is shown`() {
        module("p.spb", "English")
        val settings = stack("p.spb")
        val model = viewModel(settings)
        model.update(AppSettings().withBibleEverywhere(settings.copy(splitLongVerses = true)))
        assertTrue(model.splitLongVersesEnabled)
        model.update(
            AppSettings().withBibleEverywhere(settings.copy(splitLongVerses = true, longVerseWordCount = 3)),
        )
        assertEquals(3, model.longVerseWordCount)
    }

    @Test
    fun `a rename relabels the loaded translations and re-emits the live verse`() {
        module("p.spb", "English")
        module("s.spb", "Русский")
        val settings = stack("p.spb", "s.spb")
        val model = viewModel(settings)
        val token = model.verseSelectionToken.value
        val renamed = settings.withTranslations(
            listOf(
                BibleTranslationSettings(fileName = "p.spb", customName = "Ours", customAbbreviation = "OUR"),
                BibleTranslationSettings(fileName = "s.spb"),
            ),
        )
        model.update(AppSettings(bibleSettings = renamed))
        assertEquals("Ours", model.loadedTranslations.value.first().bible.getBibleTitle())
        assertEquals("OUR", model.getSelectedVerses().first().bibleAbbreviation)
        assertTrue(model.verseSelectionToken.value != token)
    }

    @Test
    fun `a rename with nothing loaded changes nothing`() {
        val settings = stack("gone.spb")
        val model = viewModel(settings)
        val renamed = settings.withTranslations(
            listOf(BibleTranslationSettings(fileName = "gone.spb", customName = "X")),
        )
        model.update(AppSettings(bibleSettings = renamed))
        assertTrue(model.loadedTranslations.value.isEmpty())
        assertTrue(model.verses.value.isEmpty())
    }

    @Test
    fun `a missing module is reported and an empty file name is not`() {
        module("p.spb", "English")
        val settings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            listOf(BibleTranslationSettings(fileName = "p.spb"), BibleTranslationSettings(fileName = "missing.spb")),
        )
        val model = viewModel(settings)
        assertEquals(1, model.loadErrors.value.size)
        assertTrue(model.loadErrors.value.single().resourcePath.endsWith("missing.spb"))
        assertEquals(listOf("p.spb"), model.loadedTranslations.value.map { it.fileName })
    }

    @Test
    fun `reloading onto a bible without the open book falls back to the selected position`() {
        module("p.spb", "English")
        val other = Files.createTempDirectory("cp-bible-load-other").toFile()
        try {
            module("g.spb", "Genesis words", bookId = 1, bookName = "Genesis", folder = other)
            val model = viewModel(stack("p.spb"))
            assertEquals(listOf("John"), model.books.value)
            model.update(AppSettings(bibleSettings = stack("g.spb", folder = other)))
            assertEquals(listOf("Genesis"), model.books.value)
            assertEquals(0, model.selectedBookIndex.value)
            assertTrue(model.verses.value.isNotEmpty())
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `reordering with nothing loaded is a no-op`() {
        val model = viewModel(BibleSettings())
        model.applyTranslationOrder()
        assertTrue(model.loadedTranslations.value.isEmpty())
    }

    @Test
    fun `losing every module empties the books and verses`() {
        module("p.spb", "English")
        val model = viewModel(stack("p.spb"))
        assertTrue(model.books.value.isNotEmpty())
        val emptyFolder = Files.createTempDirectory("cp-bible-load-empty").toFile()
        try {
            model.update(AppSettings(bibleSettings = stack("p.spb", folder = emptyFolder)))
            assertTrue(model.books.value.isEmpty())
            assertTrue(model.verses.value.isEmpty())
        } finally {
            emptyFolder.deleteRecursively()
        }
    }
}
