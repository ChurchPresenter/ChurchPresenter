package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import kotlin.test.Test

/**
 * The drip feed as the room sees it — the letter-by-letter caption reveal, driven by the
 * "Speed (ms/letter)" setting on the STT tab.
 *
 * These cover the reported failure: at any speed slower than roughly the default the reveal used to
 * restart on every arriving segment, so the pace on screen was set by the STT server's segment
 * cadence and 100, 120 and 140 ms/letter were indistinguishable. The knob has to be the thing that
 * decides the pace, and a segment landing mid-reveal must not snap the sentence before it to full.
 *
 * Every wait here is on the test's VIRTUAL clock (`autoAdvance = false` + `advanceTimeBy`), so the
 * timing is exact and the tests cost no wall-clock time. The reveal only runs on text that arrives
 * after the first composition — an output opened mid-service deliberately inherits its backlog
 * whole rather than re-typing it — so each case starts from an empty caption and then speaks.
 */
@OptIn(ExperimentalTestApi::class)
class STTPresenterDripFeedRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private val spoken = "Blessed are the peacemakers"   // 27 characters
    private val alsoSpoken = "Rejoice and be glad"

    private fun segment(text: String, id: Int) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private fun dripping(speedMs: Int) =
        STTSettings(displayMode = "transcribe", dripFeedEnabled = true, dripFeedSpeed = speedMs)

    /**
     * Renders the presenter over a caption that starts empty, and hands the body a setter for the
     * segments and for the settings so a test can speak, and can change the speed mid-reveal.
     */
    private fun runDrip(
        initialSettings: STTSettings,
        body: ComposeUiTest.(speak: (List<STTSegment>) -> Unit, retune: (STTSettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var segments by mutableStateOf(emptyList<STTSegment>())
        var settings by mutableStateOf(initialSettings)
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
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
        body({ segments = it }, { settings = it })
    }

    @Test
    fun `a fast speed has more of the sentence on screen than a slow one`() {
        runDrip(dripping(speedMs = 25)) { speak, _ ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeBy(300)   // 12 letters at 25ms
            onNodeWithText("Blessed are", substring = true)
                .assertExists("at 25 ms/letter 300 ms must have typed well past the first word")
        }
        runDrip(dripping(speedMs = 200)) { speak, _ ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeBy(300)   // 1 letter at 200ms
            onNodeWithText("Blessed", substring = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun `a segment arriving mid-reveal does not snap the sentence before it to full`() {
        runDrip(dripping(speedMs = 100)) { speak, _ ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeBy(350)   // 3 letters in — nowhere near the whole sentence
            onNodeWithText(spoken, substring = true).assertDoesNotExist()

            // The bug: a new segment reset the cursor, which dumped the sentence before it on
            // screen whole — so the pace came from the server's cadence, not from the speed setting.
            speak(listOf(segment(spoken, id = 1), segment(alsoSpoken, id = 2)))
            mainClock.advanceTimeByFrame()
            onNodeWithText(spoken, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `the reveal keeps going until the whole caption has been read out`() {
        runDrip(dripping(speedMs = 100)) { speak, _ ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeBy(350)
            speak(listOf(segment(spoken, id = 1), segment(alsoSpoken, id = 2)))
            mainClock.advanceTimeBy(10_000)

            onNodeWithText(spoken, substring = true).assertExists()
            onNodeWithText(alsoSpoken, substring = true)
                .assertExists("the cursor must carry on across the boundary, not stall at the first segment")
        }
    }

    @Test
    fun `changing the speed applies to the reveal already in flight`() {
        runDrip(dripping(speedMs = 1_000)) { speak, retune ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeBy(1_100)   // 1 letter at 1000ms
            onNodeWithText("Blessed", substring = true).assertDoesNotExist()

            retune(dripping(speedMs = 1))
            mainClock.advanceTimeBy(100)
            onNodeWithText(spoken, substring = true)
                .assertExists("a speed change must not wait for the next segment to take effect")
        }
    }

    @Test
    fun `drip feed switched off puts the caption up immediately`() {
        runDrip(STTSettings(displayMode = "transcribe", dripFeedEnabled = false)) { speak, _ ->
            speak(listOf(segment(spoken, id = 1)))
            mainClock.advanceTimeByFrame()
            onNodeWithText(spoken, substring = true).assertExists()
        }
    }
}
