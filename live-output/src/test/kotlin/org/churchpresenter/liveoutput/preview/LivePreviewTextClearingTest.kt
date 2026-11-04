@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearTextOnly
import org.churchpresenter.liveoutput.restoreText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test

class LivePreviewTextClearingTest {

    private val lyric = "Amazing grace how sweet the sound"

    private val settings = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(OutputProfile(id = "main", displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
            screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
        ),
    )

    @Test
    fun `the cleared lyric leaves the preview and comes back when restored`() = runComposeUiTest {
        val manager = PresenterManager().apply {
            setPresentingMode(Presenting.LYRICS)
            setDisplayedLyricSection(LyricSection(type = "verse", title = "Amazing Grace", lines = listOf(lyric)))
        }
        setContent {
            MaterialTheme { LivePreviewPanel(presenterManager = manager, appSettings = settings) }
        }
        waitForIdle()
        onNodeWithText(lyric, substring = true).assertExists()

        manager.clearTextOnly()
        waitForIdle()
        onNodeWithText(lyric, substring = true).assertDoesNotExist()

        manager.restoreText()
        waitForIdle()
        onNodeWithText(lyric, substring = true).assertExists()
    }

    @Test
    fun `the cleared announcement leaves the preview and comes back on go live`() = runComposeUiTest {
        val announcement = "Coffee after the service"
        val manager = PresenterManager().apply {
            setAnnouncementText(announcement)
            setDisplayedAnnouncementText(announcement)
            setPresentingMode(Presenting.ANNOUNCEMENTS)
        }
        setContent {
            MaterialTheme { LivePreviewPanel(presenterManager = manager, appSettings = settings) }
        }
        waitForIdle()
        onNodeWithText(announcement, substring = true).assertExists()

        manager.clearTextOnly()
        waitForIdle()
        onNodeWithText(announcement, substring = true).assertDoesNotExist()

        manager.previewBus.present(Presenting.ANNOUNCEMENTS)
        waitForIdle()
        onNodeWithText(announcement, substring = true).assertExists()
    }
}
