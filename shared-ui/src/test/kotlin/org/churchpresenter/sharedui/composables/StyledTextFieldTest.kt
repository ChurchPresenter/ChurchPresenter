package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StyledTextFieldTest {

    @Test
    fun `a trailing icon is drawn after the text`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                StyledTextField(value = "12", onValueChange = {}, trailingIcon = { Text("px") })
            }
        }
        onNodeWithText("px").assertExists()
    }

    @Test
    fun `a multi-line field keeps every line it is given`() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                StyledTextField(value = text, onValueChange = { text = it }, singleLine = false, minLines = 3)
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("Line one\nLine two")
        waitForIdle()
        assertEquals("Line one\nLine two", text)
    }

    @Test
    fun `a multi-line field is taller than a single-line one`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    StyledTextField("One", {}, modifier = Modifier.testTag("single"))
                    StyledTextField(
                        "One\nTwo\nThree\nFour", {}, modifier = Modifier.testTag("multi"),
                        singleLine = false, maxLines = 4,
                    )
                }
            }
        }
        val single = onNodeWithTag("single").fetchSemanticsNode().size.height
        val multi = onNodeWithTag("multi").fetchSemanticsNode().size.height
        assertTrue(multi > single, "multi $multi should be taller than single $single")
    }

    @Test
    fun `a disabled field still shows its label`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                StyledTextField(value = "", onValueChange = {}, label = "Host", enabled = false, placeholder = "none")
            }
        }
        onNodeWithText("HOST").assertExists()
        onNodeWithText("none").assertExists()
    }
}
