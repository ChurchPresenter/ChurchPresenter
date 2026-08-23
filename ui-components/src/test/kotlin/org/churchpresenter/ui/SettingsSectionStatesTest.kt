package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The titled block every settings tab is built from, and the labelled row inside it.
 *
 * Collapsing is the behaviour worth pinning: a collapsible section hides its body but must keep its
 * title, or an operator who collapses a section loses the only way back to it.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsSectionStatesTest {

    @Test
    fun `a plain section shows title and body`() = runComposeUiTest {
        setContent { MaterialTheme { SettingsSection(title = "Output") { Text("body") } } }
        onNodeWithText("Output").assertIsDisplayed()
        onNodeWithText("body").assertIsDisplayed()
    }

    @Test
    fun `a collapsible section expanded shows its body`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection(title = "Output", collapsible = true, expanded = true) { Text("body") }
            }
        }
        onNodeWithText("body").assertIsDisplayed()
    }

    @Test
    fun `a collapsed section hides the body but keeps the title`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection(title = "Output", collapsible = true, expanded = false) { Text("body") }
            }
        }
        onNodeWithText("Output").assertIsDisplayed()
        assertEquals(
            0,
            onAllNodesWithText("body").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "a collapsed section must not leave its body in the tree",
        )
    }

    @Test
    fun `clicking a collapsible header reports the new state`() = runComposeUiTest {
        var expanded = true
        setContent {
            MaterialTheme {
                SettingsSection(
                    title = "Output",
                    collapsible = true,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) { Text("body") }
            }
        }
        onAllNodes(hasClickAction()).onFirst().performClick()
        waitForIdle()
        assertFalse(expanded, "the header toggles the caller's state")
    }

    @Test
    fun `a non-collapsible header is not clickable`() = runComposeUiTest {
        setContent { MaterialTheme { SettingsSection(title = "Output") { Text("body") } } }
        assertEquals(
            0,
            onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "a section that cannot collapse must not look interactive",
        )
    }

    @Test
    fun `a setting row shows its label beside its control`() = runComposeUiTest {
        setContent { MaterialTheme { SettingRow(label = "Port") { Text("8080") } } }
        onNodeWithText("Port").assertIsDisplayed()
        onNodeWithText("8080").assertIsDisplayed()
    }

    @Test
    fun `a setting row renders with an empty label`() = runComposeUiTest {
        setContent { MaterialTheme { SettingRow(label = "") { Text("value only") } } }
        onNodeWithText("value only").assertIsDisplayed()
        assertTrue(true)
    }
}
