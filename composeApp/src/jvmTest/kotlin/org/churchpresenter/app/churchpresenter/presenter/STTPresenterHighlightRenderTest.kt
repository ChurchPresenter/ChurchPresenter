package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.app.churchpresenter.viewmodel.HighlightedWord
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class STTPresenterHighlightRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun segment(text: String, id: Int = 1) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private fun runStt(
        settings: STTSettings,
        transcription: List<STTSegment> = emptyList(),
        inProgressText: String = "",
        highlightedWords: List<HighlightedWord> = emptyList(),
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(screen) {
                STTPresenter(
                    segments = transcription,
                    inProgressText = inProgressText,
                    translationSegments = emptyList(),
                    inProgressTranslation = "",
                    highlightedWords = highlightedWords,
                    sttSettings = settings,
                )
            }
        }
        body()
    }

    @Test
    fun `in-progress text is appended alongside a completed segment`() = runStt(
        STTSettings(displayMode = "transcribe", showInProgress = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
        inProgressText = "be with you",
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
        onNodeWithText("be with you", substring = true).assertExists()
    }

    @Test
    fun `in-progress text alone still shows before any segment completes`() = runStt(
        STTSettings(displayMode = "transcribe", showInProgress = true, dripFeedEnabled = false),
        inProgressText = "Blessed are the peacemakers",
    ) {
        onNodeWithText("Blessed are the peacemakers", substring = true).assertExists()
    }

    @Test
    fun `blank in-progress text adds nothing to a completed segment`() = runStt(
        STTSettings(displayMode = "transcribe", showInProgress = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
        inProgressText = "   ",
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
    }

    @Test
    fun `a literal highlighted word does not disturb the caption`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Amazing grace how sweet the sound")),
        highlightedWords = listOf(HighlightedWord(word = "grace", color = "#FF0000")),
    ) {
        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
    }

    @Test
    fun `a regex highlighted word matches multiple words in the caption`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace to you")),
        highlightedWords = listOf(HighlightedWord(word = "grace|peace", color = "#00FF00", isRegex = true)),
    ) {
        onNodeWithText("Grace and peace to you", substring = true).assertExists()
    }

    @Test
    fun `a case-sensitive highlighted word still renders the caption`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
        highlightedWords = listOf(HighlightedWord(word = "Grace", color = "#FF0000", caseSensitive = true)),
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
    }

    @Test
    fun `a blank highlighted word is skipped without breaking the others`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
        highlightedWords = listOf(
            HighlightedWord(word = "   ", color = "#FF0000"),
            HighlightedWord(word = "peace", color = "#00FF00"),
        ),
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
    }

    @Test
    fun `an invalid regex highlight is caught and the caption still renders`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
        highlightedWords = listOf(HighlightedWord(word = "(unclosed", color = "#FF0000", isRegex = true)),
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
    }

    @Test
    fun `word highlighting enabled with no highlighted words leaves the caption untouched`() = runStt(
        STTSettings(displayMode = "transcribe", showWordHighlighting = true, dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace")),
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
    }
}
