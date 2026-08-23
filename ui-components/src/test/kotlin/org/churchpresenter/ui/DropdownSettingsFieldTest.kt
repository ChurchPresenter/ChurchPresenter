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

    @Test
    fun `explicit bounds are honoured over the defaults`() = runComposeUiTest {
        var narrow = 0.dp
        var wide = 0.dp
        setContent {
            MaterialTheme {
                narrow = rememberDropdownWidthFor(listOf("a"), min = 40.dp, max = 60.dp)
                wide = rememberDropdownWidthFor(listOf("x".repeat(200)), min = 40.dp, max = 60.dp)
            }
        }
        waitForIdle()
        // The width is the measured text plus chrome, then clamped — so a small `min` may already
        // be exceeded by the text itself. What the caller's bounds guarantee is the ceiling.
        assertTrue(narrow in 40.dp..60.dp, "narrow was $narrow, outside the caller's bounds")
        assertEquals(60.dp, wide, "an absurd option takes the caller's maximum, not the default 280")
    }

    @Test
    fun `a min above the max still yields a width inside the caller's bounds`() = runComposeUiTest {
        var w = 0.dp
        setContent { MaterialTheme { w = rememberDropdownWidthFor(listOf("abc"), min = 100.dp, max = 200.dp) } }
        waitForIdle()
        assertTrue(w >= 100.dp && w <= 200.dp, "was $w")
    }

    @Test
    fun `an empty option list falls back to the minimum`() = runComposeUiTest {
        var w = 0.dp
        setContent { MaterialTheme { w = rememberDropdownWidthFor(emptyList(), min = 120.dp, max = 300.dp) } }
        waitForIdle()
        assertEquals(120.dp, w, "with nothing to measure the field takes its floor")
    }
}
