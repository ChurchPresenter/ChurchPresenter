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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BibleViewModelBookNamesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-book-names-vm").toFile()
        listOf("kjv.spb", "rst.spb").forEach { SpbFixture.spbFile(dir, name = it) }
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun stack(kjv: Map<Int, String> = emptyMap(), rst: Map<Int, String> = emptyMap()) = AppSettings(
        bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            listOf(
                BibleTranslationSettings(fileName = "kjv.spb", bookNameOverrides = kjv),
                BibleTranslationSettings(fileName = "rst.spb", bookNameOverrides = rst),
            ),
        ),
    )

    private fun viewModel(settings: AppSettings) =
        BibleViewModel(settings, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)

    @Test
    fun `a module is loaded under its own overrides`() {
        val model = viewModel(stack(kjv = mapOf(1 to "Bereshit"), rst = mapOf(1 to "Бытие")))

        assertEquals("Bereshit", model.books.value.first())
        assertEquals(listOf("Bereshit", "Бытие"), model.loadedBibles.value.map { it.getBookName(1) })
    }

    @Test
    fun `the verse handed to the presenter carries the override`() {
        val model = viewModel(stack(kjv = mapOf(1 to "Bereshit")))

        assertEquals("Bereshit", model.getSelectedVerses().first().bookName)
    }

    @Test
    fun `an override saved while loaded reaches the book list without reading the files again`() {
        val model = viewModel(stack())
        dir.listFiles().orEmpty().forEach { assertTrue(it.delete(), "could not delete ${it.name}") }

        model.updateSettings(stack(kjv = mapOf(1 to "Bereshit"), rst = mapOf(1 to "Бытие")))

        assertEquals("Bereshit", model.books.value.first())
        assertEquals("Бытие", model.loadedBibles.value[1].getBookName(1))
    }

    @Test
    fun `an override saved while loaded re-emits the selection so the screen follows`() {
        val model = viewModel(stack())
        val before = model.verseSelectionToken.value

        model.updateSettings(stack(kjv = mapOf(1 to "Bereshit")))

        assertNotEquals(before, model.verseSelectionToken.value)
        assertEquals("Bereshit", model.getSelectedVerses().first().bookName)
    }

    @Test
    fun `clearing an override gives the file's name back`() {
        val model = viewModel(stack(kjv = mapOf(1 to "Bereshit")))

        model.updateSettings(stack())

        assertEquals("Genesis", model.books.value.first())
    }

    @Test
    fun `a file naming its books only by number is listed in English`() {
        SpbFixture.spbFile(
            dir,
            name = "kjv.spb",
            content = SpbFixture.buildContent(
                title = "Numbered",
                books = listOf(SpbFixture.Book(43, "Book 43", 1)),
                verses = listOf(SpbFixture.Verse(43, 1, 1, "In the beginning was the Word.")),
            ),
        )

        assertEquals(listOf("John"), viewModel(stack()).books.value)
    }

    @Test
    fun `a file with no book list at all is listed in English`() {
        SpbFixture.spbFile(
            dir,
            name = "kjv.spb",
            content = SpbFixture.buildContent(
                title = "Bare",
                books = emptyList(),
                verses = listOf(SpbFixture.Verse(43, 1, 1, "In the beginning was the Word.")),
            ),
        )

        assertEquals(listOf("John"), viewModel(stack()).books.value)
    }
}
