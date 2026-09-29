@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/** The gear's editor: every control edits the [ProjectionSettings] it is handed and nothing else. */
class PreviewGroupsPopoverTest {

    private fun base() = ProjectionSettings(
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Lobby"), ScreenAssignment()),
        ndiOutputs = listOf(ScreenAssignment()),
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
}
