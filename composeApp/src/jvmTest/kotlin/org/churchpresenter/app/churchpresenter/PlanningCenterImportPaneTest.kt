package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.planningcenter.ui.PlanningCenterWindow
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The app's side of the Planning Center import: where what it picks lands ([PlanningCenterImportSink])
 * and the import itself opened through a window that draws in place. Nothing here reaches Planning
 * Center: with no token the import only offers to connect, and the Connect button is never pressed.
 */
@OptIn(ExperimentalTestApi::class)
class PlanningCenterImportPaneTest {

    private val schedules = mutableListOf<ScheduleViewModel>()

    /**
     * Every Schedule a test made is disposed after it. Left running, its autosave loop writes the
     * schedule into the fork's shared home a minute later, and a later suite that draws the Schedule
     * tab opens on the restore prompt instead of the window it came to test.
     */
    @AfterTest
    fun disposeSchedules() {
        schedules.forEach { it.dispose() }
    }

    /** A sink over a fresh Schedule, with every settings change applied to [settings]. */
    private inner class Rig {
        val schedule = ScheduleViewModel().also { schedules += it }
        var settings = AppSettings()
        val sink = PlanningCenterImportSink(schedule) { settings = it(settings) }
    }

    @Test
    fun `everything the import picks is added to the Schedule in order`() {
        val rig = Rig()
        rig.sink.addSong(12, "Amazing Grace", "Hymnal", "song-1")
        rig.sink.addLabel("Welcome", "#FFFFFF", "#000000")
        rig.sink.addPresentation("/decks/sermon.pptx", "sermon.pptx", 14, "pptx")
        rig.sink.addPicture("/pictures/easter", "easter", 6)
        rig.sink.addMedia("https://example.org/clip.mp4", "Clip", "video")
        rig.sink.addAnnouncement("Coffee after the service")
        rig.sink.addBibleVerse("John", 3, 16, "For God so loved the world", "16-17", 43)

        val items = rig.schedule.scheduleItems
        assertEquals(7, items.size)
        assertIs<ScheduleItem.SongItem>(items[0]).also { assertEquals("Amazing Grace", it.title) }
        assertIs<ScheduleItem.LabelItem>(items[1])
        assertIs<ScheduleItem.PresentationItem>(items[2]).also { assertEquals(14, it.slideCount) }
        assertIs<ScheduleItem.PictureItem>(items[3])
        assertIs<ScheduleItem.MediaItem>(items[4])
        assertIs<ScheduleItem.AnnouncementItem>(items[5])
        assertIs<ScheduleItem.BibleVerseItem>(items[6]).also { assertEquals(43, it.bookId) }
    }

    @Test
    fun `connecting saves the tokens and who connected`() {
        val rig = Rig()
        rig.sink.connected("access", "refresh", 1_000L, "Sam")
        val pco = rig.settings.planningCenterSettings
        assertEquals("access", pco.accessToken)
        assertEquals("refresh", pco.refreshToken)
        assertEquals(1_000L, pco.tokenExpiresAtEpochMs)
        assertEquals("Sam", pco.connectedPersonName)
    }

    @Test
    fun `a refresh replaces the tokens and keeps who connected`() {
        val rig = Rig()
        rig.sink.connected("access", "refresh", 1_000L, "Sam")
        rig.sink.tokensRefreshed("access-2", "refresh-2", 2_000L)
        val pco = rig.settings.planningCenterSettings
        assertEquals("access-2", pco.accessToken)
        assertEquals(2_000L, pco.tokenExpiresAtEpochMs)
        assertEquals("Sam", pco.connectedPersonName)
    }

    @Test
    fun `disconnecting clears the tokens and the name`() {
        val rig = Rig()
        rig.sink.connected("access", "refresh", 1_000L, "Sam")
        rig.sink.disconnected()
        val pco = rig.settings.planningCenterSettings
        assertEquals("", pco.accessToken)
        assertEquals("", pco.refreshToken)
        assertEquals(0L, pco.tokenExpiresAtEpochMs)
        assertEquals("", pco.connectedPersonName)
    }

    /** Draws each window's content where it is, and records its title. */
    private fun inPlace(titles: MutableList<String>): PlanningCenterWindow = { spec, content ->
        titles += spec.title
        Box { content() }
    }

    @Test
    fun `with no token the import offers to connect, and Cancel dismisses it`() = runComposeUiTest {
        val titles = mutableListOf<String>()
        var dismissed = 0
        setContent {
            PlanningCenterImportPane(
                isVisible = true,
                theme = ThemeMode.LIGHT,
                settings = PlanningCenterSettings(),
                sink = Rig().sink,
                onDismiss = { dismissed++ },
                window = inPlace(titles),
            )
        }
        onNodeWithText("Connect to Planning Center").assertExists()
        assertTrue(titles.isNotEmpty(), "the connect window was opened through the window it was given")
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `an open import follows a new sink and new settings, and closes when hidden`() = runComposeUiTest {
        val titles = mutableListOf<String>()
        val sink = mutableStateOf(Rig().sink)
        val settings = mutableStateOf(PlanningCenterSettings())
        val visible = mutableStateOf(true)
        setContent {
            PlanningCenterImportPane(
                isVisible = visible.value,
                theme = ThemeMode.LIGHT,
                settings = settings.value,
                sink = sink.value,
                onDismiss = {},
                window = inPlace(titles),
            )
        }
        onNodeWithText("Connect to Planning Center").assertExists()
        settings.value = PlanningCenterSettings(connectedPersonName = "Sam")
        waitForIdle()
        sink.value = Rig().sink
        waitForIdle()
        onNodeWithText("Connect to Planning Center").assertExists()
        visible.value = false
        waitForIdle()
        onNodeWithText("Connect to Planning Center").assertDoesNotExist()
    }

    @Test
    fun `a hidden import opens nothing, and the song editor waits for a song`() = runComposeUiTest {
        val titles = mutableListOf<String>()
        setContent {
            PlanningCenterImportPane(
                isVisible = false,
                theme = ThemeMode.DARK,
                settings = PlanningCenterSettings(),
                sink = Rig().sink,
                onDismiss = {},
                window = inPlace(titles),
            )
            planningCenterSongEditor(ThemeMode.DARK)(null, "Planning Center", {}, {})
        }
        waitForIdle()
        assertTrue(titles.isEmpty())
    }
}
