package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BottomAlignedText] draws only the lines inside its window: clipping the whole text at a line
 * edge left ink from the line above showing (descenders, the outline, a shadow hang below their
 * own line). What is drawn is the text from the window's first line on, and nothing else is there
 * to be read out.
 */
@OptIn(ExperimentalTestApi::class)
class CaptionTextRenderTest {

    private val style = TextStyle(color = Color.White, fontSize = 20.sp, lineHeight = 24.sp)

    // Twelve short words, about one per line in a box this narrow
    private val words = (1..12).joinToString(" ") { "word$it" }

    private fun ComposeUiTest.show(maxLines: Int, paged: Boolean = false) {
        setContent {
            Box(Modifier.width(90.dp)) {
                BottomAlignedText(AnnotatedString(words), style, maxLines = maxLines, paged = paged)
            }
        }
    }

    /** Every text a reader would be told about, the measuring copies being hidden from it. */
    private fun ComposeUiTest.readable(): List<String> =
        onAllNodesWithAnyText().map { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        }

    private fun ComposeUiTest.onAllNodesWithAnyText() =
        onAllNodes(hasText("word", substring = true)).fetchSemanticsNodes()

    @Test
    fun `only the last lines are drawn, from the start of the window's first line`() = runComposeUiTest {
        show(maxLines = 2)
        val shown = readable()
        assertEquals(1, shown.size, "one copy of the caption, not the measuring ones as well")
        val text = shown.single()
        assertTrue(words.endsWith(text), "the drawn text is a tail of the caption: $text")
        assertFalse(text.startsWith("word1 "), "the lines above the window are not drawn at all: $text")
        assertTrue(text.startsWith("word"), "it starts at a line's start, never mid-word: $text")
    }

    @Test
    fun `with no line limit everything is drawn`() = runComposeUiTest {
        show(maxLines = 0)
        assertEquals(listOf(words), readable())
    }

    @Test
    fun `text that fits is drawn whole`() = runComposeUiTest {
        setContent {
            Box(Modifier.width(400.dp)) { BottomAlignedText(AnnotatedString("Grace and peace"), style, maxLines = 3) }
        }
        assertEquals(listOf("Grace and peace"), onAllNodes(hasText("Grace", substring = true)).fetchSemanticsNodes()
            .map { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } })
    }

    @Test
    fun `pop-on draws the page the newest line is on, starting at the top of the window`() = runComposeUiTest {
        show(maxLines = 5, paged = true)
        val paged = readable().single()
        assertTrue(words.endsWith(paged), paged)
        assertTrue(paged.length < words.length, "an earlier page has been cleared: $paged")
        val windowTop = onRoot().fetchSemanticsNode().boundsInRoot.top
        val textTop = onAllNodes(hasText(paged)).fetchSemanticsNodes().single().boundsInRoot.top
        assertEquals(windowTop, textTop, 0.5f)
    }
}
