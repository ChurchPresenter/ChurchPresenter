package org.churchpresenter.calendar.ui

import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The picker opens on the row being edited -- see `pickerFor`. */
class PickerStateTest {

    @Test
    fun `a verse row opens on its book and chapter with its range selected`() {
        val row = ScheduleItem.BibleVerseItem("v", "Psalms", 2, 3, "", verseRange = "3-5", bookId = 19)
        val picker = pickerFor(row, BIBLE_BOOKS)
        assertEquals(PickKind.BIBLE, picker.kind)
        assertEquals("Psalms", picker.book?.name)
        assertEquals(2, picker.chapter)
        assertEquals(3..5, picker.selection)
        assertEquals("Psalms 2:3-5", picker.pendingVerses?.displayText)
    }

    @Test
    fun `a typed verse row is found by name, and an unknown one opens on all books`() {
        val byName = pickerFor(ScheduleItem.BibleVerseItem("v", "genesis", 1, 4, "", bookId = 0), BIBLE_BOOKS)
        assertEquals("Genesis", byName.book?.name)
        assertEquals(4..4, byName.selection)

        val unknown = pickerFor(ScheduleItem.BibleVerseItem("v", "Nowhere", 1, 1, "", bookId = 0), BIBLE_BOOKS)
        assertEquals(PickKind.BIBLE, unknown.kind)
        assertNull(unknown.book)

        val badChapter = pickerFor(ScheduleItem.BibleVerseItem("v", "Genesis", 9, 1, "", bookId = 1), BIBLE_BOOKS)
        assertNull(badChapter.book, "a chapter the book does not have cannot be opened on")
    }

    @Test
    fun `a chapter below one cannot be opened on either`() {
        assertNull(pickerFor(ScheduleItem.BibleVerseItem("v", "Genesis", 0, 1, "", bookId = 1), BIBLE_BOOKS).book)
    }

    @Test
    fun `taps anchor a range, extend it, and clear it`() {
        val picker = pickerFor(ScheduleItem.BibleVerseItem("v", "Genesis", 1, 4, "", bookId = 1), BIBLE_BOOKS)
        picker.clearVerses()
        assertNull(picker.selection)
        picker.tapVerse(5)
        assertEquals(5..5, picker.selection)
        picker.tapVerse(2)
        assertEquals(2..5, picker.selection, "a second tap extends, in either direction")
        picker.tapVerse(9)
        assertEquals(5..9, picker.selection)
        picker.clearVerses()
        picker.tapVerse(3)
        picker.tapVerse(3)
        assertNull(picker.selection, "tapping the only selected verse again clears it")
    }

    @Test
    fun `the whole chapter is selectable, and nothing is when there is no chapter`() {
        val picker = pickerFor(ScheduleItem.BibleVerseItem("v", "Genesis", 2, 4, "", bookId = 1), BIBLE_BOOKS)
        picker.selectWholeChapter()
        assertEquals(1..25, picker.selection)
        picker.showAllBooks()
        picker.selectWholeChapter()
        assertEquals(1..1, picker.selection, "no book and no chapter: the one verse there always is")
        assertNull(picker.pendingVerses, "and nothing to add without a book")
    }

    @Test
    fun `a ministry row opens with all three of its lines, and offers itself once named`() {
        val row = ScheduleItem.MinistryItem("m", "Violin", "Jake")
        val picker = pickerFor(row, BIBLE_BOOKS, plannedSeconds = 210)
        assertEquals(PickKind.MINISTRY, picker.kind)
        assertEquals("Violin", picker.query)
        assertEquals("Jake", picker.detail)
        assertEquals("3:30", picker.duration)
        assertEquals(210, picker.ministrySeconds())
        assertEquals("Violin", picker.pendingMinistry?.title)

        picker.query = "  "
        assertNull(picker.pendingMinistry, "nothing to add without a name")
        picker.duration = "soon"
        assertNull(picker.ministrySeconds())
    }

    @Test
    fun `a song row opens with its title in the search, and nothing opens on songs`() {
        val song = pickerFor(song("a", "Amazing Grace"), BIBLE_BOOKS)
        assertEquals(PickKind.SONGS, song.kind)
        assertEquals("Amazing Grace", song.query)

        val fresh = pickerFor(null, BIBLE_BOOKS)
        assertEquals(PickKind.SONGS, fresh.kind)
        assertEquals("", fresh.query)

        assertEquals(PickKind.SECTION, pickerFor(heading("h"), BIBLE_BOOKS).kind)
        val clip = ScheduleItem.MediaItem("m", "/clip.mp4", "clip", "local")
        assertEquals(PickKind.PRESETS, pickerFor(clip, BIBLE_BOOKS).kind, "anything else was picked from the presets")
    }

    @Test
    fun `re-tapping the anchor of a range shrinks it to that verse`() {
        val picker = pickerFor(ScheduleItem.BibleVerseItem("v", "Genesis", 1, 4, "", bookId = 1), BIBLE_BOOKS)
        picker.clearVerses()
        picker.tapVerse(5)
        picker.tapVerse(9)
        picker.tapVerse(5)
        assertEquals(5..5, picker.selection)
    }
}
