@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.media.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.media.presenter.SubtitleOverlay
import org.churchpresenter.media.subtitles.SubtitleCue
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.screenshot.SCREENSHOT_ROOT
import kotlin.test.Test

/**
 * Video subtitles carrying a backdrop, on a full output, so a clip at the parent of the text shows.
 * The live-caption half of the same rendering is the app's `CaptionBackdropScreenshotTest`.
 */
class SubtitleBackdropScreenshotTest {

    private fun shootSubtitles(name: String, settings: MediaSettings) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(1280.dp, 720.dp)) { SubtitleOverlay(cue = CUE, mediaSettings = settings) }
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    @Test
    fun `subtitles on a plate`() = shootSubtitles("subtitle_backdrop", subtitles(LINE_PLATE))

    @Test
    fun `subtitles in a bordered box`() = shootSubtitles("subtitle_backdrop_border", subtitles(BORDER_BOX))

    /**
     * Bottom **Left**, which is where the subtitle card loses its own left edge.
     *
     * `MediaSettings.position` carries the alignment for subtitles rather than a separate field, so
     * this is one setting doing both jobs — the card moves and the text aligns with it.
     */
    @Test
    fun `subtitles bottom left in a bordered box`() =
        shootSubtitles("subtitle_border_left", subtitles(BORDER_BOX, Constants.BOTTOM_LEFT))

    @Test
    fun `subtitles bottom right in a bordered box`() =
        shootSubtitles("subtitle_border_right", subtitles(BORDER_BOX, Constants.BOTTOM_RIGHT))

    private fun subtitles(backdrop: TextBackdrop, position: String = Constants.BOTTOM_CENTER) = MediaSettings(
        backdrop = backdrop,
        position = position,
        // An opaque card, so the plate and the box read against something rather than over black.
        backgroundColor = "#101820",
        backgroundOpacity = 100,
    )

    private companion object {
        const val SECTION = "subtitleBackdrop"

        val CUE = SubtitleCue(startMs = 0, endMs = 5_000, text = "And he said unto them, go ye into all the world.")

        /** A band behind each line. Not near-black: the card it sits on is dark. */
        val LINE_PLATE = TextBackdrop(
            lineBackground = true,
            lineBackgroundColor = "#1B3A6B",
            lineBackgroundOpacity = 90,
        )

        /** A box around the block. No fill: with one it stops being a box of its own. */
        val BORDER_BOX = TextBackdrop(
            border = true,
            borderColor = "#FFD54F",
            borderWidth = 6,
            borderPadding = 18,
            borderRadius = 12,
        )
    }
}
