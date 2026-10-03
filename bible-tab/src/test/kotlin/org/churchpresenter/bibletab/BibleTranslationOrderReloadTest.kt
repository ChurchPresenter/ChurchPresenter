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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BibleTranslationOrderReloadTest {

    private lateinit var dir: File
    private val created = mutableListOf<BibleViewModel>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-order").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = bibleFixture)
        SpbFixture.spbFile(dir, name = SECOND_MODULE, content = bibleFixture)
    }

    @AfterTest
    fun tearDown() {
        created.forEach { it.dispose() }
        dir.deleteRecursively()
    }

    private fun settings(vararg files: String, storage: String = dir.absolutePath) = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = storage,
            primaryBible = files.firstOrNull().orEmpty(),
            translations = files.map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun model(settings: AppSettings): BibleViewModel {
        val vm = BibleViewModel(settings, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
        created += vm
        val deadline = System.currentTimeMillis() + 5_000
        while (!vm.isFullyLoaded) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out loading")
            Thread.yield()
        }
        return vm
    }

    @Test
    fun `swapping the order swaps which module is secondary without a reload`() {
        val vm = model(settings("test.spb", SECOND_MODULE))
        val loaded = vm.loadedTranslations.value
        val tokenBefore = vm.verseSelectionToken.value

        vm.appSettings = settings(SECOND_MODULE, "test.spb")
        vm.applyTranslationOrder()

        assertEquals(listOf(SECOND_MODULE, "test.spb"), vm.loadedTranslations.value.map { it.fileName })
        assertSame(loaded.first().bible, vm.secondaryBible.value)
        assertTrue(vm.verseSelectionToken.value > tokenBefore)
    }

    @Test
    fun `the same order changes nothing`() {
        val vm = model(settings("test.spb", SECOND_MODULE))
        val loaded = vm.loadedTranslations.value
        val tokenBefore = vm.verseSelectionToken.value

        vm.applyTranslationOrder()

        assertSame(loaded, vm.loadedTranslations.value)
        assertEquals(tokenBefore, vm.verseSelectionToken.value)
    }

    @Test
    fun `with nothing loaded the order is left alone`() {
        val vm = model(settings("missing.spb"))
        assertTrue(vm.loadedTranslations.value.isEmpty())

        vm.applyTranslationOrder()

        assertTrue(vm.loadedTranslations.value.isEmpty())
    }

    @Test
    fun `with no library folder every configured module is reported missing`() {
        val vm = model(settings("test.spb", storage = ""))

        assertNull(vm.primaryBible.value)
        assertEquals(BibleViewModel.MODULE_FILE_MISSING, vm.loadErrors.value.single().reason)
    }
}
