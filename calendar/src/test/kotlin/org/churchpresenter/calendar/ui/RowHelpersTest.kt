@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The picker's plain helpers -- how a song is found and turned into a row, a stored color read
 * back, a ministry row and a whole chapter opened -- and the icon every kind of row is drawn with.
 */
class RowHelpersTest {

    private val songs = listOf(
        SongItem(number = "12", title = "Holy, Holy, Holy", songbook = "Hymns"),
        SongItem(number = "12a", title = "Be Still", songbook = "Hymns"),
        SongItem(number = "3", title = "Twelve Gates", songbook = "Hymns"),
    )

    @Test
    fun `a song is found by its title or by its whole number`() {
        assertEquals(listOf("Holy, Holy, Holy"), matchSongs(songs, " 12 ").map { it.title }, "12, not 12a")
        assertEquals(listOf("Twelve Gates"), matchSongs(songs, "twelve").map { it.title })
    }

    @Test
    fun `a song numbered with letters is an unnumbered row`() {
        assertEquals(12, songs[0].toScheduleItem().songNumber)
        assertEquals(0, songs[1].toScheduleItem().songNumber)
    }

    @Test
    fun `a stored color that is not hex is grey`() {
        assertEquals(Color.Gray, parseHex("#teal"))
        assertEquals(Color(0xFF112233), parseHex("#112233"))
    }

    @Test
    fun `a ministry row with no planned length opens with no length typed`() {
        val row = ScheduleItem.MinistryItem(id = "m", title = "Prayer", detail = "Elder")
        assertEquals("", pickerFor(row, emptyList()).duration)
        assertEquals("1:30", pickerFor(row, emptyList(), plannedSeconds = 90).duration)
    }

    @Test
    fun `the whole chapter is its verse count, or one verse when the book is not known`() {
        val picker = PickerState(PickKind.BIBLE)
        picker.chapter = 2
        picker.selectWholeChapter()
        assertEquals(1..1, picker.selection, "a chapter with no book to count")

        picker.book = CalendarBibleBook(bookId = 19, name = "Psalms", verseCounts = listOf(6, 12))
        picker.selectWholeChapter()
        assertEquals(1..12, picker.selection)

        picker.chapter = null
        picker.clearVerses()
        assertNull(picker.selection)
    }

    @Test
    fun `every kind of row has its own icon`() = runComposeUiTest {
        val rows = listOf(
            ScheduleItem.SongItem("s", 1, "Song", "Hymns", "Hymns::1"),
            ScheduleItem.BibleVerseItem("b", "John", 3, 16, "For God so loved"),
            ScheduleItem.PictureItem("p", "/pics", "Pictures", 3),
            ScheduleItem.PresentationItem("d", "/deck.pptx", "Deck", 10, "pptx"),
            ScheduleItem.MediaItem("v", "/clip.mp4", "Clip", "local"),
            ScheduleItem.LowerThirdItem("l", "preset", "Name", false, 0L),
            ScheduleItem.AnnouncementItem(id = "a", text = "Welcome"),
            ScheduleItem.WebsiteItem("w", "https://church.example"),
            ScheduleItem.SceneItem("sc", "scene-1", "Scene"),
            ScheduleItem.DictionaryItem("x", "G26", "agape", "agapē", "love"),
            ScheduleItem.MinistryItem(id = "m", title = "Prayer"),
            ScheduleItem.LabelItem("h", "Worship", "#FFFFFF", "#5B9DF5"),
            ScheduleItem.CueItem(id = "c", action = CueAction.BLANK),
        )
        var looks: List<ItemLook> = emptyList()
        setContent { AppThemeWrapper(theme = ThemeMode.LIGHT) { looks = rows.map { lookFor(it) } } }
        waitForIdle()

        assertEquals(rows.size, looks.size)
        assertEquals(rows.size, looks.map { it.icon }.toSet().size, "no two kinds share an icon")
    }
}
