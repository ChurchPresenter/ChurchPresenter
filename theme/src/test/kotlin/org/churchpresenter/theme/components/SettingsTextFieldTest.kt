package org.churchpresenter.theme.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The text field every settings form is typed into, in its two forms: captioned — the label in
 * small capitals above the value, in a box of fixed height — and plain, a bare bordered line.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsTextFieldTest {

    @Test
    fun `a captioned field shows its label in capitals and hands typing on`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme { SettingsTextField(value = value, onValueChange = { value = it }, label = "File name") }
        }
        onNodeWithText("FILE NAME").assertExists("the caption is upper-cased")
        onNode(hasSetTextAction()).performTextInput("sunday")
        assertEquals("sunday", value)
    }

    @Test
    fun `a plain field has no caption and still takes text`() = runComposeUiTest {
        var value by mutableStateOf("a")
        setContent { MaterialTheme { SettingsTextField(value = value, onValueChange = { value = it }) } }
        onNodeWithText("A").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextReplacement("b")
        assertEquals("b", value)
    }

    @Test
    fun `the placeholder shows only while the field is empty, in both forms`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = value, onValueChange = { value = it }, label = "Hex", placeholder = { Text("#RRGGBB") },
                )
                SettingsTextField(value = value, onValueChange = { value = it }, placeholder = { Text("plain hint") })
            }
        }
        onNodeWithText("#RRGGBB").assertExists()
        onNodeWithText("plain hint").assertExists()
        value = "#123456"
        onNodeWithText("#RRGGBB").assertDoesNotExist()
        onNodeWithText("plain hint").assertDoesNotExist()
    }

    @Test
    fun `trailing icon, supporting text and the disabled state are rendered`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "x", onValueChange = { }, label = "With extras", enabled = false, isError = true,
                    trailingIcon = { Text("swatch") }, supportingText = { Text("must be six digits") },
                )
                SettingsTextField(value = "y", onValueChange = { }, trailingIcon = { Text("plain swatch") })
            }
        }
        onNodeWithText("swatch").assertExists()
        onNodeWithText("plain swatch").assertExists()
        onNodeWithText("must be six digits").assertExists()
        onNodeWithText("x").assertIsNotEnabled()
        onNodeWithText("y").assertIsEnabled()
    }

    @Test
    fun `a captioned single-line field is a fixed box and a multi-line one grows with its lines`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "one", onValueChange = { }, label = "Single", fillWidth = true,
                    modifier = Modifier.testTag("single"),
                )
                SettingsTextField(
                    value = "one\ntwo\nthree\nfour", onValueChange = { }, label = "Multi", singleLine = false,
                    fillWidth = true, containerColor = Color.Red, modifier = Modifier.testTag("multi"),
                )
            }
        }
        onNodeWithTag("single").assertHeightIsEqualTo(42.dp)
        val multi = onNodeWithTag("multi").fetchSemanticsNode().size.height
        val single = onNodeWithTag("single").fetchSemanticsNode().size.height
        assert(multi > single) { "four lines need more than the one-line box, got $multi vs $single" }
    }

    @Test
    fun `a plain multi-line field takes several lines`() = runComposeUiTest {
        var value by mutableStateOf("one\ntwo")
        setContent {
            MaterialTheme { SettingsTextField(value = value, onValueChange = { value = it }, singleLine = false) }
        }
        onNode(hasSetTextAction()).performTextReplacement("one\ntwo\nthree")
        assertEquals("one\ntwo\nthree", value)
    }
}
