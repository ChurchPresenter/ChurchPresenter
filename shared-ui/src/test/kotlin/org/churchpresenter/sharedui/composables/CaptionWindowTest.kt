@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window [BottomAlignedText] shows of a caption that changes under it: the lines it keeps, the
 * room it is given, the text it will grow into, and how it moves when lines arrive or leave.
 *
 * Every word is `w<n>`, unique in its text, so it can be followed from frame to frame. Every wait
 * is on the virtual clock.
 */
class CaptionWindowTest {

    // Lines all one height, as the captions draw them
    private val style = TextStyle(
        color = Color.White,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
    )

    private fun words(from: Int, to: Int) = (from..to).joinToString(" ") { "w$it" }

    /** Where each word on screen starts, by its own text, hidden ones left out. */
    private fun ComposeUiTest.wordsOnScreen(): Map<String, Offset> {
        val found = mutableMapOf<String, Offset>()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: return@forEach
            val laidOut = layout.layoutInput.text
            val hidden = laidOut.spanStyles.filter { it.item.color == Color.Transparent }
            WORD.findAll(laidOut.text).filter { m -> hidden.none { m.range.first in it.start until it.end } }.forEach {
                found[it.value] = node.positionInRoot + layout.getBoundingBox(it.range.first).topLeft
            }
        }
        return found
    }

    private fun ComposeUiTest.shownText(): String =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .joinToString(" ") { it.text }

    private fun window(
        width: Int = 200,
        height: Int? = null,
        // Held by its bottom edge, as captions at the bottom of the page are
        heldByBottom: Boolean = false,
        body: ComposeUiTest.(set: (CaptionState) -> Unit) -> Unit,
        initial: CaptionState,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            val size = Modifier.width(width.dp).let { if (height == null) it else it.height(height.dp) }
            Box(size, contentAlignment = if (heldByBottom) Alignment.BottomStart else Alignment.TopStart) {
                BottomAlignedText(
                    text = AnnotatedString(state.text),
                    style = style.copy(textAlign = state.align),
                    maxLines = state.maxLines,
                    rollUpMillis = ROLL_MS,
                    keepFrom = state.keepFrom,
                    layoutText = state.layoutText?.let(::AnnotatedString),
                    slideWhenGrowing = state.slideWhenGrowing,
                    onSafeCut = state.onSafeCut,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        body { state = it }
    }

    private data class CaptionState(
        val text: String,
        val maxLines: Int = 3,
        val keepFrom: Int = 0,
        val layoutText: String? = null,
        val align: TextAlign = TextAlign.Left,
        val slideWhenGrowing: Boolean = false,
        val onSafeCut: ((Int) -> Unit)? = null,
    )

    /** One frame, then halfway through the slide, then settled: where [word] was at each. */
    private fun ComposeUiTest.slideOf(word: String, change: () -> Unit): Triple<Float, Float, Float> {
        val from = wordsOnScreen().getValue(word).y
        change()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(ROLL_MS / 2L)
        val mid = wordsOnScreen().getValue(word).y
        mainClock.advanceTimeBy(SETTLE_MS)
        return Triple(from, mid, wordsOnScreen().getValue(word).y)
    }

    private fun assertSlidUp(word: String, slide: Triple<Float, Float, Float>) {
        val (from, mid, to) = slide
        assertTrue(to < from - 0.5f, "$word moves up: $from -> $to")
        assertTrue(mid < from - 0.5f && mid > to + 0.5f, "$word is part-way up mid-slide: $from, $mid, $to")
    }

    @Test
    fun `text leaving the front as a line arrives below slides the rest up`() = window(
        initial = CaptionState(words(1, 30)),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            // Everything above the window's first line leaves as new words arrive: cut at a line
            // start, as the captions are
            val start = wordsOnScreen().keys.minOf { it.drop(1).toInt() }
            assertSlidUp("w30", slideOf("w30") { set(CaptionState(words(start, 36))) })
        },
    )

    @Test
    fun `a correction under the window's first line still slides it up when a line arrives`() = window(
        initial = CaptionState(words(1, 30)),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            // The newest line corrected as more arrives: the old window's text no longer runs on
            // unchanged, but its first line is still there to follow
            val corrected = words(1, 28) + " W29 w30 " + words(31, 36)
            assertSlidUp("w30", slideOf("w30") { set(CaptionState(corrected)) })
        },
    )

    @Test
    fun `text rewritten past recognition jumps rather than slides`() = window(
        initial = CaptionState(words(1, 30)),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            set(CaptionState(words(100, 130)))
            mainClock.advanceTimeByFrame()
            val first = wordsOnScreen()
            mainClock.advanceTimeBy(ROLL_MS / 2L)
            assertEquals(first, wordsOnScreen(), "nothing slides into a rewrite")
        },
    )

    @Test
    fun `a box still filling slides its lines up when held by its bottom`() = window(
        height = 200,
        heldByBottom = true,
        initial = CaptionState(words(1, 4), slideWhenGrowing = true),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            assertSlidUp("w1", slideOf("w1") { set(CaptionState(words(1, 12), slideWhenGrowing = true)) })
        },
    )

    @Test
    fun `a box still filling and held by its top leaves its lines where they are`() = window(
        initial = CaptionState(words(1, 4)),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            val (from, mid, to) = slideOf("w1") { set(CaptionState(words(1, 12))) }
            assertEquals(from, mid, 0.5f)
            assertEquals(from, to, 0.5f)
        },
    )

    @Test
    fun `the window starts no higher than the line holding keepFrom`() = window(
        initial = CaptionState(words(1, 12), maxLines = 5, keepFrom = words(1, 12).indexOf("w9")),
        body = { _ ->
            mainClock.advanceTimeBy(SETTLE_MS)
            val shown = wordsOnScreen()
            assertTrue("w9" in shown && "w12" in shown, "the kept words show: ${shown.keys}")
            assertTrue("w1" !in shown, "the lines wholly before them do not: ${shown.keys}")
        },
    )

    @Test
    fun `a part-typed word already sits on the line it ends on`() = window(
        initial = CaptionState(LONG, maxLines = 6, layoutText = LONG),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            val finished = wordsOnScreen()
            // Each word typed only a few letters in: none of it may sit anywhere its finished self does not
            for (cut in LONG.indices.filter { LONG[it] == 'w' }.map { it + 3 }) {
                set(CaptionState(LONG.substring(0, cut), maxLines = 6, layoutText = LONG))
                mainClock.advanceTimeByFrame()
                wordsOnScreen().forEach { (word, at) ->
                    assertEquals(finished.getValue(word).x, at.x, 0.5f, "$word keeps its place at $cut")
                }
            }
        },
    )

    @Test
    fun `a centred line being typed is placed at its finished width and reads only what is shown`() = window(
        initial = CaptionState("w1 w2 w3", layoutText = "w1 w2 w3", align = TextAlign.Center),
        body = { set ->
            mainClock.advanceTimeBy(SETTLE_MS)
            val finished = wordsOnScreen().getValue("w1").x
            set(CaptionState("w1", layoutText = "w1 w2 w3", align = TextAlign.Center))
            mainClock.advanceTimeByFrame()
            assertEquals(finished, wordsOnScreen().getValue("w1").x, 0.5f)
            assertEquals("w1", shownText().trim(), "the rest of the line is laid out, not read out")
        },
    )

    @Test
    fun `a box too short for its lines shows the newest whole lines that fit`() = window(
        // Three lines of 24 are 72; 60 holds two
        height = 60,
        initial = CaptionState(words(1, 30)),
        body = { _ ->
            mainClock.advanceTimeBy(SETTLE_MS)
            val tops = wordsOnScreen().values.map { it.y }.distinct()
            assertEquals(2, tops.size, "two whole lines: $tops")
            assertTrue(tops.all { it >= -0.5f && it + 24 <= 60.5f }, "each within the box: $tops")
            assertTrue("w30" in wordsOnScreen(), "the newest line is the one kept")
        },
    )

    @Test
    fun `the start of a line that will not be drawn again is handed back`() {
        val cuts = mutableListOf<Int>()
        window(
            initial = CaptionState(words(1, 60), onSafeCut = { cuts += it }),
            body = { _ -> mainClock.advanceTimeBy(SETTLE_MS) },
        )
        assertTrue(cuts.isNotEmpty() && cuts.all { it > 0 }, "a line start above the window: $cuts")
        val text = words(1, 60)
        assertTrue(cuts.all { text[it - 1] == ' ' }, "each one where a line began: $cuts")
    }

    private companion object {
        val WORD = Regex("w\\d+")

        // Long words, so a part-typed one fits on a line its finished self does not
        const val LONG = "w1alphabetical w2bravissimo w3chartreuse w4dandelion w5effervescent w6flabbergast"

        const val ROLL_MS = 300
        const val SETTLE_MS = 2_000L
    }
}
