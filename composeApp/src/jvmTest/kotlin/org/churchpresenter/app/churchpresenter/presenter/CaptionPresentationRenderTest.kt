package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.stt.STTSegment
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where the caption output puts its text for each presentation, box shape and two-language layout. */
@OptIn(ExperimentalTestApi::class)
class CaptionPresentationRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun segment(text: String, id: Int) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private val english = listOf(segment("Grace and peace", 1), segment("Let us pray", 2))
    private val spanish = listOf(segment("Gracia y paz", 1), segment("Oremos", 2))

    private fun runStt(settings: STTSettings, body: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            Box(screen) {
                STTPresenter(
                    segments = english,
                    inProgressText = "",
                    translationSegments = spanish,
                    inProgressTranslation = "",
                    highlightedWords = emptyList(),
                    sttSettings = settings.copy(dripFeedEnabled = false),
                )
            }
        }
        body()
    }

    private fun ComposeUiTest.boundsOf(text: String) =
        onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot

    /** The window the test draws in, which the screen-sized box is fitted to. */
    private fun ComposeUiTest.window() = onRoot().fetchSemanticsNode().boundsInRoot

    @Test
    fun `a full-width band touches the bottom edge, or keeps the bottom margin when asked`() {
        runStt(STTSettings(boxShape = CAPTION_BOX_BAND, backgroundColor = "#000000", backgroundOpacity = 80)) {
            val gap = window().bottom - boundsOf("Grace and peace").bottom
            // The band's own padding is all that sits between the text and the screen's bottom
            assertTrue(gap < 40f, "flush band: text ends ${gap}px above the edge")
        }
        runStt(STTSettings(boxShape = CAPTION_BOX_BAND, bandTouchesEdge = false, marginBottom = 120)) {
            assertTrue(window().bottom - boundsOf("Grace and peace").bottom > 120f, "a floating band keeps the margin")
        }
    }

    @Test
    fun `the band's side margins inset its text instead of narrowing the band`() = runStt(
        STTSettings(boxShape = CAPTION_BOX_BAND, marginLeft = 300, marginRight = 300, horizontalAlignment = "Left"),
    ) {
        val text = boundsOf("Grace and peace")
        assertTrue(text.left >= 300f && text.right <= window().right - 300f, "text inside the side margins: $text")
    }

    @Test
    fun `interleaved puts each line's translation straight after it`() = runStt(
        STTSettings(displayMode = "both", layout = "interleaved", maxLines = 6),
    ) {
        val node = onNodeWithText("Gracia y paz", substring = true).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals("Grace and peace\nGracia y paz\nLet us pray\nOremos", text)
    }

    @Test
    fun `each language in its own box sits in its own place, one above the other`() = runStt(
        STTSettings(displayMode = "both", layout = "stacked", separateLanguageBoxes = true),
    ) {
        val own = boundsOf("Grace and peace")
        val other = boundsOf("Gracia y paz")
        assertTrue(own.bottom <= other.top, "transcript $own above translation $other")
    }

    @Test
    fun `a ticker keeps each language on one line and enters from the right`() = runStt(
        STTSettings(
            displayMode = "both", layout = "side_by_side", reading = CaptionReading(style = CAPTION_STYLE_TICKER),
        ),
    ) {
        waitForIdle()
        val pieces = onAllNodes(hasText("Grace and peace", substring = true)).fetchSemanticsNodes()
        assertTrue(pieces.isNotEmpty())
        // Under the test clock the crawl holds still, so new words wait at the right edge
        // Waiting off the right edge, so clipped: its place is read, not what is visible of it
        val rightEdge = window().right
        pieces.forEach { piece ->
            assertTrue(piece.positionInRoot.x >= rightEdge - 120f, "enters at the right: ${piece.positionInRoot}")
        }
        val own = pieces.first().positionInRoot.y
        val other = onAllNodes(hasText("Gracia y paz", substring = true)).fetchSemanticsNodes().first().positionInRoot.y
        assertTrue(own != other, "two tickers stack even side by side: $own vs $other")
    }
}
