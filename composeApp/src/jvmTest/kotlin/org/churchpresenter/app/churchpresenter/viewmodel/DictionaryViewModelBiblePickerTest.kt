package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Choosing which Bible the dictionary tab shows its interlinear verses in. The dropdown is built by
 * scanning a folder of `.spb` modules for their `##Title:` line — the file name is a fallback, not
 * the label, because real modules are named things like `rst_strongs.spb`.
 *
 * Real `.spb` files are written to a temp folder via [SpbFixture] rather than mocked, so the title
 * extraction and the module load are both exercised for real.
 */
class DictionaryViewModelBiblePickerTest {

    private lateinit var dir: File
    private val created = mutableListOf<DictionaryViewModel>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-dictionary-bibles").toFile()
        mockkConstructor(InterlinearRepository::class)
        coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
        coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(InterlinearRepository::class)
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun vm(): DictionaryViewModel = DictionaryViewModel().also { created.add(it) }

    private fun writeBible(name: String, title: String = "Test Bible"): File =
        SpbFixture.spbFile(dir, name, SpbFixture.sampleContent(title))

    // ── Listing the available modules ───────────────────────────────────────────

    @Test
    fun `nothing is offered before a folder is configured`() {
        val d = vm()
        assertTrue(d.availableDictBibles.isEmpty())
        assertNull(d.dictBible)
        assertEquals("", d.dictBibleFile)
    }

    @Test
    fun `an unset folder clears the list`() {
        writeBible("kjv.spb", "King James Version")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }

        d.loadAvailableBibles("")
        assertTrue(d.availableDictBibles.isEmpty(), "clearing the setting must empty the dropdown")
    }

    @Test
    fun `a folder that does not exist yields an empty list rather than an error`() {
        writeBible("kjv.spb", "King James Version")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the first scan") { d.availableDictBibles.isNotEmpty() }

        d.loadAvailableBibles(File(dir, "gone").absolutePath)
        awaitUntil("the missing folder to empty the list") { d.availableDictBibles.isEmpty() }
    }

    @Test
    fun `a file path instead of a folder yields an empty list`() {
        val file = writeBible("kjv.spb", "King James Version")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the first scan") { d.availableDictBibles.isNotEmpty() }

        d.loadAvailableBibles(file.absolutePath)
        awaitUntil("the bad path to empty the list") { d.availableDictBibles.isEmpty() }
    }

    @Test
    fun `each module is listed by its title, not its file name`() {
        writeBible("kjv_strongs.spb", "King James Version")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }

        val (path, title) = d.availableDictBibles.single()
        assertEquals("King James Version", title, "a dropdown of file names is unreadable")
        assertTrue(path.endsWith("kjv_strongs.spb"), "the value behind the label is still the file to load")
    }

    @Test
    fun `a module with no title falls back to its file name`() {
        File(dir, "untitled.spb").writeText("##Copyright: public domain\n-----\n")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }
        assertEquals("untitled", d.availableDictBibles.single().second)
    }

    @Test
    fun `a title buried past the header block is not searched for`() {
        // Only the first 10 lines are read — a full scan of every module in the folder would stall
        // the dropdown on a directory of multi-megabyte Bibles.
        val body = buildString {
            repeat(12) { appendLine("filler line $it") }
            appendLine("##Title: Buried Title")
        }
        File(dir, "deep.spb").writeText(body)
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }
        assertEquals("deep", d.availableDictBibles.single().second)
    }

    @Test
    fun `modules are listed in file-name order`() {
        writeBible("c.spb", "Third")
        writeBible("a.spb", "First")
        writeBible("b.spb", "Second")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.size == 3 }
        assertEquals(listOf("First", "Second", "Third"), d.availableDictBibles.map { it.second })
    }

    @Test
    fun `non-module files in the folder are ignored`() {
        writeBible("kjv.spb", "King James Version")
        File(dir, "notes.txt").writeText("not a bible")
        File(dir, "songs.sps").writeText("not a bible either")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }
        assertEquals(listOf("King James Version"), d.availableDictBibles.map { it.second })
    }

    @Test
    fun `the extension is matched case-insensitively`() {
        writeBible("shouty.SPB", "Shouty Bible")
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the folder scan") { d.availableDictBibles.isNotEmpty() }
        assertEquals(listOf("Shouty Bible"), d.availableDictBibles.map { it.second })
    }

    @Test
    fun `an empty folder yields an empty list`() {
        val d = vm()
        d.loadAvailableBibles(dir.absolutePath)
        awaitUntil("the scan of an empty folder to settle") { d.availableDictBibles.isEmpty() }
    }

    // ── Choosing a module ───────────────────────────────────────────────────────

    @Test
    fun `choosing a module loads it`() {
        val file = writeBible("kjv.spb", "King James Version")
        val d = vm()

        d.setDictBible(file.absolutePath)

        awaitUntil("the bible to load") { d.dictBible != null && !d.isDictBibleLoading }
        assertEquals(file.absolutePath, d.dictBibleFile)
        assertEquals("King James Version", d.dictBible?.getBibleTitle(), "the module must actually be parsed, not just referenced")
        assertEquals(3, d.dictBible?.getBookCount())
    }

    @Test
    fun `choosing none unloads the current module`() {
        val file = writeBible("kjv.spb")
        val d = vm()
        d.setDictBible(file.absolutePath)
        awaitUntil("the bible to load") { d.dictBible != null }

        d.setDictBible("")

        assertEquals("", d.dictBibleFile)
        assertNull(d.dictBible, "the verses of an unselected Bible must not linger")
    }

    @Test
    fun `re-choosing the module already loaded does not reload it`() {
        val file = writeBible("kjv.spb")
        val d = vm()
        d.setDictBible(file.absolutePath)
        awaitUntil("the bible to load") { d.dictBible != null }
        val loaded = d.dictBible

        d.setDictBible(file.absolutePath)

        assertTrue(d.dictBible === loaded, "re-picking the current entry must not re-parse the whole module")
        assertTrue(!d.isDictBibleLoading)
    }

    /**
     * A module that cannot be read leaves an *empty* Bible behind, not `null`: `Bible.loadFromSpb`
     * swallows its own exceptions (`catch (_: Exception) {}`), so nothing ever reaches
     * `setDictBible`'s catch branch and `dictBible` is assigned the half-built instance.
     *
     * Harmless as it stands — an empty Bible renders as no verses, which is what a missing file
     * should look like — but it means "did the module load?" cannot be answered by a null check.
     * Asserted as-is so a future change to either error path is a deliberate one.
     */
    @Test
    fun `a module that will not parse leaves an empty Bible rather than a half-parsed one`() {
        val d = vm()
        d.setDictBible(File(dir, "missing.spb").absolutePath)
        awaitUntil("the failed load to settle") { !d.isDictBibleLoading }

        assertEquals(0, d.dictBible?.getBookCount(), "a failed load must not leave stray books behind")
        assertEquals(0, d.dictBible?.getVerseCount())
    }

    @Test
    fun `a truncated module loads as far as it parsed without taking the tab down`() {
        File(dir, "truncated.spb").writeText("##Title: Broken\n1 Genesis 2\n-----\nB001C001V0")
        val d = vm()
        d.setDictBible(File(dir, "truncated.spb").absolutePath)
        awaitUntil("the load to settle") { !d.isDictBibleLoading }
        assertEquals(0, d.dictBible?.getVerseCount(), "a half-written verse line must not become a verse")
    }

    @Test
    fun `switching modules replaces the loaded one`() {
        val first = writeBible("first.spb", "First Bible")
        val second = writeBible(
            "second.spb",
            "Second Bible"
        )
        val d = vm()
        d.setDictBible(first.absolutePath)
        awaitUntil("the first bible to load") { d.dictBible != null }
        val firstLoaded = d.dictBible

        d.setDictBible(second.absolutePath)
        awaitUntil("the second bible to load") { d.dictBible !== firstLoaded && d.dictBible != null }

        assertEquals(second.absolutePath, d.dictBibleFile)
    }
}
