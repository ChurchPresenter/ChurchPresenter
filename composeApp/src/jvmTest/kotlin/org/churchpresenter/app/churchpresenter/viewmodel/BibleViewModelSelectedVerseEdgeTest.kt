package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [BibleViewModel.getSelectedVerses] when a verse is blank in the primary translation.
 *
 * The primary text is taken from the verse list the operator sees, and a real module can carry a
 * verse marker with no text (a translation that omits a verse). The builder must then skip the
 * empty primary entry rather than push a blank line to the screen — while still showing the
 * secondary translation if it has that verse.
 */
class BibleViewModelSelectedVerseEdgeTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBibles() {
        dir = Files.createTempDirectory("cp-bible-edge-test").toFile()
        // Primary John 3: verse 2 is present but blank.
        SpbFixture.spbFile(dir, name = "p.spb", content = SpbFixture.buildContent(
            title = "Primary",
            books = listOf(SpbFixture.Book(43, "John", 3)),
            verses = listOf(
                SpbFixture.Verse(43, 3, 1, "English 1"),
                SpbFixture.Verse(43, 3, 2, ""), // blank in the primary
                SpbFixture.Verse(43, 3, 3, ""), // also blank, for an all-blank multi-selection
                SpbFixture.Verse(43, 3, 4, "English 4"),
            ),
        ))
        // Secondary has all four verses.
        SpbFixture.spbFile(dir, name = "s.spb", content = SpbFixture.buildContent(
            title = "Secondary",
            books = listOf(SpbFixture.Book(43, "Иоанна", 3)),
            verses = (1..4).map { SpbFixture.Verse(43, 3, it, "Русский $it") },
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath, primaryBible = "p.spb", secondaryBible = "s.spb",
            )),
        )
        awaitUntil("load") { vm.books.value.isNotEmpty() && vm.isFullyLoaded }
        val token = vm.verseSelectionToken.value
        vm.loadChapter(0, 3) // John 3
        awaitUntil("chapter") { vm.verseSelectionToken.value > token }
    }

    @AfterTest
    fun cleanUp() {
        runCatching { vm.dispose() }
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

    /** The index in the loaded verse list whose number is [verseNumber], or -1. */
    private fun indexOfVerse(verseNumber: Int): Int =
        vm.verses.value.indexOfFirst { it.substringBefore(". ").trim() == verseNumber.toString() }

    @Test
    fun `the blank primary verse is kept in the list so it can be selected`() {
        // Documents the precondition the next test relies on: an empty-text verse survives loading.
        assertTrue(indexOfVerse(2) >= 0, "a verse with no text is still a selectable row")
    }

    @Test
    fun `a verse blank in the primary shows only the secondary translation`() {
        val blankIndex = indexOfVerse(2)
        vm.selectVerse(blankIndex)

        val selected = vm.getSelectedVerses()

        // The primary entry is skipped (empty text); only the secondary is presented.
        assertEquals(1, selected.size, "a blank primary line must not be pushed to the screen")
        assertEquals("Русский 2", selected.single().verseText)
    }

    @Test
    fun `a normal verse still shows both translations`() {
        // The neighbouring non-blank verse confirms the skip is specific to the empty one.
        vm.selectVerse(indexOfVerse(1))

        val selected = vm.getSelectedVerses()

        assertEquals(2, selected.size)
        assertEquals(listOf("English 1", "Русский 1"), selected.map { it.verseText })
    }

    @Test
    fun `a multi-selection joins only the non-blank primary verses`() {
        // Verses 1-3 selected, but 2 and 3 are blank in the primary: the joined primary text is
        // just verse 1, while the secondary joins all three it has.
        vm.selectVerse(indexOfVerse(1))
        vm.shiftClickVerse(indexOfVerse(3))

        val selected = vm.getSelectedVerses()

        assertEquals("English 1", selected[0].verseText, "blank primary verses drop out of the joined text")
        assertEquals("Русский 1 Русский 2 Русский 3", selected[1].verseText)
    }

    @Test
    fun `a multi-selection of only blank primary verses yields no primary entry`() {
        // Verses 2-3 are both blank in the primary, so there is nothing to join for it; the
        // secondary still carries its own text.
        vm.selectVerse(indexOfVerse(2))
        vm.shiftClickVerse(indexOfVerse(3))

        val selected = vm.getSelectedVerses()

        assertEquals(1, selected.size, "an all-blank primary selection contributes no primary line")
        assertEquals("Русский 2 Русский 3", selected.single().verseText)
    }
}
