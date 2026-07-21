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
 * What actually reaches the output screen: [BibleViewModel.getSelectedVerses] builds the
 * `SelectedVerse` list the presenter renders — one entry per translation.
 *
 * The bilingual pairing is the interesting part. The two translations are matched through the
 * CANONICAL code reference, not the display numbering, because a Russian module numbers Psalms by
 * the LXX while an English one doesn't. The fixture below deliberately reproduces that offset:
 * the secondary Bible stores the same verses one chapter lower in display numbering while sharing
 * the same codes, so a naive display-number pairing would show the wrong verse in the second
 * language — which an operator who doesn't read that language would never catch.
 */
class BibleViewModelBilingualTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBibles() {
        dir = Files.createTempDirectory("cp-bible-bilingual-test").toFile()

        // Primary: display numbering == canonical numbering.
        SpbFixture.spbFile(
            dir, name = "primary.spb",
            content = SpbFixture.buildContent(
                title = "English Bible",
                books = listOf(SpbFixture.Book(19, "Psalms", 1)),
                verses = (1..4).map {
                    SpbFixture.Verse(book = 19, chapter = 23, verse = it, text = "English verse $it")
                },
            ),
        )

        // Secondary: LXX-style, one chapter lower in DISPLAY numbering, same canonical codes.
        SpbFixture.spbFile(
            dir, name = "secondary.spb",
            content = SpbFixture.buildContent(
                title = "Русская Библия",
                books = listOf(SpbFixture.Book(19, "Псалтирь", 1)),
                verses = (1..4).map {
                    SpbFixture.Verse(
                        book = 19, chapter = 22, verse = it, text = "Русский стих $it",
                        codeBook = 19, codeChapter = 23, codeVerse = it,
                    )
                },
            ),
        )

        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "primary.spb",
                    secondaryBible = "secondary.spb",
                ),
            ),
        )
        awaitUntil("books to load") { vm.books.value.isNotEmpty() }
        awaitUntil("verse data to load") { vm.isFullyLoaded }
        openPsalm23()
    }

    @AfterTest
    fun cleanUp() {
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

    private fun openPsalm23() {
        val token = vm.verseSelectionToken.value
        vm.loadChapter(0, 23)
        awaitUntil("Psalm 23 to load") { vm.verseSelectionToken.value > token }
    }

    // ── Single verse ────────────────────────────────────────────────────────────

    @Test
    fun `a single verse produces one entry per translation`() {
        vm.selectVerse(0)
        val verses = vm.getSelectedVerses()

        assertEquals(2, verses.size, "primary and secondary both go to the presenter")
        assertEquals("English verse 1", verses[0].verseText)
        assertEquals("Русский стих 1", verses[1].verseText)
    }

    @Test
    fun `the primary entry carries its own book name and translation title`() {
        vm.selectVerse(0)
        val primary = vm.getSelectedVerses().first()

        assertEquals("Psalms", primary.bookName)
        assertEquals("English Bible", primary.bibleName)
        assertEquals(23, primary.chapter)
        assertEquals(1, primary.verseNumber)
        assertEquals(19, primary.bookId)
    }

    @Test
    fun `the secondary entry uses its own language's book name`() {
        vm.selectVerse(0)
        val secondary = vm.getSelectedVerses()[1]

        assertEquals("Псалтирь", secondary.bookName, "the second language must not show the English name")
        assertEquals("Русская Библия", secondary.bibleName)
    }

    @Test
    fun `translations are paired by canonical code, not by display numbering`() {
        // The secondary stores this passage at display chapter 22; pairing on the display number
        // would find nothing (or the wrong psalm entirely).
        for (index in 0..3) {
            vm.selectVerse(index)
            val verses = vm.getSelectedVerses()
            assertEquals(2, verses.size, "verse ${index + 1} lost its translation")
            assertEquals("English verse ${index + 1}", verses[0].verseText)
            assertEquals("Русский стих ${index + 1}", verses[1].verseText, "wrong verse paired")
        }
    }

    @Test
    fun `the verse selection is clamped before building the output`() {
        vm.selectVerse(3)
        openPsalm23() // reload resets to verse 1
        assertEquals(1, vm.getSelectedVerses().first().verseNumber)
    }

    @Test
    fun `no verses loaded means nothing to present`() {
        val empty = BibleViewModel(AppSettings())
        assertTrue(empty.getSelectedVerses().isEmpty(), "an unconfigured Bible must not produce a blank slide")
    }

    // ── Multi-verse ─────────────────────────────────────────────────────────────

    @Test
    fun `a contiguous multi-selection joins the text and reports a range`() {
        vm.selectVerse(0)
        vm.shiftClickVerse(2)

        val verses = vm.getSelectedVerses()
        assertEquals(2, verses.size)
        assertEquals("English verse 1 English verse 2 English verse 3", verses[0].verseText)
        assertEquals("1-3", verses[0].verseRange)
        assertEquals(1, verses[0].verseNumber, "the range starts at the first selected verse")
    }

    @Test
    fun `the secondary translation is joined in the same order`() {
        vm.selectVerse(0)
        vm.shiftClickVerse(2)

        val secondary = vm.getSelectedVerses()[1]
        assertEquals("Русский стих 1 Русский стих 2 Русский стих 3", secondary.verseText)
        assertEquals("1-3", secondary.verseRange, "both languages must report the same range")
    }

    @Test
    fun `a non-contiguous multi-selection lists the verse numbers`() {
        vm.selectVerse(0)
        vm.ctrlClickVerse(2)

        val primary = vm.getSelectedVerses().first()
        assertEquals("1,3", primary.verseRange)
        assertEquals("English verse 1 English verse 3", primary.verseText)
    }

    @Test
    fun `multi-selected verses are emitted in verse order, not click order`() {
        // Ctrl-clicking bottom-up must still read top-down on screen.
        vm.selectVerse(3)
        vm.ctrlClickVerse(1)
        vm.ctrlClickVerse(0)

        val primary = vm.getSelectedVerses().first()
        assertEquals("English verse 1 English verse 2 English verse 4", primary.verseText)
        assertEquals("1,2,4", primary.verseRange)
    }

    @Test
    fun `a whole-chapter selection works`() {
        vm.selectVerse(0)
        vm.shiftClickVerse(3)

        val primary = vm.getSelectedVerses().first()
        assertEquals("1-4", primary.verseRange)
        assertTrue(primary.verseText.startsWith("English verse 1"))
        assertTrue(primary.verseText.endsWith("English verse 4"))
    }

    // ── Primary only ────────────────────────────────────────────────────────────

    @Test
    fun `with no secondary translation only one entry is produced`() {
        val soloDir = Files.createTempDirectory("cp-bible-solo-test").toFile()
        try {
            SpbFixture.spbFile(soloDir, name = "only.spb")
            val solo = BibleViewModel(
                AppSettings(
                    bibleSettings = BibleSettings(storageDirectory = soloDir.absolutePath, primaryBible = "only.spb"),
                ),
            )
            awaitUntil("solo bible to load") { solo.books.value.isNotEmpty() }
            awaitUntil("solo verse data") { solo.isFullyLoaded }

            val token = solo.verseSelectionToken.value
            solo.loadChapter(2, 3) // John 3
            awaitUntil("John 3") { solo.verseSelectionToken.value > token }
            solo.selectVerse(0)

            assertEquals(1, solo.getSelectedVerses().size, "no second translation, no second entry")
        } finally {
            soloDir.deleteRecursively()
        }
    }
}
