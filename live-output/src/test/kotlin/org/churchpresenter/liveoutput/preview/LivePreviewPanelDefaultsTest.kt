@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The panel where a host gives it nothing to report to -- a preview composes it that way -- and its
 * profile swap menu dismissed without a pick. Every control must still be safe to use.
 */
class LivePreviewPanelDefaultsTest {

    private fun ComposeUiTest.clickOutsidePopup() {
        onAllNodes(isRoot())[0].performTouchInput { click(bottomRight - Offset(2f, 2f)) }
        waitForIdle()
    }

    @Test
    fun `editing a layout with no host to report to is safe, Done included`() = runComposeUiTest {
        val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                browserSourceOutputs = listOf(ScreenAssignment()),
                previewLayouts = listOf(PreviewLayout(id = "l", root = PreviewArea(output = bs0))),
                activePreviewLayout = "l",
                listUnplacedOutputs = false,
            ),
        )
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = settings, editingLayout = true)
            }
        }
        waitForIdle()
        onNode(hasText("Split across") and hasAnyAncestor(hasTestTag(previewAreaTag(emptyList())))).performClick()
        waitForIdle()
        onNodeWithTag(PREVIEW_LAYOUT_DONE_TAG).performClick()
        waitForIdle()
        // Nothing was reported, so the layout is as it was: still one area.
        onNodeWithTag(previewAreaTag(listOf(0))).assertDoesNotExist()
    }

    @Test
    fun `the profile swap menu closes on a click outside and picks with no host to report to`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        waitForIdle()
        onAllNodesWithContentDescription("Swap output profile")[0].performClick()
        waitForIdle()
        onNodeWithText("Blank").assertExists()
        clickOutsidePopup()
        onNodeWithText("Blank").assertDoesNotExist()

        onAllNodesWithContentDescription("Swap output profile")[0].performClick()
        waitForIdle()
        onNodeWithText("Blank").performClick()
        waitForIdle()
        onNodeWithText("Blank").assertDoesNotExist()
    }
}
