@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The small pieces the sheets are built from, each on its own: how a pinned start reads against
 * the service's, a cue with no time to fire at, the book scope on a book the library lost, a kind
 * chip pressed twice, the Bible tab with no Bible, and a saved export with no folder to name.
 */
class SmallPartsTest {

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalUse24HourClock provides true) { content() }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a pinned start reads as an offset from the service, or as the clock when either cannot be read`() =
        runComposeUiTest {
            val labels = mutableListOf<String>()
            show {
                labels.clear()
                labels += startOffsetLabel("10:00", "10:00")
                labels += startOffsetLabel("10:15", "10:00")
                labels += startOffsetLabel("09:45", "10:00")
                labels += startOffsetLabel("09:45", "")
                labels += startOffsetLabel("soon", "10:00")
            }
            assertEquals(listOf("On time", "+15", "−15", "09:45", "soon"), labels)
        }

    @Test
    fun `a cue timed from a start that is not known has no time to show`() = runComposeUiTest {
        var text = "unset"
        show { text = cueTimeText(ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, offsetMinutes = 5), null) }
        assertEquals("", text)
    }

    @Test
    fun `a song book the library no longer has counts no songs`() = runComposeUiTest {
        show {
            SongBookScope(
                songs = listOf(SongItem(number = "1", title = "A", songbook = "Hymns")),
                selected = "Choruses",
                onSelect = {},
            )
        }
        assertTrue(shows("Choruses"))
        assertTrue(shows("0"))
    }

    @Test
    fun `a kind chip pressed again shows every kind`() = runComposeUiTest {
        var selected by mutableStateOf<PresetKind?>(null)
        val presets = listOf(
            ItemPreset("p1", "Welcome", ScheduleItem.SceneItem("s", "scene-1", "Welcome")),
            ItemPreset("p2", "Lobby", ScheduleItem.PictureItem("pic", "/pics", "Lobby", 4)),
        )
        show { PresetKindScope(presets, selected) { selected = it } }

        clickFirst("Scenes")
        assertEquals(PresetKind.SCENES, selected)
        clickFirst("Scenes")
        assertEquals(null, selected)
    }

    @Test
    fun `with no Bible loaded a typed reference is still offered`() = runComposeUiTest {
        val added = mutableListOf<ScheduleItem>()
        var query by mutableStateOf("")
        show {
            BibleResults(
                books = emptyList<CalendarBibleBook>(), query = query, book = null, chapter = null,
                selection = null, onBook = {}, onChapter = {}, onVerse = {}, onAdd = { added += it },
            )
        }
        assertTrue(shows("No Bible is loaded"))

        query = "John 3:16"
        waitForIdle()
        clickFirst("John 3:16")
        assertEquals("John", assertIs<ScheduleItem.BibleVerseItem>(added.single()).bookName)
    }

    @Test
    fun `a book is found by the short name its tile shows`() = runComposeUiTest {
        val books = listOf(
            CalendarBibleBook(bookId = 23, name = "Книга пророка Исаии", verseCounts = listOf(31), shortName = "Исаия"),
            CalendarBibleBook(bookId = 1, name = "Бытие", verseCounts = listOf(31)),
        )
        show {
            BibleResults(
                books = books, query = "исаия", book = null, chapter = null,
                selection = null, onBook = {}, onChapter = {}, onVerse = {}, onAdd = {},
            )
        }
        assertTrue(shows("Исаия"))
        assertTrue(!shows("Бытие"))
    }

    @Test
    fun `a saved export with no folder to name still says it was saved`() = runComposeUiTest {
        show { ExportToast(ExportOutcome.Saved(File("run.pdf")), onDismiss = {}) }
        assertTrue(shows("run.pdf"))
    }

    @Test
    fun `a cue on a disarmed service can still be ticked and fired by hand`() = runComposeUiTest {
        var toggled = 0
        var fired = 0
        show {
            CueRow(
                cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, absoluteTime = "09:45"),
                startTime = "10:00",
                armed = false,
                status = null,
                onToggle = { toggled++ },
                onFire = { fired++ },
            )
        }
        assertTrue(shows("09:45"), "its time is still shown, dimmed")
        clickIcon("Fire this cue now")
        assertEquals(1, fired)
        clickIcon("Skip this cue")
        assertEquals(1, toggled)
    }
}
