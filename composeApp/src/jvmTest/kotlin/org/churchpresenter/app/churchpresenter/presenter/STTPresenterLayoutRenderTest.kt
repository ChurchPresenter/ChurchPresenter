package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.STTSettings
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class STTPresenterLayoutRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun segment(text: String, id: Int) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private fun runStt(
        settings: STTSettings,
        transcription: List<STTSegment> = emptyList(),
        translation: List<STTSegment> = emptyList(),
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(screen) {
                STTPresenter(
                    segments = transcription,
                    inProgressText = "",
                    translationSegments = translation,
                    inProgressTranslation = "",
                    highlightedWords = emptyList(),
                    sttSettings = settings,
                )
            }
        }
        body()
    }

    @Test
    fun `side-by-side layout places the transcription left of the translation`() = runStt(
        STTSettings(displayMode = "both", layout = "side_by_side", dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace", id = 1)),
        translation = listOf(segment("Gnade und Frieden", id = 2)),
    ) {
        val left = onNodeWithText("Grace and peace", substring = true).fetchSemanticsNode().boundsInRoot
        val right = onNodeWithText("Gnade und Frieden", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            left.left < right.left,
            "side-by-side must place transcription left of translation, was $left vs $right"
        )
    }

    @Test
    fun `side-by-side-inverse layout places the translation left of the transcription`() = runStt(
        STTSettings(displayMode = "both", layout = "side_by_side_inverse", dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace", id = 1)),
        translation = listOf(segment("Gnade und Frieden", id = 2)),
    ) {
        val transcriptionBounds = onNodeWithText("Grace and peace", substring = true).fetchSemanticsNode().boundsInRoot
        val translationBounds = onNodeWithText("Gnade und Frieden", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            translationBounds.left < transcriptionBounds.left,
            "the inverse layout must place the translation first, was $translationBounds vs $transcriptionBounds"
        )
    }

    @Test
    fun `stacked-inverse layout places the translation above the transcription`() = runStt(
        STTSettings(displayMode = "both", layout = "stacked_inverse", dripFeedEnabled = false),
        transcription = listOf(segment("Grace and peace", id = 1)),
        translation = listOf(segment("Gnade und Frieden", id = 2)),
    ) {
        val transcriptionBounds = onNodeWithText("Grace and peace", substring = true).fetchSemanticsNode().boundsInRoot
        val translationBounds = onNodeWithText("Gnade und Frieden", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            translationBounds.top < transcriptionBounds.top,
            "the inverse layout must place the translation above the transcription, was $translationBounds vs " +
                "$transcriptionBounds"
        )
    }

    @Test
    fun `a maxLines of zero still renders the full caption`() = runStt(
        STTSettings(displayMode = "transcribe", maxLines = 0, dripFeedEnabled = false),
        transcription = listOf(segment("Blessed are the peacemakers", id = 1)),
    ) {
        onNodeWithText("Blessed are the peacemakers", substring = true).assertExists()
    }

    @Test
    fun `translate mode falls back to the transcription before any translation has arrived`() = runStt(
        STTSettings(displayMode = "translate", dripFeedEnabled = false),
        transcription = listOf(segment("Peace be with you", id = 1)),
    ) {
        onNodeWithText(
            "Peace be with you",
            substring = true
        ).assertExists("translate mode with no translation yet must fall back to the transcription")
    }

    @Test
    fun `transcribe mode falls back to the translation if it arrives before the transcription`() = runStt(
        STTSettings(displayMode = "transcribe", dripFeedEnabled = false),
        translation = listOf(segment("Und mit deinem Geiste", id = 2)),
    ) {
        onNodeWithText(
            "Und mit deinem Geiste",
            substring = true
        ).assertExists("a translation arriving before the transcription must still be shown")
    }
}
