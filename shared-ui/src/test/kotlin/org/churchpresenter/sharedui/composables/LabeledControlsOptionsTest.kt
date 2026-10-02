package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LabeledControlsOptionsTest {

    private val style = TextStyle(fontSize = 15.sp)

    @Test
    fun `a checkbox takes its own style, color, spacing and control modifier`() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                LabeledCheckbox(
                    checked = true, onCheckedChange = { changes += it }, label = "Show reference",
                    modifier = Modifier, enabled = true, style = style, color = Color.Red, supporting = null,
                    controlModifier = Modifier.testTag("box"), spacing = 12.dp, controlAtEnd = false,
                )
            }
        }
        onNodeWithTag("box", useUnmergedTree = true).assertExists()
        onNodeWithText("Show reference").performClick()
        waitForIdle()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun `a radio button takes its own style, color, spacing and control modifier`() = runComposeUiTest {
        var clicks = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                LabeledRadioButton(
                    selected = false, onClick = { clicks++ }, label = "Verse only",
                    modifier = Modifier, enabled = true, style = style, color = Color.Blue,
                    supporting = "One per slide",
                    controlModifier = Modifier.testTag("radio"), spacing = 4.dp, controlAtEnd = true,
                )
            }
        }
        onNodeWithTag("radio", useUnmergedTree = true).assertExists()
        onNodeWithText("Verse only").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a switch takes its own style, color, spacing and control modifier`() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                LabeledSwitch(
                    checked = false, onCheckedChange = { changes += it }, label = "Loop",
                    modifier = Modifier, enabled = true, style = style, color = Color.Green, supporting = null,
                    controlModifier = Modifier.testTag("switch"), spacing = 6.dp, controlAtEnd = false,
                )
            }
        }
        onNodeWithTag("switch", useUnmergedTree = true).assertExists()
        onNodeWithText("Loop").performClick()
        waitForIdle()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `a labeled control shows its label beside its content`() = runComposeUiTest {
        setContent { MaterialTheme { LabeledControl("Size") { Text("Large") } } }
        onNodeWithText("Size").assertExists()
        onNodeWithText("Large").assertExists()
    }
}
