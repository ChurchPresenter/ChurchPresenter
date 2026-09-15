package org.churchpresenter.theme.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The B / I / U square: its letter is the label, a click is the toggle, and it draws in either state. */
@OptIn(ExperimentalTestApi::class)
class TextStyleToggleButtonTest {

    @Test
    fun `the letter is shown and a click calls back, in both states`() = runComposeUiTest {
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
        onNodeWithText("B").assertExists().performClick()
        assertTrue(bold)
        onNodeWithText("B").assertExists("still drawn once active").performClick()
        assertEquals(2, clicks)
        assertTrue(!bold)
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
        onNodeWithText("I").assertExists()
        onNodeWithText("U").assertExists()
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
        onNodeWithText("S").assertExists()
    }
}
