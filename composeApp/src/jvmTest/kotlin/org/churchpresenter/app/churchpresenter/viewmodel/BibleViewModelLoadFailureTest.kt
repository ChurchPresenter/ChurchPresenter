package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
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
 * It does not happen, and this pins why. `loadFromSpb` swallows every exception, so a corrupt
 * module yields an empty `Bible` rather than a null one — `loadBibles` therefore takes its normal
 * path and rewrites the verse list from that empty module, clearing it. The `else if
 * (booksOnlyBible == null)` fallthrough beside it, which would leave the old verses in place, is
 * unreachable for the same reason.
 *
 * That makes these tests a guard on a load path whose safety is currently accidental: it rests on
 * a `catch (_: Exception) {}` several layers away, and anyone who makes `loadFromSpb` propagate —
 * a reasonable thing to want — turns that fallthrough live and reintroduces the stale-text state.
 * These fail if that happens.
 *
 * A module with invalid UTF-8 is the reachable version of "will not parse": both readers open the
 * file with a reporting UTF-8 decoder, and on a file this small the whole thing is decoded on the
 * first buffer fill, so even the header scan comes back with nothing.
 */
class BibleViewModelLoadFailureTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-load-failure").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
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
    }
}
