@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.announcements.annButton
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MainDesktopAnnouncementAndCueTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-main-desktop-branches").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun settings(only: Tabs? = null): AppSettings {
        val base = AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath))
        val withText = base.copy(announcementsSettings = base.announcementsSettings.copy(text = "Welcome"))
        return if (only == null) withText
        else withText.copy(hiddenTabs = Tabs.entries.filter { it != only }.map { it.name }.toSet())
    }

    private class Seen {
        var actions = ScheduleActions()
    }

    private fun root(
        appSettings: AppSettings,
        presenterManager: PresenterManager = PresenterManager(),
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        var current by mutableStateOf(appSettings)
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = current,
                    presenterManager = presenterManager,
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    live = LiveOutputCallbacks(
                        presenting = { presenterManager.setPresentingMode(it) },
                        onVerseSelected = {},
                        onSongItemSelected = {},
                    ),
                    publish = MainDesktopPublishers(
                        onScheduleActionsReady = { seen.actions = it },
                    ),
                )
            }
        }
        waitForIdle()
        // A changed input rebuilds the root's scope, which picks up the Schedule's published actions.
        current = appSettings.copy(songFavoritesPanelHeightDp = appSettings.songFavoritesPanelHeightDp + 1)
        waitForIdle()
        block(seen)
    }

    private fun ComposeUiTest.scheduleRows(): Int =
        onAllNodesWithTag(SCHEDULE_ROW_CARD_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.addAnnouncementFromTab() {
        annButton("Add to Schedule").performClick()
        waitForIdle()
    }

    @Test
    fun `an untimed announcement added from its tab is put on the schedule`() =
        root(settings(Tabs.ANNOUNCEMENTS)) { seen ->
            addAnnouncementFromTab()

            assertEquals(1, scheduleRows())
        }

    @Test
    fun `an announcement with a duration is put on the schedule`() {
        val base = settings(Tabs.ANNOUNCEMENTS)
        root(base.copy(announcementsSettings = base.announcementsSettings.copy(timerMinutes = 5))) { seen ->
            addAnnouncementFromTab()

            assertEquals(1, scheduleRows())
        }
    }

    @Test
    fun `an announcement in clock mode is put on the schedule`() {
        val base = settings(Tabs.ANNOUNCEMENTS)
        val clock = base.announcementsSettings.copy(timerMode = Constants.TIMER_MODE_CLOCK)
        root(base.copy(announcementsSettings = clock)) { seen ->
            addAnnouncementFromTab()

            assertEquals(1, scheduleRows())
        }
    }

    @Test
    fun `an announcement with only seconds is put on the schedule`() {
        val base = settings(Tabs.ANNOUNCEMENTS)
        root(base.copy(announcementsSettings = base.announcementsSettings.copy(timerSeconds = 30))) { _ ->
            addAnnouncementFromTab()
            assertEquals(1, scheduleRows())
        }
    }

    @Test
    fun `an announcement with only hours is put on the schedule`() {
        val base = settings(Tabs.ANNOUNCEMENTS)
        root(base.copy(announcementsSettings = base.announcementsSettings.copy(timerHours = 1))) { _ ->
            addAnnouncementFromTab()
            assertEquals(1, scheduleRows())
        }
    }

    @Test
    fun `a slideshow cue starts each kind of player and ignores the rest`() {
        val presenter = PresenterManager()
        root(settings(), presenter) { seen ->
        val folder = File(dir, "Pictures").apply { mkdirs() }
        seen.actions.playSlideshow(
            ScheduleItem.MediaItem(id = "m", mediaUrl = "file:///nowhere.mp4", mediaTitle = "t", mediaType = "local"),
            1,
        )
        seen.actions.playSlideshow(
            ScheduleItem.PictureItem(
                id = "p", folderPath = folder.absolutePath, folderName = "Pictures", imageCount = 0,
            ),
            0,
        )
        seen.actions.playSlideshow(
            ScheduleItem.PresentationItem(
                id = "d", filePath = File(dir, "deck.pdf").absolutePath, fileName = "deck.pdf",
                slideCount = 0, fileType = "pdf",
            ),
            2,
        )
        seen.actions.playSlideshow(ScheduleItem.SongItem(id = "s", songNumber = 1, title = "A", songbook = "B"), 1)
        waitForIdle()

        assertEquals(Presenting.NONE, presenter.slideContent.value, "a cue starts a player; it does not go live")
        }
    }

    @Test
    fun `presenting a scene that does not exist still takes the canvas live`() {
        val presenter = PresenterManager()
        root(settings(), presenter) { seen ->
            seen.actions.presentScene("no-such-scene")
            waitForIdle()

            assertEquals(Presenting.CANVAS, presenter.slideContent.value)
        }
    }
}
