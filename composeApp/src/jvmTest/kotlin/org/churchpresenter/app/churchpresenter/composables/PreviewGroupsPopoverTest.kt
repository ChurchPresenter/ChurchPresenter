@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.PreviewGroup
import org.churchpresenter.settings.PreviewGroupShape
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The gear's editor: every control edits the [ProjectionSettings] it is handed and nothing else. */
class PreviewGroupsPopoverTest {

    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)

    private fun base(vararg groups: PreviewGroup) = ProjectionSettings(
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Lobby"), ScreenAssignment()),
        ndiOutputs = listOf(ScreenAssignment()),
        previewGroups = groups.toList(),
    )

    /** Composes the popover open over [start]; [block] drives it and reads the latest settings. */
    private fun edit(start: ProjectionSettings, block: ComposeUiTest.(() -> ProjectionSettings) -> Unit) =
        runComposeUiTest {
            var current by mutableStateOf(start)
            setContent {
                MaterialTheme {
                    PreviewGroupsPopover(expanded = true, onDismiss = {}, proj = current, onChange = { current = it })
                }
            }
            waitForIdle()
            block { current }
        }

    @Test
    fun `with no groups it says so and offers a new one`() = edit(base()) {
        onNodeWithText("No groups yet", substring = true).assertExists()
        onNodeWithText("New group").assertExists()
    }

    @Test
    fun `new group adds an empty group`() = edit(base()) { now ->
        onNodeWithText("New group").performClick()
        waitForIdle()
        assertEquals(1, now().previewGroups.size)
        onNodeWithText("Group 1").assertExists()
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
    fun `outputs not yet in a group are offered by their names`() = edit(base(PreviewGroup("g"))) {
        onNodeWithText("Add Lobby").assertExists()
        onNodeWithText("Add Browser Source 2").assertExists()
        onNodeWithText("Add NDI Output 1").assertExists()
    }

    @Test
    fun `adding an output puts it in the group and stops offering it`() =
        edit(base(PreviewGroup("g"))) { now ->
            onNodeWithText("Add Lobby").performClick()
            waitForIdle()
            assertEquals(listOf(bs0), now().previewGroups.single().members)
            onNodeWithText("Add Lobby").assertDoesNotExist()
        }

    @Test
    fun `picking a shape sets the group's shape`() = edit(base(PreviewGroup("g"))) { now ->
        onNodeWithText("3×1").performClick()
        waitForIdle()
        assertEquals(PreviewGroupShape.THREE_BY_ONE, now().previewGroups.single().shape)
    }

    @Test
    fun `every shape is offered on the one row`() = edit(base(PreviewGroup("g"))) {
        PreviewGroupShape.entries.forEach { onNodeWithText("${it.columns}×${it.rows}").assertExists() }
    }

    @Test
    fun `the hide switch on the group's line hides and shows the group`() = edit(base(PreviewGroup("g"))) { now ->
        assertFalse(now().previewGroups.single().hidden)
        onNodeWithTag(hideGroupTag("g")).performClick()
        waitForIdle()
        assertTrue(now().previewGroups.single().hidden)
        onNodeWithTag(hideGroupTag("g")).performClick()
        waitForIdle()
        assertFalse(now().previewGroups.single().hidden)
    }

    @Test
    fun `move down swaps a member with the one after it`() =
        edit(base(PreviewGroup("g", members = listOf(bs0, bs1)))) { now ->
            onAllNodesWithContentDescription("Move down").onFirst().performClick()
            waitForIdle()
            assertEquals(listOf(bs1, bs0), now().previewGroups.single().members)
        }

    @Test
    fun `move up swaps a member with the one before it`() =
        edit(base(PreviewGroup("g", members = listOf(bs0, bs1)))) { now ->
            onAllNodesWithContentDescription("Move up")[1].performClick()
            waitForIdle()
            assertEquals(listOf(bs1, bs0), now().previewGroups.single().members)
        }

    @Test
    fun `the first member cannot move up and the last cannot move down`() =
        edit(base(PreviewGroup("g", members = listOf(bs0, bs1)))) { now ->
            onAllNodesWithContentDescription("Move up").onFirst().performClick()
            onAllNodesWithContentDescription("Move down")[1].performClick()
            waitForIdle()
            assertEquals(listOf(bs0, bs1), now().previewGroups.single().members)
        }

    @Test
    fun `removing a member returns it to the offered list`() =
        edit(base(PreviewGroup("g", members = listOf(ndi0)))) { now ->
            onNodeWithText("Add NDI Output 1").assertDoesNotExist()
            onNodeWithContentDescription("Remove from group").performClick()
            waitForIdle()
            assertEquals(emptyList(), now().previewGroups.single().members)
            onNodeWithText("Add NDI Output 1").assertExists()
        }

    @Test
    fun `deleting a group removes it`() = edit(base(PreviewGroup("g"))) { now ->
        onNodeWithContentDescription("Delete group").performClick()
        waitForIdle()
        assertEquals(emptyList(), now().previewGroups)
    }

    @Test
    fun `groups are numbered by position`() = edit(base(PreviewGroup("a"), PreviewGroup("b"))) {
        onNode(hasText("Group 1")).assertExists()
        onNode(hasText("Group 2")).assertExists()
    }

    @Test
    fun `a member past the grid's capacity is still listed`() = edit(
        base(PreviewGroup("g", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(bs0, bs1))),
    ) {
        onNodeWithText("Lobby").assertExists()
        onNodeWithText("Browser Source 2").assertExists()
    }
}
