package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bold/Italic/Underline/Shadow toggle row shared by the text-style editors.
 *
 * The tooltip on each button uses `TooltipArea`'s real hover delay — advanced deterministically via
 * [ComposeUiTest.mainClock][androidx.compose.ui.test.MainTestClock] rather than skipped, the same
 * approach [TooltipIconButtonTest] documents for the same underlying mechanism.
 */
@OptIn(ExperimentalTestApi::class)
class TextStyleButtonsTest {

    @Test
    fun `all four style labels render`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithText("B").assertExists()
        onNodeWithText("I").assertExists()
        onNodeWithText("U").assertExists()
        onNodeWithText("S").assertExists()
    }

    @Test
    fun `clicking Bold reports the toggled value`() = runComposeUiTest {
        var bold = false
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = bold, italic = false, underline = false, shadow = false,
                    onBoldChange = { bold = it }, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithText("B").performClick()
        assertTrue(bold, "clicking Bold while off must report true")
    }

    @Test
    fun `clicking an active button reports turning it off`() = runComposeUiTest {
        var italic = true
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = italic, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = { italic = it }, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithText("I").performClick()
        assertEquals(false, italic, "clicking Italic while on must report false")
    }

    @Test
    fun `clicking Underline and Shadow report their own toggled values independently`() = runComposeUiTest {
        var underline = false
        var shadow = false
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = underline, shadow = shadow,
                    onBoldChange = {}, onItalicChange = {},
                    onUnderlineChange = { underline = it }, onShadowChange = { shadow = it },
                )
            }
        }
        onNodeWithText("U").performClick()
        onNodeWithText("S").performClick()
        assertTrue(underline)
        assertTrue(shadow)
    }

    @Test
    fun `hovering over a button shows its tooltip text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithText("Bold", substring = true).assertDoesNotExist()

        onAllNodesWithText("B").onFirst().performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Bold").assertExists("hovering the Bold button must show its tooltip")
    }
}
