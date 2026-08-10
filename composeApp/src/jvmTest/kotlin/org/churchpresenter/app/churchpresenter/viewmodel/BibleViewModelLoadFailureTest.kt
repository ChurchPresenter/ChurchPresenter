package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.CrashReportSweep
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Switching to a module that will not parse.
 *
 * The thing that must not happen is the *previous* translation's verse text being left standing:
 * the book column and the cross-reference column would describe the new module while the verse
 * list quietly described the old one, and nothing on screen would say so.
 *
 * It does not happen, and this pins why. `loadFromSpb` catches every exception rather than
 * propagating it, so a corrupt module yields an empty `Bible` rather than a null one — `loadBibles`
 * therefore takes its normal path and rewrites the verse list from that empty module, clearing it.
 * The `else if (booksOnlyBible == null)` fallthrough beside it, which would leave the old verses in
 * place, is unreachable for the same reason.
 *
 * That is deliberate rather than accidental now: the failure travels back as `Bible.loadError`
 * instead of as an exception, and `loadErrors` below is what the tab shows. Anyone who makes
 * `loadFromSpb` propagate instead — a reasonable-sounding thing to want — turns that fallthrough
 * live and reintroduces the stale-text state. These fail if that happens.
 *
 * A module with invalid UTF-8 is the reachable version of "will not parse": both readers open the
 * file with a reporting UTF-8 decoder, and on a file this small the whole thing is decoded on the
 * first buffer fill, so even the header scan comes back with nothing.
 */
class BibleViewModelLoadFailureTest {

    private lateinit var dir: File

    /** A failed load reports itself; these tests must not leave the report behind. */
    private val sweep = CrashReportSweep()

    @BeforeTest
    fun createDir() {
        sweep.mark()
        dir = Files.createTempDirectory("cp-bible-load-failure").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
        sweep.sweep()
    }

    private val goodModule = SpbFixture.buildContent(
        title = "Good Bible",
        books = listOf(SpbFixture.Book(1, "Genesis", 1)),
        verses = listOf(
            SpbFixture.Verse(1, 1, 1, "In the beginning God created the heaven and the earth."),
        ),
    )

    /** Readable header lines followed by a byte sequence that is not valid UTF-8. */
    private fun writeUnparsableModule(name: String) {
        val header = buildString {
            appendLine("##Title: Broken Bible")
            appendLine("1 Genesis 1")
            appendLine("-----")
        }
        File(dir, name).writeBytes(
            header.toByteArray(Charsets.UTF_8) + byteArrayOf(0xC3.toByte(), 0x28) + "\n".toByteArray(),
        )
    }

    private fun settingsFor(vararg fileNames: String) = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = dir.absolutePath,
            primaryBible = fileNames.first(),
            translations = fileNames.map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun viewModel(settings: AppSettings) = BibleViewModel(
        settings,
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `switching to a module that will not parse clears the verses it replaced`() {
        SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)
        writeUnparsableModule("broken.spb")

        val vm = viewModel(settingsFor("good.spb"))
        assertEquals(
            listOf("1. In the beginning God created the heaven and the earth."),
            vm.verses.value,
            "the good module loaded",
        )

        vm.updateSettings(settingsFor("broken.spb", "good.spb"))

        assertEquals(
            emptyList(),
            vm.verses.value,
            "the previous translation's text must not stand in for a module that failed to load",
        )
    }

    @Test
    fun `a module that will not parse offers no books either`() {
        writeUnparsableModule("broken.spb")

        val vm = viewModel(settingsFor("broken.spb"))

        // Not even the header survives: the file is small enough that the decoder reads all of it
        // on the first fill and throws before the scan sees its first line.
        assertEquals(emptyList(), vm.books.value)
        assertEquals(emptyList(), vm.verses.value)
    }

    @Test
    fun `a good module still loads normally`() {
        SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)

        val vm = viewModel(settingsFor("good.spb"))

        assertTrue(vm.verses.value.isNotEmpty())
        assertEquals(listOf("Genesis"), vm.books.value)
        assertTrue(vm.loadErrors.value.isEmpty())
    }

    /**
     * The empty book list above is indistinguishable from a Bible folder that was never set up, so
     * the operator has to be told which file failed. That is the whole point of the change: it is
     * the difference between "there is nothing here" and "this one file is broken".
     */
    @Test
    fun `a module that will not parse is named as the reason the tab is empty`() {
        writeUnparsableModule("broken.spb")

        val vm = viewModel(settingsFor("broken.spb"))

        val error = vm.loadErrors.value.single()
        assertEquals("broken.spb", error.fileName)
        assertTrue(error.reason.isNotBlank())
    }

    /**
     * A configured translation whose file is gone never reaches a load at all — it is filtered out
     * before that — so it is the one failure that has to be noticed by the ViewModel itself.
     */
    @Test
    fun `a configured translation whose file is gone is reported too`() {
        SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)

        val vm = viewModel(settingsFor("good.spb", "deleted.spb"))

        val error = vm.loadErrors.value.single()
        assertEquals("deleted.spb", error.fileName)
        assertEquals(BibleViewModel.MODULE_FILE_MISSING, error.reason)
        assertTrue(vm.verses.value.isNotEmpty(), "the translation that is fine still loads")
    }

    @Test
    fun `a reload that succeeds drops the error from the one before it`() {
        SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)
        writeUnparsableModule("broken.spb")

        val vm = viewModel(settingsFor("broken.spb", "good.spb"))
        assertTrue(vm.loadErrors.value.isNotEmpty())

        vm.updateSettings(settingsFor("good.spb"))

        assertEquals(emptyList(), vm.loadErrors.value)
    }
}
