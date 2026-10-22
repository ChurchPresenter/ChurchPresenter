@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.utils.Constants
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
    fun `the profile sets switch shows the header's button, offered with two outputs and two profiles`() {
        val offered = base().copy(outputProfiles = listOf(OutputProfile(id = "a"), OutputProfile(id = "b")))
        edit(offered) { now ->
            assertFalse(now().showProfileSetsButton)
            onNodeWithTag(TAG_SHOW_PROFILE_SETS).performClick()
            waitForIdle()
            assertTrue(now().showProfileSetsButton)
        }
        edit(base()) { onNodeWithTag(TAG_SHOW_PROFILE_SETS).assertDoesNotExist() }
    }

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
    fun `a template places an OMT output under its own key`() =
        edit(ProjectionSettings(omtOutputs = listOf(ScreenAssignment(omtName = "Overflow")))) { now ->
            click(previewTemplateTag(1))
            val omt = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 0)
            assertTrue(omt in now().activeLayout()?.root?.outputs().orEmpty(), "${now().activeLayout()?.root}")
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

    @Test
    fun `the menu is headed Display and Layout`() = edit(withLayouts("a")) { _ ->
        onNodeWithText("DISPLAY").assertExists()
        onNodeWithText("LAYOUT").assertExists()
    }

    @Test
    fun `the pencil puts the cursor in the drawn layout's name`() = edit(withLayouts("a")) { _ ->
        onNodeWithContentDescription("Rename layout").performClick()
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `deleting a layout not drawn keeps the drawn one`() = edit(withLayouts("a", "b")) { now ->
        onAllNodesWithContentDescription("Delete layout")[1].performClick()
        waitForIdle()
        assertEquals(listOf("a"), now().previewLayouts.map { it.id })
        assertEquals("a", now().activePreviewLayout)
    }

    @Test
    fun `each template is named for a screen reader and under the pointer`() = edit(base()) { _ ->
        onNodeWithText("New layout").assertExists()
        onNodeWithContentDescription("2×2").assertExists()
        onNodeWithTag(previewTemplateTag(1)).performMouseInput { enter(center) }
        waitForIdle()
        onNodeWithText("New layout: 2×2").assertExists()
        onNodeWithTag(previewTemplateTag(1)).performMouseInput { exit() }
        waitForIdle()
        onNodeWithText("New layout").assertExists()
    }

    @Test
    fun `Edit layout closes the menu`() = runComposeUiTest {
        var dismissed = false
        setContent {
            MaterialTheme {
                PreviewGroupsPopover(
                    expanded = true,
                    onDismiss = { dismissed = true },
                    proj = withLayouts("a"),
                    onChange = {},
                )
            }
        }
        waitForIdle()
        click(TAG_EDIT_LAYOUT)
        assertTrue(dismissed)
    }
}
