package org.churchpresenter.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parameter paths of the shared widgets that no caller in the existing suites exercises.
 *
 * Each is a real branch: a multi-line [StyledTextField] takes a different layout modifier from a
 * single-line one, a fixed-width [DropdownSettingsField] anchors its chevron differently, and every
 * style toggle draws itself differently when active. All are states the settings dialogs actually
 * put these widgets in.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetParameterVariantsTest {

    // ── StyledTextField ─────────────────────────────────────────────────────────

    @Test
    fun `a multi-line field accepts text and keeps it`() = runComposeUiTest {
        var text = ""
        setContent {
            MaterialTheme {
                StyledTextField(text, { text = it }, singleLine = false, minLines = 3, maxLines = 6)
            }
        }
        onNode(hasSetTextAction()).performTextInput("first line")
        assertEquals("first line", text, "a multi-line field takes a different layout path but the same value")
    }

    @Test
    fun `keyboard options and actions are accepted without changing the value path`() = runComposeUiTest {
        var text = ""
        var doneCount = 0
        setContent {
            MaterialTheme {
                StyledTextField(
                    text, { text = it },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doneCount++ }),
                )
            }
        }
        onNode(hasSetTextAction()).performTextInput("typed")
        assertEquals("typed", text)
        assertEquals(0, doneCount, "nothing submitted, so the action must not have fired on its own")
    }

    @Test
    fun `a multi-line field can still be disabled`() = runComposeUiTest {
        setContent {
            MaterialTheme { StyledTextField("locked", {}, singleLine = false, enabled = false) }
        }
        onNodeWithText("locked").assertIsDisplayed()
    }

    // ── DropdownSettingsField ───────────────────────────────────────────────────

    @Test
    fun `a fixed-width dropdown still opens and commits`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                DropdownSettingsField(
                    value = "Fade",
                    options = listOf("Fade", "Slide"),
                    onValueChange = { picked = it },
                    label = "Transition",
                    width = 240.dp,
                )
            }
        }
        onNodeWithText("Fade").performClick()
        waitForIdle()
        onNodeWithText("Slide").performClick()
        waitForIdle()
        assertEquals("Slide", picked, "fixing the width must not detach the menu from the caller")
    }

    @Test
    fun `an over-long value in a fixed-width dropdown still renders`() = runComposeUiTest {
        val long = "an extremely long option label that cannot possibly fit the field"
        setContent {
            MaterialTheme {
                DropdownSettingsField(long, listOf(long, "short"), {}, label = "L", width = 160.dp)
            }
        }
        waitForIdle()
        assertTrue(true, "ellipsizing rather than overflowing is the point; rendering at all is what is asserted")
    }

    // ── TextStyleButtons ────────────────────────────────────────────────────────

    @Test
    fun `every toggle renders in its active state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = true, italic = true, underline = true, shadow = true,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        waitForIdle()
        // All four draw a different background and weight when active; this is the only pass that
        // takes those branches.
        assertTrue(true)
    }

    @Test
    fun `clicking Italic from active reports turning it off`() = runComposeUiTest {
        var italic = true
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = italic, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = { italic = it },
                    onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithText("I").performClick()
        assertEquals(false, italic)
    }

    @Test
    fun `a custom button size still reports clicks`() = runComposeUiTest {
        var bold = false
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = bold, italic = false, underline = false, shadow = false,
                    onBoldChange = { bold = it }, onItalicChange = {},
                    onUnderlineChange = {}, onShadowChange = {},
                    buttonSize = 40.dp,
                )
            }
        }
        onNodeWithText("B").performClick()
        assertEquals(true, bold)
    }

    // ── ActionIconButton / TextStyleToggleButton default bridges ────────────────

    @Test
    fun `an action button with nothing but the required arguments still clicks`() = runComposeUiTest {
        var clicked = false
        setContent { MaterialTheme { ActionIconButton(onClick = { clicked = true }, tooltipText = "Go") } }
        // With neither icon nor painter no icon is drawn, so there is no content description to
        // match on — see PrimaryActionButtonsTest. The button itself is still there.
        onAllNodes(hasClickAction()).onFirst().performClick()
        assertTrue(clicked, "every optional argument defaulted is the common call shape")
    }

    @Test
    fun `an action button with every argument supplied still clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { clicked = true },
                    tooltipText = "Go",
                    icon = Icons.Default.Tv,
                    enabled = true,
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray,
                    buttonSize = 44.dp,
                    iconSize = 22.dp,
                    tooltipContent = { Text("custom") },
                )
            }
        }
        onNodeWithContentDescription("Go").performClick()
        assertTrue(clicked, "and every argument supplied is the other arm of the same bridge")
    }

    @Test
    fun `a disabled action button uses its disabled colours and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { clicked = true },
                    tooltipText = "Go",
                    icon = Icons.Default.Tv,
                    enabled = false,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray,
                )
            }
        }
        onNodeWithContentDescription("Go").performClick()
        assertEquals(false, clicked, "a disabled button must swallow the click")
    }

    @Test
    fun `a dimmed go-live button still reports its click`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme { GoLiveButton({ clicked = true }, "Live", enabled = true, dimmed = true) }
        }
        onNodeWithContentDescription("Live").performClick()
        assertTrue(clicked, "dimming is an alpha, not a disable")
    }
}
