@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.stt.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.stt.presenter.STTPresenter
import org.churchpresenter.stt.STTSegment
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.SCREENSHOT_ROOT

/**
 * The ways captions can be put on screen: pop-on, a full-width band (flush and floating), the two
 * languages interleaved or each in its own box, dimmed sentence lines, and the line window that
 * draws nothing of the line above it. A ticker is left out: under the test clock it holds still with
 * its words waiting off the right edge, so its picture would be empty.
 */
class CaptionPresentationScreenshotTest {

    private val screen = Modifier.size(1280.dp, 720.dp).background(Color(0xFF2B4A6B))

    private fun shoot(name: String, settings: STTSettings, translated: Boolean = false) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    STTPresenter(
                        segments = ENGLISH,
                        inProgressText = "",
                        translationSegments = if (translated) SPANISH else emptyList(),
                        inProgressTranslation = "",
                        highlightedWords = emptyList(),
                        sttSettings = settings.copy(dripFeedEnabled = false),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    private val card = STTSettings(fontSize = 34, backgroundColor = "#000000", backgroundOpacity = 70)

    @Test
    fun `pop-on starts a block at the top of its window`() =
        shoot("pop_on", card.copy(maxLines = 3, reading = CaptionReading(style = CAPTION_STYLE_POP_ON)))

    @Test
    fun `a band flush with the bottom edge`() = shoot("band_flush", card.copy(boxShape = CAPTION_BOX_BAND))

    @Test
    fun `a band held off the edge by the margin`() =
        shoot("band_floating", card.copy(boxShape = CAPTION_BOX_BAND, bandTouchesEdge = false, marginBottom = 80))

    @Test
    fun `the languages interleaved, translation in its own look`() = shoot(
        "interleaved",
        card.copy(
            displayMode = "both", layout = "interleaved", maxLines = 6,
            translationTextColor = "#FFD966", translationItalic = true,
        ),
        translated = true,
    )

    @Test
    fun `each language in its own box`() = shoot(
        "separate_boxes",
        card.copy(displayMode = "both", layout = "stacked", separateLanguageBoxes = true, maxLines = 2),
        translated = true,
    )

    @Test
    fun `a line per sentence, older ones dimmed`() = shoot(
        "sentences_dimmed",
        card.copy(
            maxLines = 4,
            reading = CaptionReading(lineBreaks = CAPTION_BREAK_SENTENCE, dimOlderLines = true, dimStepPercent = 25),
        ),
    )

    /** The window shows its two lines and nothing of the outlined, shadowed line above them. */
    @Test
    fun `a two-line window with an outline and shadow`() = shoot(
        "window_outline_shadow",
        card.copy(maxLines = 2, lineSpacing = 100, shadow = true, outline = TextOutline(enabled = true, width = 4)),
    )

    private companion object {
        const val SECTION = "captionPresentation"

        val ENGLISH = listOf(
            "In the beginning was the Word, and the Word was with God, and the Word was God.",
            "The same was in the beginning with God.",
            "All things were made by him; and without him was not any thing made that was made.",
            "In him was life; and the life was the light of men.",
        ).mapIndexed { i, text -> STTSegment(i, "", text, 0.0, 0.0, true) }

        val SPANISH = listOf(
            "En el principio era el Verbo, y el Verbo era con Dios, y el Verbo era Dios.",
            "Este era en el principio con Dios.",
            "Todas las cosas por él fueron hechas.",
            "En él estaba la vida, y la vida era la luz de los hombres.",
        ).mapIndexed { i, text -> STTSegment(i, "", text, 0.0, 0.0, true) }
    }
}
