package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The labelled dropdown used across the settings tabs.
 *
 * Its own behaviour beyond opening a menu is [rememberDropdownWidthFor], which sizes the field from
 * the longest option so the menu does not resize as the selection changes — clamped at both ends,
 * because one very long translated option would otherwise push the field off the tab.
 */
@OptIn(ExperimentalTestApi::class)
class DropdownSettingsFieldTest {

    @Test
    fun `picking an option reports it`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                DropdownSettingsField(
                    value = "Fade",
                    options = listOf("Fade", "Slide", "None"),
                    onValueChange = { picked = it },
                    label = "Transition",
                )
            }
        }
        onNodeWithText("Fade").performClick()
        waitForIdle()
        onNodeWithText("Slide").performClick()
        waitForIdle()
        assertEquals("Slide", picked)
    }

    @Test
    fun `the label and current value are both shown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSettingsField("None", listOf("None", "Fade"), {}, label = "Transition")
            }
        }
        onNodeWithText("TRANSITION").assertIsDisplayed()  // the field upper-cases its label
        onNodeWithText("None").assertIsDisplayed()
    }

    @Test
    fun `the width grows with the longest option and stays inside its bounds`() = runComposeUiTest {
        var short = 0.dp
        var long = 0.dp
        var huge = 0.dp
        setContent {
            MaterialTheme {
                short = rememberDropdownWidthFor(listOf("a"))
                long = rememberDropdownWidthFor(listOf("a", "a considerably longer option label"))
                huge = rememberDropdownWidthFor(listOf("x".repeat(400)))
            }
        }
        waitForIdle()
        assertEquals(160.dp, short, "a tiny option list still gets the minimum width")
        assertTrue(long >= short, "a longer option cannot make the field narrower")
        assertTrue(huge <= 280.dp, "an absurd option must be clamped, not allowed to run off the tab")
    }

    @Test
    fun `an empty option list still renders`() = runComposeUiTest {
        setContent { MaterialTheme { DropdownSettingsField("", emptyList(), {}, label = "Empty") } }
        onNodeWithText("EMPTY").assertIsDisplayed()
    }
}
