package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.STTSettings
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import kotlin.test.Test

/**
 * The live captions the room reads during a service.
 *
 * The STT presenter turns a list of transcription (and optionally translation) segments into the
 * text on the lower band, filtered by the display mode: "transcribe" shows only the spoken language,
 * "translate" only the translation, "both" stacks them. Getting the mode filter wrong is the failure
 * that matters — a deaf visitor reading a screen that shows the wrong language, or shows nothing.
 *
 * These render the presenter in a real composition and assert on the text that lands on screen, so
 * the whole path — segment joining, whitespace normalisation, the mode branch — runs on the way.
 *
 * Drip-feed (letter-by-letter reveal) is DISABLED in every case: with it on the caption fills in over
 * time and a text assertion would race the animation. Disabled, the full caption is present at once,
 * so every wait is on the text itself, never on a delay.
 */
@OptIn(ExperimentalTestApi::class)
class STTPresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    /** Drip-feed off so the caption is fully present immediately — see the class doc. */
    private fun settings(mode: String) = STTSettings(displayMode = mode, dripFeedEnabled = false)

    private fun segment(text: String, id: Int = 1) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private fun runStt(
        settings: STTSettings,
        transcription: List<STTSegment> = emptyList(),
        translation: List<STTSegment> = emptyList(),
        body: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
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
    fun `transcribe mode puts the spoken text on screen`() = runStt(
        settings("transcribe"),
        transcription = listOf(segment("Peace be with you")),
    ) {
        onNodeWithText("Peace be with you", substring = true).assertExists("the caption must show the spoken words")
    }

    @Test
    fun `both mode shows the transcription and the translation together`() = runStt(
        settings("both"),
        transcription = listOf(segment("Grace and peace")),
        translation = listOf(segment("Gnade und Frieden", id = 2)),
    ) {
        onNodeWithText("Grace and peace", substring = true).assertExists()
        onNodeWithText("Gnade und Frieden", substring = true).assertExists("both languages must appear in both mode")
    }

    @Test
    fun `translate mode shows the translation and withholds the transcription`() = runStt(
        settings("translate"),
        transcription = listOf(segment("this should be hidden")),
        translation = listOf(segment("dies ist sichtbar", id = 2)),
    ) {
        onNodeWithText("dies ist sichtbar", substring = true).assertExists("translate mode must show the translation")
        onNodeWithText("this should be hidden", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `consecutive segments are joined into one caption`() = runStt(
        settings("transcribe"),
        transcription = listOf(segment("Blessed are", id = 1), segment("the peacemakers", id = 2)),
    ) {
        onNodeWithText("Blessed are", substring = true).assertExists()
        onNodeWithText("the peacemakers", substring = true).assertExists("later segments must not drop off the caption")
    }
}
