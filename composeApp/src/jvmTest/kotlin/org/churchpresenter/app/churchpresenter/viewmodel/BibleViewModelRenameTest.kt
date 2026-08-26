package org.churchpresenter.app.churchpresenter.viewmodel

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

/**
 * Renaming a translation while it is loaded.
 *
 * A rename changes neither the folder nor the selection, so it takes neither of the reload paths —
 * and the verse on screen carries a *copy* of the name and abbreviation it was built with. Both
 * halves are what made a cleared abbreviation stay on the presentation screen: the module in memory
 * kept its rename, and nothing re-emitted the selection.
 */
class BibleViewModelRenameTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-rename-vm-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun modules(): BibleSettings {
        listOf("kjv.spb" to "King James Version", "rst.spb" to "Synodal").forEach { (file, title) ->
            SpbFixture.spbFile(
                dir, name = file,
                content = SpbFixture.buildContent(
                    title = title,
                    books = listOf(SpbFixture.Book(43, "John", 1)),
                    verses = listOf(SpbFixture.Verse(43, 1, 1, "In the beginning was the Word.")),
                ),
            )
        }
        return BibleSettings(storageDirectory = dir.absolutePath)
    }

    private fun stack(vararg translations: BibleTranslationSettings) =
        modules().withTranslations(translations.toList())

    private fun viewModel(settings: BibleSettings) = BibleViewModel(
        AppSettings(bibleSettings = settings),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun named(name: String = "", abbreviation: String = "") = stack(
        BibleTranslationSettings(fileName = "kjv.spb", customName = name, customAbbreviation = abbreviation),
        BibleTranslationSettings(fileName = "rst.spb"),
    )

    private fun BibleViewModel.primaryNames(): Pair<String, String> =
        primaryBible.value!!.let { it.getBibleTitle() to it.getBibleAbbreviation() }

    // ── At load ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a module is loaded under the name it was renamed to`() {
        val model = viewModel(named("Authorised Version", "AV"))

        assertEquals("Authorised Version" to "AV", model.primaryNames())
    }

    @Test
    fun `a translation with no rename is loaded as itself`() {
        val model = viewModel(named("Authorised Version", "AV"))

        val second = model.loadedBibles.value[1]
        assertEquals("Synodal", second.getBibleTitle())
    }

    @Test
    fun `a rename reaches the verse the presenter is handed`() {
        val model = viewModel(named("Authorised Version", "AV"))

        val verse = model.getSelectedVerses().first()
        assertEquals("Authorised Version", verse.bibleName)
        assertEquals("AV", verse.bibleAbbreviation)
    }

    // ── While loaded ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `renaming applies to the module already in memory`() {
        val model = viewModel(named())

        // Deleted first: a rename must not depend on the files, or every keystroke re-reads a
        // folder of modules for a label.
        dir.listFiles().orEmpty().forEach { assertTrue(it.delete(), "could not delete ${it.name}") }
        model.updateSettings(AppSettings(bibleSettings = named("Authorised Version", "AV")))

        assertEquals("Authorised Version" to "AV", model.primaryNames())
    }

    @Test
    fun `renaming re-emits the selection so the screen follows`() {
        val model = viewModel(named())
        val tokenBefore = model.verseSelectionToken.value

        model.updateSettings(AppSettings(bibleSettings = named("Authorised Version", "AV")))

        assertTrue(
            model.verseSelectionToken.value != tokenBefore,
            "the verse on screen carries a copy of the name, so it has to be pushed again",
        )
        assertEquals("AV", model.getSelectedVerses().first().bibleAbbreviation)
    }

    @Test
    fun `clearing the abbreviation puts the module's own back on screen`() {
        val model = viewModel(named("Authorised Version", "AV"))

        model.updateSettings(AppSettings(bibleSettings = named("Authorised Version", "")))

        assertEquals("KJV", model.getSelectedVerses().first().bibleAbbreviation)
        assertEquals("Authorised Version", model.getSelectedVerses().first().bibleName)
    }

    @Test
    fun `clearing the name puts the module's own back on screen`() {
        val model = viewModel(named("Authorised Version", "AV"))

        model.updateSettings(AppSettings(bibleSettings = named("", "AV")))

        assertEquals("King James Version", model.getSelectedVerses().first().bibleName)
        assertEquals("AV", model.getSelectedVerses().first().bibleAbbreviation)
    }

    @Test
    fun `a settings change that renames nothing does not re-emit the selection`() {
        val model = viewModel(named("Authorised Version", "AV"))
        val tokenBefore = model.verseSelectionToken.value

        model.updateSettings(AppSettings(bibleSettings = named("Authorised Version", "AV").copy(marginTop = 12)))

        assertEquals(tokenBefore, model.verseSelectionToken.value)
    }

    @Test
    fun `renaming the second translation leaves the first alone`() {
        val model = viewModel(named())

        model.updateSettings(
            AppSettings(
                bibleSettings = stack(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb", customName = "Pew Bible", customAbbreviation = "PB"),
                ),
            ),
        )

        assertEquals("King James Version" to "KJV", model.primaryNames())
        assertEquals("Pew Bible", model.loadedBibles.value[1].getBibleTitle())
        assertEquals("PB", model.loadedBibles.value[1].getBibleAbbreviation())
    }
}
