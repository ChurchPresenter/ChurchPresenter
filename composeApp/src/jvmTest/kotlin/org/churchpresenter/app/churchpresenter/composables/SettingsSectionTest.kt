package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * The titled card that groups related fields throughout the settings dialogs.
 *
 * It renders its [title] header and then its slot content; if either failed to compose, a settings
 * group would appear headerless or empty. Asserting both proves the header and the content slot are
 * wired.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsSectionTest {

    @Test
    fun `the section renders its title and its slot content`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection(title = "Display") {
                    Text("Auto-fit text")
                }
            }
        }
        onNodeWithText("Display", substring = true).assertExists("the group must show its heading")
        onNodeWithText("Auto-fit text",
            substring = true).assertExists("the slot content must be composed inside the section")
    }

    @Test
    fun `headerTrailing content renders alongside the title when given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection(title = "Display", headerTrailing = { Text("Reset") }) {
                    Text("Auto-fit text")
                }
            }
        }
        onNodeWithText("Reset").assertExists("headerTrailing must be composed at the header's trailing edge")
    }
}
