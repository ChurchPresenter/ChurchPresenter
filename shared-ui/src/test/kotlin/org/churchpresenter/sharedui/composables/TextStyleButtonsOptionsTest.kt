package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TextStyleButtonsOptionsTest {

    @Test
    fun `a strikethrough button appears only when asked for and reports its toggle`() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    modifier = Modifier, buttonSize = 32.dp,
                    strikethrough = true, onStrikethroughChange = { changes += it },
                    showShadow = false, showUnderline = true,
                )
            }
        }
        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun `strikethrough and shadow sit side by side when both are on`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = true, italic = true, underline = true, shadow = true,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    strikethrough = false, onStrikethroughChange = {},
                )
            }
        }
        assertEquals(2, onAllNodesWithText("S").fetchSemanticsNodes().size)
    }

    @Test
    fun `underline and shadow can both be left out`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    showShadow = false, showUnderline = false,
                )
            }
        }
        onNodeWithText("B").assertExists()
        assertEquals(0, onAllNodesWithText("U").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("S").fetchSemanticsNodes().size)
    }
}
