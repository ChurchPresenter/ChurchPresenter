@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.activeLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gear's editor: every control edits the [ProjectionSettings] it is handed and nothing else --
 * the name and mode switches, and the panel's layouts.
 */
class PreviewGroupsPopoverTest {

    private fun base() = ProjectionSettings(
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Lobby"), ScreenAssignment()),
        ndiOutputs = listOf(ScreenAssignment()),
    )

    private var editing = false

    /** Composes the popover open over [start]; [block] drives it and reads the latest settings. */
    private fun edit(start: ProjectionSettings, block: ComposeUiTest.(() -> ProjectionSettings) -> Unit) =
        runComposeUiTest {
            var current by mutableStateOf(start)
            setContent {
                MaterialTheme {
                    PreviewGroupsPopover(
                        expanded = true,
                        onDismiss = {},
                        proj = current,
                        onChange = { current = it },
                        onEditLayout = { editing = true },
                    )
                }
            }
            waitForIdle()
            block { current }
        }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun withLayouts(vararg ids: String) = base().copy(
        previewLayouts = ids.map { PreviewLayout(id = it) },
        activePreviewLayout = ids.first(),
    )

    @Test
    fun `the labels switch turns output labels off and on`() = edit(base()) { now ->
        assertTrue(now().showOutputLabels)
        onNodeWithTag(TAG_SHOW_LABELS).performClick()
        waitForIdle()
        assertFalse(now().showOutputLabels)
        onNodeWithTag(TAG_SHOW_LABELS).performClick()
        waitForIdle()
        assertTrue(now().showOutputLabels)
    }

    @Test
    fun `the display type switch turns the mode text off`() = edit(base()) { now ->
        assertTrue(now().showOutputModes)
        onNodeWithTag(TAG_SHOW_MODES).performClick()
        waitForIdle()
        assertFalse(now().showOutputModes)
    }

    @Test
    fun `with no layout it says the panel lists every output, and offers templates`() = edit(base()) { now ->
        onNodeWithText("every output is listed", substring = true).assertExists()
        onNodeWithTag(TAG_EDIT_LAYOUT).assertDoesNotExist()
        click(previewTemplateTag(1))
        val layout = now().activeLayout()
        assertEquals(SPLIT_DOWN, layout?.root?.split, "2x2 is two rows")
        assertEquals(previewOutputKeys(now()).take(4), layout?.root?.outputs(), "filled with the outputs in order")
    }

    @Test
    fun `a tall template puts the first output in a column beside the rest`() = edit(base()) { now ->
        click(previewTemplateTag(5))
        val root = now().activeLayout()?.root
        assertEquals(SPLIT_ACROSS, root?.split)
        assertEquals(previewOutputKeys(now()).first(), root?.children?.first()?.output)
        assertEquals(3, root?.children?.last()?.children?.size)
    }

    @Test
    fun `every template makes a layout that is drawn at once`() = edit(base()) { now ->
        repeat(7) { index -> click(previewTemplateTag(index)) }
        assertEquals(7, now().previewLayouts.size)
        assertEquals(now().previewLayouts.last().id, now().activePreviewLayout)
    }

    @Test
    fun `a click on another layout draws it`() = edit(withLayouts("a", "b")) { now ->
        onNodeWithText("Layout 2").performClick()
        waitForIdle()
        assertEquals("b", now().activePreviewLayout)
    }

    @Test
    fun `the drawn layout's name is typed over`() = edit(withLayouts("a")) { now ->
        onNode(hasSetTextAction()).performTextReplacement("Sunday")
        waitForIdle()
        assertEquals("Sunday", now().activeLayout()?.name)
    }

    @Test
    fun `deleting the drawn layout draws the next`() = edit(withLayouts("a", "b")) { now ->
        onAllNodesWithContentDescription("Delete layout")[0].performClick()
        waitForIdle()
        assertEquals(listOf("b"), now().previewLayouts.map { it.id })
        assertEquals("b", now().activePreviewLayout)
    }

    @Test
    fun `fill the panel and listing the rest are switched`() = edit(withLayouts("a")) { now ->
        click(TAG_FILLS_PANEL)
        click(TAG_LIST_UNPLACED)
        assertTrue(now().previewLayoutFillsPanel)
        assertFalse(now().listUnplacedOutputs)
    }

    @Test
    fun `Edit layout hands editing to the panel`() = edit(withLayouts("a")) { _ ->
        editing = false
        click(TAG_EDIT_LAYOUT)
        assertTrue(editing)
    }
}
