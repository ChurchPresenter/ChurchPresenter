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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a change to the translation stack costs (issue #96).
 *
 * Rearranging the stack used to go through the same full reload as adding or swapping a translation:
 * every .spb parsed again from disk, and — because the load republishes a books-only bible partway
 * through — the verse list emptied out under whatever was live until the parse finished. None of that
 * work is needed to put the same modules in a different order.
 *
 * Both view models here run on `Dispatchers.Unconfined`, so a reload would finish inside the
 * `updateSettings` call rather than after it. That is what makes the assertions below decide
 * anything: they are read immediately afterwards, when a reload would already have happened.
 */
class BibleViewModelTranslationOrderTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-order-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    /** Three one-verse modules, each with its own wording so the stack order is readable. */
    private fun threeModules(): BibleSettings {
        listOf("p.spb" to "English", "s.spb" to "Русский", "t.spb" to "Deutsch").forEach { (file, text) ->
            SpbFixture.spbFile(
                dir, name = file,
                content = SpbFixture.buildContent(
                    title = file,
                    books = listOf(SpbFixture.Book(43, "John", 1)),
                    verses = listOf(SpbFixture.Verse(43, 1, 1, text)),
                ),
            )
        }
        return BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            listOf("p.spb", "s.spb", "t.spb").map { BibleTranslationSettings(fileName = it) },
        )
    }

    private fun viewModel(settings: BibleSettings) = BibleViewModel(
        AppSettings(bibleSettings = settings),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `reordering behind the navigation bible neither re-reads the files nor blanks the verses`() {
        val settings = threeModules()
        val model = viewModel(settings)
        assertEquals(listOf("p.spb", "s.spb", "t.spb"), model.loadedTranslations.value.map { it.fileName })
        val versesBefore = model.verses.value
        assertTrue(versesBefore.isNotEmpty(), "fixture error: the initial load produced no verses")
        val tokenBefore = model.verseSelectionToken.value

        // The modules are already parsed and in memory, so putting them in a different order must not
        // depend on the files at all. Deleting them is how that is held to.
        dir.listFiles().orEmpty().forEach { assertTrue(it.delete(), "could not delete ${it.name}") }

        model.updateSettings(AppSettings(bibleSettings = settings.moveTranslation(2, -1)))

        assertEquals(
            listOf("p.spb", "t.spb", "s.spb"),
            model.loadedTranslations.value.map { it.fileName },
            "the new order must be applied to what is already loaded",
        )
        assertEquals(
            listOf("p.spb", "t.spb", "s.spb"),
            model.loadedBibles.value.map { it.getBibleTitle() },
            "the bible list the presenter stacks from must be reordered with it",
        )
        assertEquals(versesBefore, model.verses.value, "the verse list must be left alone")
        assertTrue(model.isFullyLoaded, "nothing was reloaded, so nothing is mid-load")
        assertEquals(
            listOf("English", "Deutsch", "Русский"),
            model.getSelectedVerses().map { it.verseText },
            "the live selection must restack in the new order",
        )
        assertTrue(
            model.verseSelectionToken.value != tokenBefore,
            "the selection must be re-emitted so the screen restacks too",
        )
    }

    @Test
    fun `a stack whose order is unchanged is left completely alone`() {
        val settings = threeModules()
        val model = viewModel(settings)
        val tokenBefore = model.verseSelectionToken.value

        // Same stack, a styling change elsewhere in the same settings object.
        model.updateSettings(AppSettings(bibleSettings = settings.copy(splitBrowseMode = true)))

        assertEquals(listOf("p.spb", "s.spb", "t.spb"), model.loadedTranslations.value.map { it.fileName })
        assertEquals(
            tokenBefore, model.verseSelectionToken.value,
            "nothing moved, so the live verse must not be re-emitted",
        )
    }

    // ── The reload decision on its own ──────────────────────────────────────────────────────────

    private fun stack(vararg names: String, storage: String = "/bibles"): BibleSettings =
        BibleSettings(storageDirectory = storage)
            .withTranslations(names.map { BibleTranslationSettings(fileName = it) })

    private fun reloadRequired(previous: BibleSettings, next: BibleSettings): Boolean =
        viewModel(threeModules()).translationReloadRequired(previous, next)

    @Test
    fun `rearranging the stack behind the navigation bible needs no reload`() {
        assertFalse(reloadRequired(stack("a.spb", "b.spb", "c.spb"), stack("a.spb", "c.spb", "b.spb")))
    }

    @Test
    fun `a different navigation bible needs a reload`() {
        // The first of the stack supplies the book, chapter and verse lists, and the canonical code
        // every other translation's verse is looked up by — so this one is not a rearrangement.
        assertTrue(reloadRequired(stack("a.spb", "b.spb", "c.spb"), stack("b.spb", "a.spb", "c.spb")))
    }

    @Test
    fun `adding or removing a translation needs a reload`() {
        assertTrue(reloadRequired(stack("a.spb", "b.spb"), stack("a.spb", "b.spb", "c.spb")))
        assertTrue(reloadRequired(stack("a.spb", "b.spb", "c.spb"), stack("a.spb", "b.spb")))
        assertTrue(reloadRequired(stack("a.spb", "b.spb"), stack("a.spb", "c.spb")))
    }

    @Test
    fun `a different bible folder needs a reload`() {
        // Same file names, different files.
        assertTrue(
            reloadRequired(
                stack("a.spb", "b.spb", storage = "/bibles"),
                stack("a.spb", "b.spb", storage = "/other"),
            ),
        )
    }
}
