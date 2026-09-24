@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Browsing scripture, and the presets a service is built from. */
class BibleAndPresetsTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun ComposeUiTest.openBible() {
        awaitText("Sunday Morning")
        clickFirst("Add song, verse or section")
        awaitText("Songs")
        clickFirst("Bible")
        awaitText("Genesis")
    }

    private fun host() = CalendarHost(bibleBooks = { BIBLE_BOOKS })

    private fun emptyService() = documentWith(service(items = emptyList(), planned = emptyMap()))

    @Test
    fun `a book opens on its chapters and a chapter on its verses`() =
        withCalendar(emptyService(), host = host()) {
            openBible()

            clickInSheet("Genesis")
            clickInSheet("1")

            assertTrue(shows("Genesis"), "the crumbs say where you are")
        }

    @Test
    fun `a whole chapter can be taken in one press`() = withCalendar(emptyService(), host = host()) { folder ->
        openBible()
        clickInSheet("Genesis")
        clickInSheet("1")

        clickFirst("Whole chapter")
        waitForIdle()
        clickLast("Add")
        waitForIdle()

        assertTrue(
            stored(folder).services.single().items.any { it is ScheduleItem.BibleVerseItem },
            "a verse range lands in the run of show",
        )
    }

    @Test
    fun `the crumbs lead back out of a book`() = withCalendar(emptyService(), host = host()) {
        openBible()
        clickInSheet("Genesis")

        clickFirst("All books")
        waitForIdle()

        assertTrue(shows("Psalms"), "back to the whole list")
    }

    @Test
    fun `a typed reference is offered whatever the browse state`() =
        withCalendar(emptyService(), host = host()) {
            openBible()

            typeIntoFirstField("Psalms 2:1-4")
            waitForIdle()

            assertTrue(shows("Psalms 2"), "the reference typed out is a result of its own")
        }

    @Test
    fun `a book shows its short name, is found by either, and is stored by the Bible's own`() {
        val isaiah = CalendarBibleBook(
            bookId = 23,
            name = "Книга пророка Исаии",
            verseCounts = listOf(31, 22),
            shortName = "Исаия",
        )
        withCalendar(emptyService(), host = CalendarHost(bibleBooks = { listOf(isaiah) })) { folder ->
            awaitText("Sunday Morning")
            clickFirst("Add song, verse or section")
            awaitText("Songs")
            clickFirst("Bible")
            awaitText("Исаия")
            assertFalse(shows("Книга пророка Исаии"), "the tile carries the short name")

            typeIntoFirstField("пророка")
            waitForIdle()
            clickInSheet("Исаия")
            clickInSheet("1")
            clickFirst("Whole chapter")
            waitForIdle()
            clickLast("Add")
            waitForIdle()

            val verse = stored(folder).services.single().items
                .filterIsInstance<ScheduleItem.BibleVerseItem>()
                .single()
            assertEquals("Книга пророка Исаии", verse.bookName)
        }
    }

    @Test
    fun `presets can be narrowed to one kind`() = withCalendar(documentWith(service())) { folder ->
        seedPresets(
            folder,
            ItemPreset(
                id = "p1", name = "Countdown",
                item = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5),
            ),
            ItemPreset(
                id = "p2", name = "Welcome clip",
                item = ScheduleItem.MediaItem(
                    id = "m", mediaUrl = "/c.mp4", mediaTitle = "Welcome", mediaType = "local",
                ),
            ),
        )
        clickFirst("Add song, verse or section")
        awaitText("Presets")
        clickFirst("Presets")
        awaitText("Countdown")

        clickFirst("Timers")
        waitForIdle()

        assertTrue(shows("Countdown"))
        assertTrue(!shows("Welcome clip"), "the other kinds are put away")
    }

    @Test
    fun `a preset can be deleted from settings`() = withCalendar(documentWith(service())) { folder ->
        seedPresets(
            folder,
            ItemPreset(
                id = "p1", name = "Countdown",
                item = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5),
            ),
        )
        // Opening something that offers presets is what re-reads them.
        clickFirst("Add song, verse or section")
        awaitText("Presets")
        clickIcon("Close")

        clickFirst("Calendar settings")
        awaitText("Sections")
        clickFirst("Presets")
        awaitText("Countdown")

        assertTrue(shows("Countdown"), "listed where it can be removed")
    }

    @Test
    fun `a saved template is listed with what it holds`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")
        clickIcon("Save this run of show")
        awaitText("Template")
        typeIntoLastField("Standard Sunday")
        clickFirst("Save template")
        waitForIdle()

        clickFirst("Calendar settings")
        awaitText("Sections")
        clickFirst("Templates")
        awaitText("Standard Sunday")

        assertTrue(shows("items"), "and what is in it")
    }

    @Test
    fun `a song can be edited from the picker when the app offers an editor`() {
        val folder = songFolderWith(libraSong("1", "Amazing Grace"))
        try {
            var asked = false
            val editor: @Composable (SongEditRequest) -> Unit = { request ->
                asked = true
                Text("Editing ${request.song.title}", modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            withCalendarEditor(emptyService(), songFolder = folder, songEditor = editor) {
                awaitText("Sunday Morning")
                clickFirst("Add song, verse or section")
                awaitText("Amazing Grace")

                clickIcon("Edit this song")
                waitForIdle()

                assertTrue(asked, "the app's own editor is what opens, not one of this window's")
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
