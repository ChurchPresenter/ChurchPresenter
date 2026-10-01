package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsSectionLayoutTest {

    @Test
    fun `a collapsed section hides its body`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection("KJV", collapsible = true, expanded = false) { Text("Font size") }
            }
        }
        onNodeWithText("KJV").assertExists()
        assertEquals(0, onAllNodesWithText("Font size").fetchSemanticsNodes().size)
    }

    @Test
    fun `an expanded collapsible section shows its body`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsSection("KJV", collapsible = true, expanded = true) { Text("Font size") }
            }
        }
        onNodeWithText("Font size").assertExists()
    }

    @Test
    fun `clicking a collapsible header toggles it`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        setContent {
            MaterialTheme {
                SettingsSection("KJV", collapsible = true, expanded = expanded, onExpandedChange = { expanded = it }) {
                    Text("Font size")
                }
            }
        }
        onNodeWithText("KJV").performClick()
        waitForIdle()
        assertEquals(false, expanded)
        assertEquals(0, onAllNodesWithText("Font size").fetchSemanticsNodes().size)
        onNodeWithText("KJV").performClick()
        waitForIdle()
        onNodeWithText("Font size").assertExists()
    }

    @Test
    fun `a fixed section ignores clicks on its header`() = runComposeUiTest {
        var changes = 0
        setContent {
            MaterialTheme {
                SettingsSection("Display", onExpandedChange = { changes++ }) { Text("Auto-fit") }
            }
        }
        onNodeWithText("Display").performClick()
        waitForIdle()
        assertEquals(0, changes)
        onNodeWithText("Auto-fit").assertExists()
    }

    @Test
    fun `a setting row shows its label beside its control`() = runComposeUiTest {
        setContent { MaterialTheme { SettingRow("Opacity") { Text("75%") } } }
        onNodeWithText("Opacity").assertExists()
        onNodeWithText("75%").assertExists()
    }

    @Test
    fun `a top-aligned row drops its label onto the first control`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingRow(
                    "Languages",
                    width = 90.dp,
                    verticalAlignment = Alignment.Top,
                    labelTopPadding = SettingRowFirstControlOffset,
                ) { Text("English") }
            }
        }
        val label = onNodeWithText("Languages").fetchSemanticsNode().boundsInRoot
        val control = onNodeWithText("English").fetchSemanticsNode().boundsInRoot
        assertEquals(SettingRowFirstControlOffset.value * density.density, label.top - control.top, 1f)
    }

    @Test
    fun `clicking a switch row's label flips the switch`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                SettingSwitchRow("Loop", checked, { checked = it })
            }
        }
        onNode(isToggleable()).assertIsOff()
        onNodeWithText("Loop").performClick()
        waitForIdle()
        assertEquals(true, checked)
        onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun `a switch row may set its own label width`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) { SettingSwitchRow("Loop", true, {}, width = 200.dp) }
        }
        val label = onNodeWithText("Loop", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals((200 * density.density).toInt(), label.size.width)
    }
}
