@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertTrue

class CaptionRollUpTest {

    private val style = TextStyle(color = Color.White, fontSize = 20.sp, lineHeight = 24.sp)

    private fun words(n: Int) = (1..n).joinToString(" ") { "word$it" }

    @Test
    fun `new lines slide the old ones up, and rewritten text jumps without a slide`() = runComposeUiTest {
        var text by mutableStateOf(words(4))
        setContent {
            Box(Modifier.width(90.dp)) {
                BottomAlignedText(AnnotatedString(text), style, maxLines = 2, rollUpMillis = 300)
            }
        }
        waitForIdle()
        text = words(6)
        waitForIdle()
        text = words(8)
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val shown = onAllNodes(hasText("word", substring = true)).fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
        assertTrue(shown.any { it.endsWith("word8") }, "the newest line is on screen once the slide ends: $shown")
        text = words(3)
        waitForIdle()
        val after = onAllNodes(hasText("word", substring = true)).fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
        assertTrue(after.any { it.endsWith("word3") }, "rewritten text shows at once: $after")
    }

    @Test
    fun `text trimmed while its lines are still sliding stops the slide`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var text by mutableStateOf(words(4))
        setContent {
            Box(Modifier.width(90.dp)) {
                BottomAlignedText(AnnotatedString(text), style, maxLines = 2, rollUpMillis = 1_000)
            }
        }
        mainClock.advanceTimeByFrame()
        text = words(6)
        mainClock.advanceTimeByFrame()
        text = words(8)
        mainClock.advanceTimeByFrame()
        text = words(5)
        mainClock.advanceTimeBy(2_000)
        val shown = onAllNodes(hasText("word", substring = true)).fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
        assertTrue(shown.any { it.endsWith("word5") }, "the trimmed text is what shows: $shown")
    }
}
