@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.splitArea
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The live panel as its layout's editor: each area's Split, Remove, place and output picker, the
 * dividers between areas, and Done. Every edit is read back from the settings the panel hands up.
 */
class LivePreviewPanelLayoutEditTest {

    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)

    private fun settings(root: PreviewArea) = AppSettings(
        projectionSettings = ProjectionSettings(
            browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
            previewLayouts = listOf(PreviewLayout(id = "l", root = root)),
            activePreviewLayout = "l",
            listUnplacedOutputs = false,
        ),
    )

    private var done = false

    /** Composes the panel editing [root]; [block] drives it and reads the layout's latest root. */
    private fun editing(root: PreviewArea, block: ComposeUiTest.(() -> PreviewArea?) -> Unit) = runComposeUiTest {
        var current by mutableStateOf(settings(root))
        setContent {
            MaterialTheme {
                LivePreviewPanel(
                    presenterManager = PresenterManager(),
                    appSettings = current,
                    onSettingsChange = { edit -> current = edit(current) },
                    editingLayout = true,
                    onDoneEditing = { done = true },
                )
            }
        }
        waitForIdle()
        block { current.projectionSettings.activeLayout()?.root }
    }

    private fun ComposeUiTest.tapInArea(path: List<Int>, label: String) {
        onNode(hasText(label) and hasAnyAncestor(hasTestTag(previewAreaTag(path)))).performClick()
        waitForIdle()
    }

    @Test
    fun `split across puts an empty area beside the one split`() = editing(PreviewArea(output = bs0)) { root ->
        tapInArea(emptyList(), "Split across")
        assertEquals(SPLIT_ACROSS, root()?.split)
        assertEquals(listOf(bs0, ""), root()?.children?.map { it.output })
    }

    @Test
    fun `an area inside a split is split again, down`() =
        editing(splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = bs0), PreviewArea()))) { root ->
            tapInArea(listOf(1), "Split down")
            assertEquals(SPLIT_DOWN, root()?.children?.get(1)?.split)
            assertEquals(bs0, root()?.children?.first()?.output, "the area beside it is untouched")
        }

    @Test
    fun `removing one of two areas leaves the other as the whole layout`() =
        editing(splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = bs0), PreviewArea(output = bs1)))) { root ->
            tapInArea(listOf(0), "Remove")
            assertEquals(PreviewArea(output = bs1), root())
        }

    @Test
    fun `an area's place is picked`() = editing(PreviewArea(output = bs0)) { root ->
        tapInArea(emptyList(), "Top")
        assertEquals(Constants.TOP, root()?.place)
    }

    @Test
    fun `an output placed in one area is taken from the other`() =
        editing(splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = bs0), PreviewArea()))) { root ->
            onNodeWithTag(previewAreaOutputTag(listOf(1))).performClick()
            waitForIdle()
            // The label shows on the first area's picker and its preview too; the menu's item is the last.
            val choices = onAllNodes(hasText("Browser Source 1"))
            choices[choices.fetchSemanticsNodes().lastIndex].performClick()
            waitForIdle()
            assertEquals(listOf("", bs0), root()?.children?.map { it.output })
        }

    private fun ComposeUiTest.dragDivider(dx: Float) {
        onNodeWithTag(previewDividerTag(emptyList(), 0)).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(dx / 2, 0f))
            moveBy(Offset(dx / 2, 0f))
            release()
        }
        waitForIdle()
    }

    @Test
    fun `dragging a divider gives one side more of the room`() =
        editing(splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = bs0), PreviewArea(output = bs1)))) { root ->
            dragDivider(60f)
            val ratios = root()?.ratios.orEmpty()
            assertTrue(ratios[0] > ratios[1], "the left area grew: $ratios")
            assertEquals(1f, ratios.sum(), 0.001f)
        }

    @Test
    fun `a second drag carries on from where the first left the divider`() =
        editing(splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = bs0), PreviewArea(output = bs1)))) { root ->
            dragDivider(40f)
            val first = root()?.ratios.orEmpty().first()
            dragDivider(40f)
            val second = root()?.ratios.orEmpty().first()
            assertTrue(second > first, "$first → $second")
        }

    @Test
    fun `Done ends editing`() = editing(PreviewArea(output = bs0)) { _ ->
        done = false
        onNodeWithTag(PREVIEW_LAYOUT_DONE_TAG).performClick()
        waitForIdle()
        assertTrue(done)
    }
}
