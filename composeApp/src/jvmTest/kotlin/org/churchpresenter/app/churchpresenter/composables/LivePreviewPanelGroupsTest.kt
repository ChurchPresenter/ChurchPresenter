@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PreviewGroup
import org.churchpresenter.settings.PreviewGroupShape
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.layoutFromGroups
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test


/**
 * The sidebar panel once it has a layout made from preview groups -- as settings version 20 makes
 * one: which outputs it draws and what it leaves off. The display counting and badges are covered by
 * [LivePreviewPanelTest].
 *
 * Headless there is no real monitor, so `Screen 1` is always the dev-fallback preview and the
 * Browser Source and NDI outputs below are the ones a group can hold or leave out.
 */
class LivePreviewPanelGroupsTest {

    private val screen0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0)
    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)

    private val collapse = "Hide this preview"

    private fun settings(
        vararg groups: PreviewGroup,
        showLabels: Boolean = true,
        showModes: Boolean = true,
    ) = AppSettings(
        projectionSettings = ProjectionSettings(
            browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
            ndiOutputs = listOf(ScreenAssignment(ndiEnabled = true)),
            previewLayouts = listOfNotNull(layoutFromGroups(groups.toList(), name = "")),
            // As the upgrade leaves it: outputs no group held stay out of the panel.
            listUnplacedOutputs = groups.isEmpty(),
            showOutputLabels = showLabels,
            showOutputModes = showModes,
        ),
    )

    private fun ComposeUiTest.panel(s: AppSettings) = setContent {
        MaterialTheme { LivePreviewPanel(presenterManager = PresenterManager(), appSettings = s) }
    }

    // ── What is shown ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `with no groups every output is listed`() = runComposeUiTest {
        panel(settings())
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Browser Source 1").assertExists()
        onNodeWithText("Browser Source 2").assertExists()
        onNodeWithText("NDI Output 1").assertExists()
    }

    @Test
    fun `once a group exists only grouped outputs are shown`() = runComposeUiTest {
        panel(settings(PreviewGroup("g", members = listOf(bs1))))
        onNodeWithText("Browser Source 2").assertExists()
        onNodeWithText("Screen 1").assertDoesNotExist()
        onNodeWithText("Browser Source 1").assertDoesNotExist()
        onNodeWithText("NDI Output 1").assertDoesNotExist()
    }

    @Test
    fun `an empty group leaves the panel empty of outputs`() = runComposeUiTest {
        panel(settings(PreviewGroup("g")))
        onNodeWithText("Screen 1").assertDoesNotExist()
        onNodeWithText("Browser Source 1").assertDoesNotExist()
    }

    @Test
    fun `a hidden group draws nothing and its outputs do not fall back to the list`() = runComposeUiTest {
        panel(settings(PreviewGroup("g", members = listOf(bs0)).copy(hidden = true)))
        onNodeWithText("Browser Source 1").assertDoesNotExist()
        onNodeWithText("Screen 1").assertDoesNotExist()
    }

    @Test
    fun `a hidden group does not hide another`() = runComposeUiTest {
        panel(
            settings(
                PreviewGroup("a", members = listOf(bs0)).copy(hidden = true),
                PreviewGroup("b", members = listOf(bs1)),
            ),
        )
        onNodeWithText("Browser Source 1").assertDoesNotExist()
        onNodeWithText("Browser Source 2").assertExists()
    }

    @Test
    fun `members past the grid's capacity are left off`() = runComposeUiTest {
        panel(settings(PreviewGroup("g", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(bs0, bs1))))
        onNodeWithText("Browser Source 1").assertExists()
        onNodeWithText("Browser Source 2").assertDoesNotExist()
    }

    @Test
    fun `a member naming an output that no longer exists is skipped`() = runComposeUiTest {
        panel(settings(PreviewGroup("g", members = listOf("browserSource:9", bs0))))
        onNodeWithText("Browser Source 1").assertExists()
    }

    @Test
    fun `several groups are all drawn`() = runComposeUiTest {
        panel(
            settings(
                PreviewGroup("a", members = listOf(screen0, bs0)),
                PreviewGroup("b", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(ndi0)),
            ),
        )
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Browser Source 1").assertExists()
        onNodeWithText("NDI Output 1").assertExists()
    }

    // ── Collapsing ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an ungrouped preview can be collapsed`() = runComposeUiTest {
        panel(settings())
        onAllNodesWithContentDescription(collapse).assertCountEquals(4)
    }

    @Test
    fun `a preview inside a group has no collapse control`() = runComposeUiTest {
        panel(settings(PreviewGroup("g", members = listOf(bs0, bs1))))
        onAllNodesWithContentDescription(collapse).assertCountEquals(0)
        onNodeWithText("Browser Source 1").assertExists()
    }

    // ── Labels and display type ─────────────────────────────────────────────────────────────────

    @Test
    fun `turning labels off removes the name from each preview`() = runComposeUiTest {
        panel(settings(showLabels = false))
        onNodeWithText("Screen 1").assertDoesNotExist()
        onNodeWithText("Browser Source 1").assertDoesNotExist()
    }

    @Test
    fun `turning display type off removes the mode text`() = runComposeUiTest {
        panel(settings(showModes = false))
        onNodeWithText("Full Screen").assertDoesNotExist()
        onNodeWithText("Screen 1").assertExists()
    }

    @Test
    fun `the display type is shown by default`() = runComposeUiTest {
        panel(settings())
        // One per output, so there are several: the point is that the text is there at all.
        onAllNodesWithText("Full Screen").assertCountEquals(4)
    }
}
