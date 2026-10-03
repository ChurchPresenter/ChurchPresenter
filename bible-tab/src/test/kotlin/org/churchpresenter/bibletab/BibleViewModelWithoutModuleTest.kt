package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleViewModelWithoutModuleTest {

    private fun model(lines: List<String> = listOf("1. first", "2. second", "3. third")) =
        BibleViewModel(AppSettings(), dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
            .also { it._verses.value = lines }

    @Test
    fun `a single verse with no module behind it carries its number and no names`() {
        val vm = model()
        vm._selectedVerseIndex.value = 1

        val verse = vm.getSelectedVerses().single()

        assertEquals(2, verse.verseNumber)
        assertEquals("second", verse.verseText)
        assertEquals("", verse.bookName)
        assertEquals("", verse.bibleName)
        assertEquals("", verse.bibleAbbreviation)
        assertEquals(1, verse.bookId, "the book index plus one stands in for the id")
    }

    @Test
    fun `a selection past the end is pulled back onto the last verse`() {
        val vm = model()
        vm._selectedVerseIndex.value = 9

        assertEquals(3, vm.getSelectedVerses().single().verseNumber)
        assertEquals(2, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a line with no number reads as verse one`() {
        val vm = model(listOf("no number here"))

        assertEquals(1, vm.getSelectedVerses().single().verseNumber)
    }

    @Test
    fun `a passage with no module joins its verses and skips indices that are not there`() {
        val vm = model()
        vm._multiVerseEnabled.value = true
        vm._selectedVerseIndices.addAll(listOf(2, 7, 0))

        val passage = vm.getSelectedVerses().single()

        assertEquals("first third", passage.verseText)
        assertEquals(1, passage.verseNumber)
        assertEquals("", passage.bookName)
        assertEquals("", passage.bibleName)
    }

    @Test
    fun `with no module nothing follows the last verse of a chapter`() {
        val vm = model()
        vm._selectedVerseIndex.value = 2

        assertTrue(vm.getNextVerses().isEmpty())
    }

    @Test
    fun `a passage looks ahead from its last verse`() {
        val vm = model()
        vm._multiVerseEnabled.value = true
        vm._selectedVerseIndices.addAll(listOf(0, 1))

        assertEquals(3, vm.getNextVerses().single().verseNumber)
    }

    @Test
    fun `a following line with no number offers nothing`() {
        val vm = model(listOf("1. first", "unnumbered"))

        assertTrue(vm.getNextVerses().isEmpty())
    }

    @Test
    fun `the next chapter cannot be reached without a module`() {
        val vm = model()
        vm._selectedVerseIndex.value = 2

        assertFalse(vm.navigateNextChapter())
    }

    @Test
    fun `a reference with no module goes nowhere`() {
        val vm = model()

        vm.navigateToReference(SmartReference(bookIndex = 0, chapter = 1, verseStart = 1, verseEnd = null))

        assertEquals(listOf("1. first", "2. second", "3. third"), vm.verses.value)
    }

    @Test
    fun `a search result is ignored when no module or an empty one is loaded`() {
        val vm = model()
        vm._books.value = listOf("John")

        vm.selectSearchResult(BibleSearch(book = "John", chapter = "3", verse = "16"))
        assertEquals(1, vm.selectedChapter.value)

        vm._primaryBible.value = Bible()
        vm.selectSearchResult(BibleSearch(book = "John", chapter = "3", verse = "16"))
        assertEquals(1, vm.selectedChapter.value)

        vm.navigateToReference(SmartReference(bookIndex = 0, chapter = 5, verseStart = null, verseEnd = null))
        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `a search result for a book not listed is ignored`() {
        val vm = model()

        vm.selectSearchResult(BibleSearch(book = "Jude", chapter = "1", verse = "1"))

        assertEquals(1, vm.selectedChapter.value)
    }

    @Test
    fun `a canonical reference cannot be scheduled without a module`() {
        var added = false
        assertFalse(model().addCanonicalRefToSchedule(43, 3, 16) { _, _, _, _, _, _ -> added = true })
        assertFalse(added)
    }

    @Test
    fun `scheduling needs a selection inside the chapter`() {
        val vm = model()
        vm._selectedVerseIndex.value = 5
        var added = false

        assertFalse(vm.addCurrentVerseToSchedule { _, _, _, _, _, _ -> added = true })
        assertFalse(added)
    }

    @Test
    fun `scheduling a passage adds it once and clears the passage`() {
        val vm = model()
        vm._multiVerseEnabled.value = true
        vm._selectedVerseIndices.addAll(listOf(0, 1))
        val ranges = mutableListOf<String>()

        assertTrue(vm.addCurrentVerseToSchedule { _, _, _, _, range, _ -> ranges += range })

        assertEquals(listOf("1-2"), ranges)
        assertFalse(vm.multiVerseEnabled.value)
    }

    @Test
    fun `a verse resolves by name when the id is unknown or there is no module`() {
        val vm = model()
        vm._books.value = listOf("Genesis", "John")

        assertEquals(1, vm.resolveBookIndex("john", bookId = 43))
        assertEquals(-1, vm.resolveBookIndex("Jude", bookId = 0))
    }

    @Test
    fun `ctrl-click on the anchor itself starts a passage of one`() {
        val vm = model()
        vm._selectedVerseIndex.value = 1

        vm.ctrlClickVerse(1)
        assertEquals(listOf(1), vm.selectedVerseIndices)

        vm.ctrlClickVerse(9)
        assertEquals(listOf(1), vm.selectedVerseIndices, "an index outside the chapter is ignored")
    }

    @Test
    fun `ctrl-click with the anchor outside the chapter adds only the clicked verse`() {
        val vm = model()
        vm._selectedVerseIndex.value = 7

        vm.ctrlClickVerse(0)

        assertEquals(listOf(0), vm.selectedVerseIndices)
    }

    @Test
    fun `shift-click from the same verse selects just that verse`() {
        val vm = model()
        vm._selectedVerseIndex.value = 1

        vm.shiftClickVerse(1)

        assertEquals(listOf(1), vm.selectedVerseIndices)
        assertFalse(vm.multiVerseEnabled.value)
    }

    @Test
    fun `selected verse numbers skip indices with no numbered line`() {
        val vm = model(listOf("1. first", "unnumbered"))
        vm._selectedVerseIndices.addAll(listOf(0, 1, 5))

        assertEquals(listOf(1), vm.getSelectedVerseNumbers())
    }

    @Test
    fun `turning multi-verse on keeps the selection and off clears it`() {
        val vm = model()
        vm._selectedVerseIndices.addAll(listOf(0, 1))
        vm._multiVerseEnabled.value = true

        vm.toggleMultiVerse(true)
        assertEquals(listOf(0, 1), vm.selectedVerseIndices)

        vm.toggleMultiVerse(false)
        assertTrue(vm.selectedVerseIndices.isEmpty())
    }
}
