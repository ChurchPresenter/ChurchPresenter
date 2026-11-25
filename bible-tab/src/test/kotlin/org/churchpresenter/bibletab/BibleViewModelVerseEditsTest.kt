package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibleedits.BibleEditsStore
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleViewModelVerseEditsTest {

    private lateinit var dir: File
    private lateinit var editsDir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-verse-edits-vm").toFile()
        editsDir = File(dir, "data")
        SpbFixture.spbFile(dir, name = "kjv.spb")
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun save(text: String) = BibleEditsStore(editsDir).save(
        listOf(EditEntry(1, 0, "kjv.spb", 1, 1, 3, after = VerseChange(text = text))),
    )

    private fun viewModel(folder: () -> File?) = BibleViewModel(
        AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath)
                .withTranslations(listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
        ),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        verseEditsFolder = folder,
    ).also { it.awaitLoaded() }

    private fun BibleViewModel.awaitLoaded() = runBlocking {
        withTimeout(5_000) { isFullyLoadedFlow.first { it } }
    }

    private fun BibleViewModel.verse3(): String? = primaryBible.value?.getVerseDetails(1, 1, 3)?.second

    @Test
    fun `a correction saved in the editor is what the Bible tab loads`() {
        save("And God said, Let there be light!")
        assertEquals("And God said, Let there be light!", viewModel { editsDir }.verse3())
    }

    @Test
    fun `with no edits folder the file's own text is loaded`() {
        save("And God said, Let there be light!")
        assertEquals("And God said, Let there be light: and there was light.", viewModel { null }.verse3())
    }

    @Test
    fun `a correction saved later shows once the Bibles are loaded again`() {
        val model = viewModel { editsDir }
        save("Corrected.")
        model.reloadForVerseEdits()
        model.awaitLoaded()
        assertEquals("Corrected.", model.verse3())
    }
}
