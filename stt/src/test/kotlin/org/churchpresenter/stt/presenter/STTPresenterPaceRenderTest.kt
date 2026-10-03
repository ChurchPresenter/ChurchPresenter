package org.churchpresenter.stt.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.stt.STTSegment
import kotlin.test.Test
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.RSVP_FLASH_PHRASE
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Caption pacing as the room sees it, on the test's virtual clock: typing that follows the speaker,
 * and RSVP's flashes -- one at a time, the first at once, never past their words a minute, and never
 * skipping a word to catch up.
 *
 * Every wait is on the VIRTUAL clock (`autoAdvance = false`), so the timing is exact and costs no
 * wall-clock time. Each case starts from an empty caption and then speaks, since an output opened
 * mid-service inherits its backlog whole rather than revealing it.
 */
@OptIn(ExperimentalTestApi::class)
class STTPresenterPaceRenderTest {

    private val sentence = "Blessed are the peacemakers, for they shall be called"
    private val tenWords = "one two three four five six seven eight nine ten"

    private fun said(text: String, seconds: Double) =
        STTSegment(id = 1, timestamp = "", text = text, start = 10.0, end = 10.0 + seconds, completed = true)

    /** The caption text on screen, every text node joined. */
    private fun ComposeUiTest.onScreen(): String =
        onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null })
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .joinToString(" ")

    private fun showing(settings: STTSettings, body: ComposeUiTest.(speak: (STTSegment) -> Unit) -> Unit) =
        runComposeUiTest {
            var segments by mutableStateOf(emptyList<STTSegment>())
            mainClock.autoAdvance = false
            setContent {
                Box(Modifier.size(1920.dp, 1080.dp)) {
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

    /** Every flash shown, in order, until [last] is up or [limitMs] of virtual time has passed. */
    private fun ComposeUiTest.flashesUntil(last: String, limitMs: Long = 10_000): List<String> {
        val flashes = mutableListOf<String>()
        var elapsed = 0L
        while (elapsed < limitMs) {
            val now = onScreen()
            if (now.isNotBlank() && flashes.lastOrNull() != now) flashes += now
            if (now.endsWith(last)) break
            mainClock.advanceTimeBy(STEP_MS)
            elapsed += STEP_MS
        }
        return flashes
    }

    private val typing = STTSettings(displayMode = "transcribe", dripFeedEnabled = true, dripFeedSpeed = 25)
    private val rsvp = STTSettings(
        displayMode = "transcribe",
        dripFeedEnabled = false,
        reading = CaptionReading(style = CAPTION_STYLE_RSVP, rsvpWpm = 300),
    )

    @Test
    fun `matching the speaker, a fast speaker's words are typed out sooner than a slow one's`() {
        var fastDone = false
        var slowDone = true
        showing(typing.copy(matchSpeakerPace = true)) { speak ->
            speak(said(sentence, seconds = 2.5))
            mainClock.advanceTimeBy(2_500)
            fastDone = onScreen().endsWith("called")
        }
        showing(typing.copy(matchSpeakerPace = true)) { speak ->
            speak(said(sentence, seconds = 8.0))
            mainClock.advanceTimeBy(2_500)
            slowDone = onScreen().endsWith("called")
        }
        assertTrue(fastDone, "the fast speaker's sentence should be typed out within the time it was said")
        assertTrue(!slowDone, "the slow speaker's sentence should still be typing out")
    }

    @Test
    fun `without timings to measure, typing keeps its fixed speed`() {
        showing(typing.copy(matchSpeakerPace = true)) { speak ->
            speak(said(sentence, seconds = 0.0))
            mainClock.advanceTimeBy(1_500)   // 53 letters at 25 ms
            assertTrue(onScreen().endsWith("called"), "a server with no timings must not stall the typing")
        }
    }

    @Test
    fun `RSVP flashes one word at a time, the first at once`() {
        showing(rsvp) { speak ->
            speak(said(tenWords, seconds = 4.0))
            mainClock.advanceTimeBy(100)   // half of one word's 200 ms at 300 a minute
            assertEquals("one", onScreen(), "the first word goes up as soon as it is said, alone")
            assertEquals(tenWords.split(' '), flashesUntil("ten"))
        }
    }

    @Test
    fun `RSVP never flashes past its words a minute, and falls behind rather than skip`() {
        showing(rsvp.copy(matchSpeakerPace = true)) { speak ->
            speak(said("warm up " + "w ".repeat(22).trim(), seconds = 3.0))   // a fast talker, measured
            mainClock.advanceTimeBy(30_000)
            speak(said(tenWords, seconds = 1.0).copy(id = 2, start = 13.0, end = 14.0))
            val started = mainClock.currentTime
            // The warm-up's last flash is still up when the sentence lands
            val flashes = flashesUntil("ten").dropWhile { it == "w" }
            val took = mainClock.currentTime - started
            assertEquals(tenWords.split(' '), flashes, "every word is flashed, in order, none skipped")
            assertTrue(took >= 9 * 200, "ten words at 300 a minute take 1.8 s at the least, took $took ms")
        }
    }

    @Test
    fun `RSVP can flash a natural phrase at a time`() {
        showing(rsvp.copy(reading = rsvp.reading.copy(rsvpWordsPerFlash = RSVP_FLASH_PHRASE))) { speak ->
            speak(said(sentence, seconds = 4.0))
            assertEquals(
                listOf("Blessed are the peacemakers,", "for they shall", "be called"),
                flashesUntil("called"),
            )
        }
    }

    private companion object {
        const val STEP_MS = 16L
    }
}
