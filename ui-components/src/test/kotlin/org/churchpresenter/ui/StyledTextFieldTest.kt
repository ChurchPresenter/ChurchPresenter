package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The house text field the dialogs are built from.
 *
 * The placeholder is the part worth pinning: it has to disappear the moment there is a value, or a
 * field showing both reads as if it already contains the hint text.
 */
@OptIn(ExperimentalTestApi::class)
class StyledTextFieldTest {

    @Test
    fun `typing reaches the caller`() = runComposeUiTest {
        var text = ""
        setContent { MaterialTheme { StyledTextField(value = text, onValueChange = { text = it }) } }
        onNodeWithText("").performTextInput("Sunday")
        assertEquals("Sunday", text)
    }

    @Test
    fun `replacing the whole value reports the replacement`() = runComposeUiTest {
        var text = "old"
        setContent { MaterialTheme { StyledTextField(value = text, onValueChange = { text = it }) } }
        onNodeWithText("old").performTextReplacement("new")
        assertEquals("new", text)
    }

    @Test
    fun `the placeholder shows only while the field is empty`() = runComposeUiTest {
        setContent { MaterialTheme { StyledTextField(value = "", onValueChange = {}, placeholder = "Service name") } }
        onNodeWithText("Service name").assertIsDisplayed()
    }

    @Test
    fun `a field with a value does not also show its placeholder`() = runComposeUiTest {
        setContent {
            MaterialTheme { StyledTextField(value = "Evening", onValueChange = {}, placeholder = "Service name") }
        }
        onNodeWithText("Evening").assertIsDisplayed()
        assertEquals(
            0,
            onAllNodesWithText("Service name").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "the placeholder must go once there is a value",
        )
    }

    @Test
    fun `a disabled field reports itself disabled`() = runComposeUiTest {
        setContent { MaterialTheme { StyledTextField(value = "locked", onValueChange = {}, enabled = false) } }
        onNodeWithText("locked").assertIsNotEnabled()
    }

    @Test
    fun `a label and a trailing icon are both drawn`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StyledTextField(
                    value = "v",
                    onValueChange = {},
                    label = "Name",
                    trailingIcon = { Text("icon") },
                )
            }
        }
        onNodeWithText("NAME").assertIsDisplayed()  // the field upper-cases its label
        onNodeWithText("icon").assertIsDisplayed()
    }
}
