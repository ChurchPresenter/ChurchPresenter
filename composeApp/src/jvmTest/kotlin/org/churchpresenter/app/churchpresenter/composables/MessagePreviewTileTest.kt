@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.showMessage
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test

/** A message on an output, as its preview tile draws it -- and an output whose look leaves it out. */
class MessagePreviewTileTest {

    private fun manager() = PresenterManager().apply { showMessage(Cue.Message("Parent of child #42")) }

    @Test
    fun `an output draws the message up, and its tile is live`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { MaterialTheme { LivePreviewPanel(presenterManager = manager(), appSettings = AppSettings()) } }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Parent of child #42", substring = true).assertExists()
        onNodeWithTag(LIVE_TILE_TAG).assertExists()
    }

    @Test
    fun `an output whose look leaves messages out draws nothing`() = runComposeUiTest {
        val noMessages = AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(OutputProfile(id = "quiet", look = OutputLook(messages = false))),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "quiet")),
            ),
        )
        mainClock.autoAdvance = false
        setContent { MaterialTheme { LivePreviewPanel(presenterManager = manager(), appSettings = noMessages) } }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Parent of child #42", substring = true).assertDoesNotExist()
        onNodeWithTag(LIVE_TILE_TAG).assertDoesNotExist()
    }
}
