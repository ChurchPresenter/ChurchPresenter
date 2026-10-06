@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorZoneStyle
import kotlin.test.Test
import kotlin.test.assertTrue

class StageZoneContentRenderTest {

    private fun data(
        current: String = "",
        next: String = "",
        notes: String = "",
        nextChords: List<String> = emptyList(),
    ) =
        ZoneRenderData(
            currentText = current, chordLines = emptyList(), nextChordLines = nextChords, songInfo = null,
            nextText = next, currentImageBitmap = null, displayedSlide = null, clockText = "10:30",
            timerText = "Coffee after the service", presenterNotes = notes, activeScene = null,
            displayedQuestion = null, qaSettings = QASettings(), displayedDictionaryEntry = null,
            dictionarySettings = DictionarySettings(),
        )

    private fun ComposeUiTest.shows(text: String) {
        waitForIdle()
        val nodes = onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "\"$text\" is on screen")
    }

    private fun textChangesUnder(sm: StageMonitorSettings) = runComposeUiTest {
        val shown = mutableStateOf(data(current = "First verse"))
        setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                ZoneContent(
                    sm, StageMonitorContentType.BIBLE, StageMonitorZoneStyle(), shown.value, mediaViewModel = null,
                )
            }
        }
        shows("First verse")
        shown.value = data(current = "Second verse")
        mainClock.advanceTimeBy(2_000)
        shows("Second verse")
    }

    @Test
    fun `a zone crossfades to its new text`() = textChangesUnder(StageMonitorSettings(crossfade = true))

    @Test
    fun `a zone with only a fade in brings its new text in`() =
        textChangesUnder(StageMonitorSettings(fadeIn = true, fadeOut = false))

    @Test
    fun `a zone with only a fade out takes its old text away`() =
        textChangesUnder(StageMonitorSettings(fadeIn = false, fadeOut = true))

    @Test
    fun `a zone with every fade off cuts straight to its new text`() =
        textChangesUnder(StageMonitorSettings(fadeIn = false, fadeOut = false, crossfade = false))

    @Test
    fun `each kind of zone draws what it is given, and the unplumbed ones draw nothing`() {
        val cases = listOf(
            StageMonitorContentType.CLOCK to "10:30",
            StageMonitorContentType.ANNOUNCEMENT_TEXT to "Coffee after the service",
            StageMonitorContentType.PRESENTATION_NOTES to "Pause for the video",
            StageMonitorContentType.NEXT to "Verse two",
        )
        cases.forEach { (type, expected) ->
            runComposeUiTest {
                setContent {
                    Box(Modifier.size(400.dp, 200.dp)) {
                        ZoneContent(
                            StageMonitorSettings(), type, StageMonitorZoneStyle(),
                            data(
                                next = "Verse two",
                                notes = "Pause for the video",
                                nextChords = listOf("[G]Verse two"),
                            ),
                            mediaViewModel = null,
                        )
                    }
                }
                shows(expected)
            }
        }
        listOf(
            StageMonitorContentType.LOWER_THIRD, StageMonitorContentType.WEB, StageMonitorContentType.STT,
            StageMonitorContentType.PICTURES, StageMonitorContentType.PRESENTATION, StageMonitorContentType.MEDIA,
            StageMonitorContentType.QA, StageMonitorContentType.DICTIONARY, StageMonitorContentType.CANVAS,
        ).forEach { type ->
            runComposeUiTest {
                setContent {
                    Box(Modifier.size(400.dp, 200.dp)) {
                        ZoneContent(
                            StageMonitorSettings(), type, StageMonitorZoneStyle(), data(), mediaViewModel = null,
                        )
                    }
                }
                waitForIdle()
                assertTrue(onAllNodesWithText("10:30").fetchSemanticsNodes().isEmpty(), "$type draws no clock")
            }
        }
    }
}
