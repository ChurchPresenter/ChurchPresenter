package org.churchpresenter.theme.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The B / I / U square: to a screen reader a checkbox named by its tooltip, on or off; a click is the
 * toggle, and it draws in either state. The letter is drawn but not read.
 */
@OptIn(ExperimentalTestApi::class)
class TextStyleToggleButtonTest {

    @Test
    fun `it is a checkbox named by its tooltip that a click turns on and off`() = runComposeUiTest {
        var bold by mutableStateOf(false)
        var clicks = 0
        setContent {
            MaterialTheme {
                TextStyleToggleButton(
                    label = "B", tooltip = "Bold", isActive = bold, fontWeight = FontWeight.Bold,
                    onClick = { clicks++; bold = !bold },
                )
            }
        }
        val key = onNodeWithContentDescription("Bold")
        key.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        key.assertIsOff().performClick()
        assertTrue(bold)
        key.assertIsOn().performClick()
        assertEquals(2, clicks)
        assertTrue(!bold)
        key.assertIsOff()
    }

    @Test
    fun `the letter is drawn but not read`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleToggleButton(label = "B", tooltip = "Bold", isActive = false) { }
            }
        }
        onNodeWithText("B").assertDoesNotExist()
        onNodeWithText("B", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an italic, an underline and a tooltip surface render`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleToggleButton(
                    label = "I", tooltip = "Italic", isActive = true, fontStyle = FontStyle.Italic,
                ) { }
                TextStyleToggleButton(
                    label = "U", tooltip = "Underline", isActive = false, textDecoration = TextDecoration.Underline,
                ) { }
                ControlTooltip("Shown under the pointer")
            }
        }
        onNodeWithContentDescription("Italic").assertIsOn()
        onNodeWithContentDescription("Underline").assertIsOff()
        onNodeWithText("Shown under the pointer").assertExists()
    }

    @Test
    fun `a bigger button scales its letter with it`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleToggleButton(
                    label = "S", tooltip = "Shadow", isActive = false, fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal, textDecoration = null, buttonSize = 42.dp,
                    onClick = { },
                )
            }
        }
        onNodeWithContentDescription("Shadow").assertExists()
    }
}
