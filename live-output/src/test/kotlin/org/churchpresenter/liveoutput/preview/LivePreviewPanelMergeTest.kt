package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test

/**
 * Outputs merged into one picture are previewed once, under the first of them and marked as merged,
 * rather than as one tile per member each showing the same picture. Outputs outside the merge keep
 * their own tiles and their own names.
 */
@OptIn(ExperimentalTestApi::class)
class LivePreviewPanelMergeTest {

    private val following = ScreenAssignment(activeProfileId = "wall")

    private fun wall(vararg keys: String) =
        OutputProfile(id = "wall", merge = OutputMerge(keys.mapIndexed { i, key -> MergeTile(key, x = i * 1920) }))

    private fun ComposeUiTest.panel(projection: ProjectionSettings) = setContent {
        MaterialTheme {
            LivePreviewPanel(
                presenterManager = PresenterManager(),
                appSettings = AppSettings(projectionSettings = projection),
            )
        }
    }

    @Test
    fun `two merged ndi feeds are previewed once, under the first, marked as merged`() = runComposeUiTest {
        panel(
            ProjectionSettings(
                outputProfiles = listOf(wall("ndi:0", "ndi:1"), OutputProfile(id = "own")),
                ndiOutputs = listOf(following, following, ScreenAssignment(activeProfileId = "own")),
            ),
        )

        onNodeWithText("NDI Output 1 (merged)").assertExists()
        onNodeWithText("NDI Output 2", substring = true).assertDoesNotExist()
        onNodeWithText("NDI Output 3").assertExists()
    }

    @Test
    fun `two merged omt feeds are previewed once`() = runComposeUiTest {
        panel(
            ProjectionSettings(
                outputProfiles = listOf(wall("omt:0", "omt:1")),
                omtOutputs = listOf(following, following),
            ),
        )

        onNodeWithText("OMT Output 1 (merged)").assertExists()
        onNodeWithText("OMT Output 2", substring = true).assertDoesNotExist()
    }

    @Test
    fun `two merged browser sources are previewed once`() = runComposeUiTest {
        panel(
            ProjectionSettings(
                outputProfiles = listOf(wall("browserSource:0", "browserSource:1")),
                browserSourceOutputs = listOf(following, following),
            ),
        )

        onNodeWithText("Browser Source 1 (merged)").assertExists()
        onNodeWithText("Browser Source 2", substring = true).assertDoesNotExist()
    }

    @Test
    fun `two merged dev windows are previewed once`() = runComposeUiTest {
        panel(
            ProjectionSettings(
                devWindowCount = 2,
                outputProfiles = listOf(wall("screen:0", "screen:1")),
                screenAssignments = listOf(following, following),
            ),
        )

        onNodeWithText("Screen 1 (merged)").assertExists()
        onNodeWithText("Screen 2", substring = true).assertDoesNotExist()
    }
}
