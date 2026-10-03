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

class BibleViewModelSettingsChangeTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    private fun settings(name: String = "") = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = dir.absolutePath,
            primaryBible = "test.spb",
            translations = listOf(BibleTranslationSettings(fileName = "test.spb", customName = name)),
        ),
    )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-settings-change").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = BibleViewModel(settings(), dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.loadedTranslations.value.isNotEmpty())) {
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
    fun `a rename reaches a primary that is not one of the loaded translations`() {
        val standIn = SpbFixture.loadedBible(Files.createTempDirectory(dir.toPath(), "stand-in").toFile())
        vm._primaryBible.value = standIn

        vm.updateSettings(settings(name = "Our Bible"))

        assertEquals("Our Bible", standIn.getBibleTitle())
    }

    @Test
    fun `an order that no longer matches what is loaded reloads the translations`() {
        val loaded = vm.loadedTranslations.value
        vm._loadedTranslations.value = loaded + BibleViewModel.LoadedTranslation("stray.spb", loaded.first().bible)

        vm.updateSettings(settings())

        val deadline = System.currentTimeMillis() + 5_000
        while (vm.loadedTranslations.value.size != 1) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
        assertEquals(listOf("test.spb"), vm.loadedTranslations.value.map { it.fileName })
    }
}
