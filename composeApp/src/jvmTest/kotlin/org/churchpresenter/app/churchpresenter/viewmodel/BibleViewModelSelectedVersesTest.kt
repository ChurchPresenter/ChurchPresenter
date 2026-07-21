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
 * The degraded shapes of [BibleViewModel.getSelectedVerses] — a partially translated secondary
 * Bible, an empty one, a missing verse.
 *
 * These matter because the secondary translation is optional *per verse*, not just per install:
 * real modules skip verses, and a bilingual church running a partial translation must still get
 * the primary language on screen rather than a blank slide or a mismatched pairing.
 */
class BibleViewModelSelectedVersesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-selected-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
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

    /** Primary always holds John 3:1-4; [secondaryVerses] decides what the second language has. */
    private fun viewModel(
        secondaryVerses: List<Int>? = null,
        secondaryHeaderOnly: Boolean = false,
    ): BibleViewModel {
        SpbFixture.spbFile(
            dir, name = "p.spb",
            content = SpbFixture.buildContent(
                title = "Primary",
                books = listOf(SpbFixture.Book(43, "John", 3)),
                verses = (1..4).map { SpbFixture.Verse(43, 3, it, "English $it") },
            ),
        )
        if (secondaryVerses != null || secondaryHeaderOnly) {
            SpbFixture.spbFile(
                dir, name = "s.spb",
                content = SpbFixture.buildContent(
                    title = "Secondary",
                    books = listOf(SpbFixture.Book(43, "Иоанна", 3)),
                    verses = if (secondaryHeaderOnly) emptyList()
                    else secondaryVerses!!.map { SpbFixture.Verse(43, 3, it, "Русский $it") },
                ),
            )
        }
        val model = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "p.spb",
                    secondaryBible = if (secondaryVerses != null || secondaryHeaderOnly) "s.spb" else "",
                ),
            ),
        )
        awaitUntil("load") { model.books.value.isNotEmpty() && model.isFullyLoaded }
        val token = model.verseSelectionToken.value
        model.loadChapter(0, 3)
        awaitUntil("chapter") { model.verseSelectionToken.value > token }
        return model
    }

    // ── Partial secondary translation ───────────────────────────────────────────

    @Test
    fun `a verse the secondary lacks still shows the primary alone`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2)) // 3 and 4 untranslated
        vm.selectVerse(2) // verse 3

        val verses = vm.getSelectedVerses()
        assertEquals(1, verses.size, "the untranslated verse must not blank the slide")
        assertEquals("English 3", verses.single().verseText)
    }

    @Test
    fun `a verse the secondary has shows both languages`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2))
        vm.selectVerse(0)
        assertEquals(2, vm.getSelectedVerses().size)
    }

    @Test
    fun `a multi-selection spanning translated and untranslated verses keeps both entries`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2))
        vm.selectVerse(0)
        vm.shiftClickVerse(3) // verses 1-4, only 1-2 translated

        val verses = vm.getSelectedVerses()
        assertEquals(2, verses.size)
        assertEquals("English 1 English 2 English 3 English 4", verses[0].verseText)
        assertEquals(
            "Русский 1 Русский 2",
            verses[1].verseText,
            "the secondary shows what it has rather than dropping out entirely",
        )
        assertEquals("1-4", verses[0].verseRange, "the range describes the primary selection")
        assertEquals("1-4", verses[1].verseRange)
    }

    @Test
    fun `a multi-selection with nothing translated yields only the primary`() {
        val vm = viewModel(secondaryVerses = listOf(1))
        vm.selectVerse(1)
        vm.shiftClickVerse(3) // verses 2-4, none translated

        val verses = vm.getSelectedVerses()
        assertEquals(1, verses.size)
        assertEquals("English 2 English 3 English 4", verses.single().verseText)
    }

    // ── Empty or absent secondary ───────────────────────────────────────────────

    @Test
    fun `a secondary module with no verses is treated as absent`() {
        // A header-only or failed-to-parse module must not add an empty second slide.
        val vm = viewModel(secondaryHeaderOnly = true)
        vm.selectVerse(0)
        assertEquals(1, vm.getSelectedVerses().size)
    }

    @Test
    fun `an empty secondary is also ignored for a multi-selection`() {
        val vm = viewModel(secondaryHeaderOnly = true)
        vm.selectVerse(0)
        vm.shiftClickVerse(2)
        assertEquals(1, vm.getSelectedVerses().size)
    }

    @Test
    fun `no secondary configured yields a single entry`() {
        val vm = viewModel()
        vm.selectVerse(0)
        assertEquals(1, vm.getSelectedVerses().size)
    }

    // ── Look-ahead under the same conditions ────────────────────────────────────

    @Test
    fun `the look-ahead drops the secondary when the next verse is untranslated`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2))
        vm.selectVerse(1) // showing verse 2; verse 3 is untranslated

        val next = vm.getNextVerses()
        assertEquals(1, next.size)
        assertEquals("English 3", next.single().verseText)
    }

    @Test
    fun `the look-ahead shows both languages when the next verse is translated`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2))
        vm.selectVerse(0)

        val next = vm.getNextVerses()
        assertEquals(2, next.size)
        assertEquals("English 2", next[0].verseText)
        assertEquals("Русский 2", next[1].verseText)
    }

    @Test
    fun `the look-ahead is empty at the very end of the bible`() {
        // Last verse of the last chapter of the only book — there is nothing after it.
        val vm = viewModel()
        vm.selectVerse(3)
        assertTrue(vm.getNextVerses().isEmpty(), "the platform panel should simply be blank")
    }

    // ── Metadata carried onto the slide ─────────────────────────────────────────

    @Test
    fun `each entry carries its own translation's abbreviation and title`() {
        val vm = viewModel(secondaryVerses = listOf(1))
        vm.selectVerse(0)
        val verses = vm.getSelectedVerses()

        assertEquals("Primary", verses[0].bibleName)
        assertEquals("Secondary", verses[1].bibleName)
        assertTrue(verses[0].bibleAbbreviation.isNotEmpty(), "the slide shows an abbreviation")
        assertTrue(verses[1].bibleAbbreviation.isNotEmpty())
    }

    @Test
    fun `the secondary entry reports its own display numbering`() {
        val vm = viewModel(secondaryVerses = listOf(1, 2, 3, 4))
        vm.selectVerse(2)
        val secondary = vm.getSelectedVerses()[1]
        assertEquals(3, secondary.verseNumber)
        assertEquals(3, secondary.chapter)
        assertEquals("Иоанна", secondary.bookName)
    }

    @Test
    fun `a single selection reports no range`() {
        val vm = viewModel(secondaryVerses = listOf(1))
        vm.selectVerse(0)
        assertTrue(vm.getSelectedVerses().all { it.verseRange.isEmpty() })
    }
}
