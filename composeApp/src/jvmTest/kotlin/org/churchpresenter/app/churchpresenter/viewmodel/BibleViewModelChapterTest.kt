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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BibleViewModel] driven by a real loaded Bible, using the synthetic `.spb` module from
 * [SpbFixture]. This is the half of the view model that could not be reached before: chapter
 * loading, verse selection, and the ctrl/shift multi-select the operator uses to put a passage on
 * screen — all of which bail out early when no verses are loaded.
 *
 * Loading is asynchronous (a header scan, then verse data on IO), so each test waits for the state
 * it needs rather than assuming it is ready.
 */
class BibleViewModelChapterTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bibleviewmodel-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")

        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "test.spb",
                ),
            ),
        )
        awaitUntil("books to load") { vm.books.value.isNotEmpty() }
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

    /**
     * Loads a chapter and waits for it, bypassing the click debounce.
     *
     * Waits on the selection token rather than on `verses.isNotEmpty()`: the previous chapter's
     * verses stay published until the new ones arrive, so a non-empty check returns immediately
     * with stale content.
     */
    private fun openChapter(bookIndex: Int, chapter: Int) {
        val tokenBefore = vm.verseSelectionToken.value
        vm.loadChapter(bookIndex, chapter)
        awaitUntil("book $bookIndex chapter $chapter to load") { vm.verseSelectionToken.value > tokenBefore }
    }

    // ── Loading ─────────────────────────────────────────────────────────────────

    @Test
    fun `the module's books are exposed in order`() {
        assertEquals(listOf("Genesis", "Psalms", "John"), vm.books.value)
    }

    @Test
    fun `opening a chapter publishes its verses`() {
        openChapter(0, 1) // Genesis 1
        assertEquals(3, vm.verses.value.size)
        assertTrue(vm.verses.value.first().contains("In the beginning"))
        assertEquals(0, vm.selectedBookIndex.value)
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `opening a chapter resets the verse selection to the first verse`() {
        openChapter(0, 1)
        vm.selectVerse(2)
        assertEquals(2, vm.selectedVerseIndex.value)

        openChapter(0, 2)
        assertEquals(0, vm.selectedVerseIndex.value, "a new chapter starts at its first verse")
    }

    @Test
    fun `a book index past the end is clamped rather than throwing`() {
        vm.loadChapter(99, 1)
        assertTrue(vm.selectedBookIndex.value <= vm.books.value.lastIndex)
    }

    @Test
    fun `chapters for the current book come from the loaded module`() {
        openChapter(0, 1) // Genesis, which has verses in chapters 1 and 2
        assertEquals(listOf("1", "2"), vm.getChaptersForCurrentBook())
    }

    // ── Single selection ────────────────────────────────────────────────────────

    @Test
    fun `selecting a verse moves the selection and bumps the token`() {
        openChapter(0, 1)
        val token = vm.verseSelectionToken.value

        vm.selectVerse(1)
        assertEquals(1, vm.selectedVerseIndex.value)
        assertTrue(vm.verseSelectionToken.value > token, "the tab watches this token to push live")
    }

    @Test
    fun `selecting an out-of-range verse falls back to the first`() {
        openChapter(0, 1)
        vm.selectVerse(2)
        vm.selectVerse(99)
        assertEquals(0, vm.selectedVerseIndex.value)
        vm.selectVerse(-1)
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a plain click clears any multi-selection`() {
        openChapter(0, 1)
        vm.ctrlClickVerse(0)
        vm.ctrlClickVerse(2)
        assertTrue(vm.multiVerseEnabled.value)

        vm.selectVerse(1)
        assertTrue(vm.selectedVerseIndices.isEmpty(), "a plain click starts over")
        assertFalse(vm.multiVerseEnabled.value)
    }

    // ── Ctrl-click multi-select ─────────────────────────────────────────────────

    @Test
    fun `the first ctrl-click also picks up the verse already selected`() {
        // Otherwise ctrl-clicking a second verse would silently drop the one on screen.
        openChapter(0, 1)
        vm.selectVerse(0)

        vm.ctrlClickVerse(2)
        assertEquals(listOf(0, 2), vm.selectedVerseIndices.sorted())
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `ctrl-clicking a selected verse removes it`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.ctrlClickVerse(1)
        vm.ctrlClickVerse(2)
        assertEquals(listOf(0, 1, 2), vm.selectedVerseIndices.sorted())

        vm.ctrlClickVerse(1)
        assertEquals(listOf(0, 2), vm.selectedVerseIndices.sorted())
    }

    @Test
    fun `a ctrl-click on the currently selected verse starts a selection containing just it`() {
        openChapter(0, 1)
        vm.selectVerse(0)

        vm.ctrlClickVerse(0)
        assertEquals(listOf(0), vm.selectedVerseIndices, "the first ctrl-click adds, it does not toggle off")
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `removing the last multi-selected verse falls back to a single selection`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.ctrlClickVerse(0) // adds it
        vm.ctrlClickVerse(0) // and now removes it again

        assertTrue(vm.selectedVerseIndices.isEmpty())
        assertFalse(vm.multiVerseEnabled.value)
        assertEquals(0, vm.selectedVerseIndex.value, "the deselected verse becomes the single selection")
    }

    @Test
    fun `ctrl-clicking out of range is ignored`() {
        openChapter(0, 1)
        vm.ctrlClickVerse(99)
        vm.ctrlClickVerse(-1)
        assertTrue(vm.selectedVerseIndices.isEmpty())
    }

    // ── Shift-click range select ────────────────────────────────────────────────

    @Test
    fun `shift-click selects the range from the anchor`() {
        openChapter(0, 1)
        vm.selectVerse(0)

        vm.shiftClickVerse(2)
        assertEquals(listOf(0, 1, 2), vm.selectedVerseIndices.sorted())
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `shift-click works backwards from the anchor`() {
        openChapter(0, 1)
        vm.selectVerse(2)

        vm.shiftClickVerse(0)
        assertEquals(listOf(0, 1, 2), vm.selectedVerseIndices.sorted(), "direction must not matter")
    }

    @Test
    fun `repeated shift-clicks shrink and grow from the same anchor`() {
        openChapter(0, 1)
        vm.selectVerse(0)

        vm.shiftClickVerse(2)
        assertEquals(3, vm.selectedVerseIndices.size)

        vm.shiftClickVerse(1)
        assertEquals(listOf(0, 1), vm.selectedVerseIndices.sorted(), "the anchor stays put")
    }

    @Test
    fun `shift-clicking the anchor itself selects just that verse`() {
        openChapter(0, 1)
        vm.selectVerse(1)
        vm.shiftClickVerse(1)
        assertEquals(listOf(1), vm.selectedVerseIndices)
        assertFalse(vm.multiVerseEnabled.value, "one verse is not a multi-selection")
    }

    @Test
    fun `shift-clicking out of range is ignored`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.shiftClickVerse(99)
        assertTrue(vm.selectedVerseIndices.isEmpty())
    }

    @Test
    fun `a multi-selection formats as the range the audience sees`() {
        openChapter(0, 1)
        vm.selectVerse(0)
        vm.shiftClickVerse(2)

        // Indices are 0-based; verse numbers are 1-based.
        val numbers = vm.selectedVerseIndices.sorted().map { it + 1 }
        assertEquals("1-3", vm.formatVerseRange(numbers))
    }

    @Test
    fun `clearing a multi-selection leaves the single selection intact`() {
        openChapter(0, 1)
        vm.selectVerse(1)
        vm.shiftClickVerse(2)

        vm.clearMultiVerseSelection()
        assertTrue(vm.selectedVerseIndices.isEmpty())
        assertFalse(vm.multiVerseEnabled.value)
    }

    // ── Click debounce ──────────────────────────────────────────────────────────

    @Test
    fun `a double-click on a book is debounced`() {
        // Guards against a fast second click loading the same chapter twice.
        openChapter(0, 1)
        vm.selectBook(1)
        val afterFirst = vm.selectedBookIndex.value

        vm.selectBook(2) // immediately after — inside the debounce window
        assertEquals(afterFirst, vm.selectedBookIndex.value, "the second rapid click should be swallowed")
    }

    @Test
    fun `a chapter click is debounced the same way`() {
        openChapter(0, 1)
        vm.selectChapter(2)
        val afterFirst = vm.selectedChapter.value

        vm.selectChapter(1)
        assertEquals(afterFirst, vm.selectedChapter.value)
    }

    // ── Multiple chapters ───────────────────────────────────────────────────────

    @Test
    fun `switching between books loads the right verses`() {
        openChapter(2, 3) // John 3
        assertEquals(2, vm.verses.value.size)
        assertTrue(vm.verses.value.any { it.contains("For God so loved") })

        openChapter(1, 23) // Psalms 23
        assertEquals(2, vm.verses.value.size)
        assertTrue(vm.verses.value.any { it.contains("my shepherd") })
    }
}
