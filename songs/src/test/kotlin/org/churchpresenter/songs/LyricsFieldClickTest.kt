@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Clicking into the song editor's lyrics after scrolling them keeps the box where it was and puts the
 * cursor on the clicked line, rather than throwing the box back to the top (#1043).
 */
class LyricsFieldClickTest {

    private val text = (1..80).joinToString("\n") { "Line $it" }

    private fun lyricsField(block: ComposeUiTest.(() -> TextFieldValue) -> Unit) = runComposeUiTest {
        var value by mutableStateOf(TextFieldValue(text))
        setContent {
            MaterialTheme {
                LyricsTextField(value = value, onValueChange = { value = it }, modifier = Modifier.size(400.dp, 300.dp))
            }
        }
        waitForIdle()
        block { value }
    }

    private fun ComposeUiTest.textTop(): Float = onNode(hasSetTextAction()).fetchSemanticsNode().positionInRoot.y

    private fun ComposeUiTest.wheel(by: Float) {
        onRoot().performMouseInput { moveTo(Offset(100f, 150f)); scroll(by) }
        waitForIdle()
    }

    private fun TextFieldValue.cursorLine(): Int = text.substring(0, selection.start).count { it == '\n' } + 1

    @Test
    fun `the click that focuses a scrolled lyrics box leaves it where it was`() = lyricsField { value ->
        wheel(800f)
        val scrolled = textTop()
        assertTrue(scrolled < -1000f, "the wheel scrolled the box: $scrolled")

        onRoot().performMouseInput { click(Offset(100f, 150f)) }
        waitForIdle()

        assertEquals(scrolled, textTop())
        assertTrue(value().cursorLine() > 60, "cursor on line ${value().cursorLine()}")
    }

    @Test
    fun `a second click after scrolling again stays put too`() = lyricsField { value ->
        onRoot().performMouseInput { click(Offset(100f, 150f)) }
        waitForIdle()
        val firstLine = value().cursorLine()
        wheel(800f)
        val scrolled = textTop()

        onRoot().performMouseInput { click(Offset(100f, 150f)) }
        waitForIdle()

        assertEquals(scrolled, textTop())
        assertTrue(value().cursorLine() > firstLine + 50, "cursor on line ${value().cursorLine()}")
    }
}
