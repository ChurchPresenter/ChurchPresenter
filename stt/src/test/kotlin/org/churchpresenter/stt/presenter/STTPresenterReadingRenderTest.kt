package org.churchpresenter.stt.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_ROLL_UP
import org.churchpresenter.settings.CAPTION_TRANSCRIPT_BOX
import org.churchpresenter.settings.CAPTION_TRANSLATION_BOX
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.stt.STTSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The captions' reading aids and their room on the page: words or whole segments held back at a
 * pace, a caption fading out after the speaker goes quiet, a band that touches the screen's edge or
 * keeps its margin, and boxed parts placed by the page's alignment.
 *
 * Every wait is on the VIRTUAL clock (`autoAdvance = false`), so a pace costs no wall-clock time.
 */
@OptIn(ExperimentalTestApi::class)
class STTPresenterReadingRenderTest {

    private val tenWords = "one two three four five six seven eight nine ten"

    private fun said(text: String, id: Int = 1, seconds: Double = 4.0) =
        STTSegment(id = id, timestamp = "", text = text, start = 10.0, end = 10.0 + seconds, completed = true)

    /** The caption text on screen, every text node joined. */
    private fun ComposeUiTest.onScreen(): String =
        onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null })
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .joinToString(" ")

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().boundsInRoot

    /** Speaks into an empty caption, so the reveal starts from nothing rather than a backlog. */
    private fun speaking(settings: STTSettings, body: ComposeUiTest.(speak: (STTSegment) -> Unit) -> Unit) =
        runComposeUiTest {
            var segments by mutableStateOf(emptyList<STTSegment>())
            mainClock.autoAdvance = false
            setContent {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    STTPresenter(
                        segments = segments,
                        inProgressText = "",
                        translationSegments = emptyList(),
                        inProgressTranslation = "",
                        highlightedWords = emptyList(),
                        sttSettings = settings,
                    )
                }
            }
            body { segments = segments + it }
        }

    private fun showing(
        settings: STTSettings,
        transcription: List<STTSegment> = listOf(said("Grace and peace")),
        translation: List<STTSegment> = emptyList(),
        inProgress: String = "",
        inProgressTranslation: String = "",
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        body: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
        setContent {
            Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                STTPresenter(
                    segments = transcription,
                    inProgressText = inProgress,
                    translationSegments = translation,
                    inProgressTranslation = inProgressTranslation,
                    highlightedWords = emptyList(),
                    sttSettings = settings,
                    outputRole = outputRole,
                )
            }
        }
        body()
    }

    // ── Pace ────────────────────────────────────────────────────────────────────

    @Test
    fun `matching the speaker without the drip feed brings the words a word at a time`() {
        val settings = STTSettings(displayMode = "transcribe", dripFeedEnabled = false, matchSpeakerPace = true)
        speaking(settings) { speak ->
            speak(said(tenWords))
            mainClock.advanceTimeBy(STEP_MS)
            val early = onScreen()
            assertTrue(early.length < tenWords.length, "the words are not all up at once: '$early'")
            assertTrue(early.isEmpty() || tenWords.startsWith(early.trim()), "whole words, in order: '$early'")
            mainClock.advanceTimeBy(LONG_MS)
            assertEquals(tenWords, onScreen().trim(), "every word arrives in the end")
        }
    }

    @Test
    fun `pop-on under a reading-speed limit holds each segment back as a whole`() {
        val settings = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            maxSegments = 0,
            maxLines = 0,
            reading = CaptionReading(style = CAPTION_STYLE_POP_ON, readingSpeedLimit = true, readingSpeedCps = 10),
        )
        speaking(settings) { speak ->
            speak(said("Grace and peace.", id = 1))
            speak(said("Let us pray.", id = 2))
            mainClock.advanceTimeBy(STEP_MS)
            val early = onScreen()
            assertTrue("Let us pray" !in early, "the second segment waits its turn: '$early'")
            mainClock.advanceTimeBy(LONG_MS)
            val late = onScreen()
            assertTrue("Grace and peace." in late && "Let us pray." in late, "both arrive whole: '$late'")
        }
    }

    // ── Clearing after silence ──────────────────────────────────────────────────

    @Test
    fun `a caption fades off the screen once the speaker has been quiet long enough`() {
        val settings = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            reading = CaptionReading(clearAfterSilence = true, clearAfterSeconds = 1, clearFadeMillis = 100),
        )
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                Box(Modifier.size(400.dp, 200.dp)) {
                    STTPresenter(
                        segments = listOf(said("Grace")),
                        inProgressText = "",
                        translationSegments = emptyList(),
                        inProgressTranslation = "",
                        highlightedWords = emptyList(),
                        sttSettings = settings,
                    )
                }
            }
            mainClock.advanceTimeByFrame()
            assertTrue(inkedPixels() > 0, "the caption is on screen while it is fresh")
            mainClock.advanceTimeBy(LONG_MS)
            assertEquals(0, inkedPixels(), "after the silence the caption has faded out")
        }
    }

    private fun ComposeUiTest.inkedPixels(): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            if (c.alpha > 0.5f && c.red > 0.5f) count++
        }
        return count
    }

    @Test
    fun `roll-up is only timed when it is both switched on and the style`() {
        assertEquals(0, CaptionReading(rollUp = false, style = CAPTION_STYLE_ROLL_UP).rollUpMillisOrOff())
        assertEquals(0, CaptionReading(rollUp = true, style = CAPTION_STYLE_POP_ON).rollUpMillisOrOff())
        assertEquals(300, CaptionReading(rollUp = true, rollUpMillis = 300).rollUpMillisOrOff())
        assertEquals(0, CaptionReading(rollUp = true, rollUpMillis = -5).rollUpMillisOrOff())
    }

    // ── A band's margins ────────────────────────────────────────────────────────

    @Test
    fun `a band at the top touches the edge, or keeps its margin when told not to`() {
        val band = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            boxShape = CAPTION_BOX_BAND,
            position = Constants.TOP,
            marginTop = 200,
        )
        var touching = 0f
        showing(band) { touching = boundsOf("Grace").top }
        var kept = 0f
        showing(band.copy(bandTouchesEdge = false)) { kept = boundsOf("Grace").top }
        assertTrue(touching < 200f, "a band touching the edge ignores the top margin: $touching")
        assertTrue(kept >= 200f, "a band kept off the edge keeps the top margin: $kept")
    }

    @Test
    fun `a band at the bottom touches the edge, or keeps its margin when told not to`() {
        val band = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            boxShape = CAPTION_BOX_BAND,
            position = Constants.BOTTOM_RIGHT,
            marginBottom = 200,
        )
        var touching = 0f
        showing(band) { touching = boundsOf("Grace").bottom }
        var kept = 0f
        showing(band.copy(bandTouchesEdge = false)) { kept = boundsOf("Grace").bottom }
        assertTrue(touching > HEIGHT - 200f, "a band touching the edge ignores the bottom margin: $touching")
        assertTrue(kept <= HEIGHT - 200f, "a band kept off the edge keeps the bottom margin: $kept")
    }

    @Test
    fun `a band in the middle keeps both its margins`() {
        val band = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            boxShape = CAPTION_BOX_BAND,
            position = Constants.CENTER,
        )
        showing(band) {
            val drawn = boundsOf("Grace")
            assertTrue(drawn.top > 0f && drawn.bottom < HEIGHT, "a centred band stays clear of both edges")
        }
    }

    // ── Boxes ───────────────────────────────────────────────────────────────────

    private val leftQuarter =
        TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 25f, heightPercent = 25f)

    @Test
    fun `a boxed transcript on a left-aligned page is drawn in its box, the translation in the card`() {
        val settings = STTSettings(
            displayMode = "both",
            dripFeedEnabled = false,
            position = Constants.BOTTOM_LEFT,
            textBoxes = mapOf(textBoxKey(CAPTION_TRANSCRIPT_BOX, lowerThird = false) to leftQuarter),
        )
        showing(settings, translation = listOf(said("Gracia y paz"))) {
            val transcript = boundsOf("Grace and peace")
            assertTrue(transcript.right <= WIDTH * 0.25f + 1 && transcript.bottom <= HEIGHT * 0.25f + 1)
            assertTrue(boundsOf("Gracia y paz").top > HEIGHT * 0.25f, "the unboxed translation keeps the card")
        }
    }

    @Test
    fun `a boxed translation on a right-aligned page with nothing said draws nothing`() {
        val settings = STTSettings(
            displayMode = "translate",
            dripFeedEnabled = false,
            position = Constants.BOTTOM_RIGHT,
            textBoxes = mapOf(textBoxKey(CAPTION_TRANSLATION_BOX, lowerThird = false) to leftQuarter),
        )
        showing(settings, transcription = emptyList()) {
            assertEquals("", onScreen().trim(), "an empty part is given neither its box nor the card")
        }
    }

    @Test
    fun `both parts boxed on a right-aligned page are each drawn in their own box`() {
        val lower = TextBox(enabled = true, xPercent = 50f, yPercent = 75f, widthPercent = 50f, heightPercent = 25f)
        val settings = STTSettings(
            displayMode = "both",
            dripFeedEnabled = false,
            position = Constants.TOP_RIGHT,
            textBoxes = mapOf(
                textBoxKey(CAPTION_TRANSCRIPT_BOX, lowerThird = false) to leftQuarter,
                textBoxKey(CAPTION_TRANSLATION_BOX, lowerThird = false) to lower,
            ),
        )
        showing(settings, translation = listOf(said("Gracia y paz"))) {
            assertTrue(boundsOf("Grace and peace").bottom <= HEIGHT * 0.25f + 1)
            assertTrue(boundsOf("Gracia y paz").top >= HEIGHT * 0.75f - 1)
        }
    }

    // ── Look ────────────────────────────────────────────────────────────────────

    @Test
    fun `bold, shadowed captions with wider word spacing still put every word up`() {
        val settings = STTSettings(
            displayMode = "transcribe",
            dripFeedEnabled = false,
            bold = true,
            shadow = true,
            wordSpacing = 20,
        )
        showing(settings) { assertTrue("Grace" in onScreen() && "peace" in onScreen()) }
    }

    @Test
    fun `the words still being said follow the finished ones on both sides`() {
        val settings = STTSettings(
            displayMode = "both",
            layout = LAYOUT_INTERLEAVED,
            dripFeedEnabled = false,
            showInProgress = true,
            showTranslationInProgress = true,
            maxLines = 0,
            reading = CaptionReading(blankLineBetween = true),
        )
        showing(
            settings,
            translation = listOf(said("Gracia y paz")),
            inProgress = "Let us",
            inProgressTranslation = "Oremos",
        ) {
            val text = onScreen()
            assertTrue(text.indexOf("Grace and peace") < text.indexOf("Let us"), text)
            assertTrue("Gracia y paz" in text && "Oremos" in text, text)
            assertTrue("\n\n" in text, "a blank line parts each pair")
        }
    }

    @Test
    fun `the translation keeps the transcript's weight and slant unless it asks for its own`() {
        val base = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light, fontStyle = FontStyle.Normal)
        val bold = translationTextStyle(base, STTSettings(translationBold = true, translationItalic = true))
        assertEquals(FontWeight.Bold, bold.fontWeight)
        assertEquals(FontStyle.Italic, bold.fontStyle)
        assertEquals(base.fontSize, bold.fontSize, "no size of its own keeps the transcript's")
        val sized = translationTextStyle(base, STTSettings(translationFontSize = 30))
        assertEquals(FontWeight.Light, sized.fontWeight)
        assertEquals(FontStyle.Normal, sized.fontStyle)
        assertEquals(Color.Unspecified, sized.color)
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val STEP_MS = 50L
        const val LONG_MS = 10_000L
    }
}
