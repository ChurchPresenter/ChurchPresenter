@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.SubtitleOverlay
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCue
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * Live captions and video subtitles carrying a backdrop, which nothing photographed before.
 *
 * These two share one rendering — `BottomAlignedText` — and it was the **worst** of the surfaces the
 * backdrop-clipping audit turned up. It clips at the direct parent of the text and hands the text the
 * whole clip, so any Left or Right position lost that edge outright; and because it reports exactly
 * the text's height and places it at `y = 0`, even *centred* text lost its top and bottom. The
 * clipped branch is the default path on both — `STTSettings.maxLines` is 3 and
 * `MediaSettings.maxLines` is 2 — so this was not an edge case, it was the shipped configuration.
 *
 * Nothing caught it because the three backdrop suites all mount the painter on a bare `Box` with no
 * clipping ancestor, which is precisely the condition the bug needs to be absent. Hence a suite that
 * shoots the real presenters.
 *
 * Both alignments *and* the centred case are here on purpose: centred is the one that reads as fine
 * on the other surfaces, and is the one that proves the vertical half of the fix.
 */
class CaptionBackdropScreenshotTest {

    private val screen = Modifier.size(1280.dp, 720.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    // ── Live captions ───────────────────────────────────────────────────────────────────────────

    private fun shootCaptions(name: String, settings: STTSettings) = shoot(name) {
        STTPresenter(
            segments = SEGMENTS,
            inProgressText = "",
            translationSegments = emptyList(),
            inProgressTranslation = "",
            highlightedWords = emptyList(),
            sttSettings = settings,
        )
    }

    @Test
    fun `captions on a plate`() = shootCaptions("stt_backdrop", captions(LINE_PLATE))

    @Test
    fun `captions in a bordered box`() = shootCaptions("stt_backdrop_border", captions(BORDER_BOX))

    /**
     * Left is the alignment that lost its edge outright, the clip being the text's own parent.
     *
     * Driven through `position`, not `horizontalAlignment`: `STTPresenter` reads the alignment off
     * the position string (`position.contains("Left")`) and **nothing in the app reads
     * `STTSettings.horizontalAlignment` at all**. Setting that field here would have produced a
     * centred picture under a name promising a left-aligned one.
     */
    @Test
    fun `captions bottom left in a bordered box`() =
        shootCaptions("stt_border_left", captions(BORDER_BOX, Constants.BOTTOM_LEFT))

    @Test
    fun `captions bottom right in a bordered box`() =
        shootCaptions("stt_border_right", captions(BORDER_BOX, Constants.BOTTOM_RIGHT))

    @Test
    fun `captions stroked`() = shootCaptions(
        "stt_outline",
        STTSettings(outline = GLYPH_STROKE, backgroundColor = "#101820", backgroundOpacity = 100),
    )

    // ── Video subtitles ─────────────────────────────────────────────────────────────────────────

    private fun shootSubtitles(name: String, settings: MediaSettings) = shoot(name) {
        SubtitleOverlay(cue = CUE, mediaSettings = settings)
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

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun captions(backdrop: TextBackdrop, position: String = Constants.BOTTOM_CENTER) = STTSettings(
        backdrop = backdrop,
        position = position,
        // An opaque card, so the plate and the box read against something rather than over black.
        backgroundColor = "#101820",
        backgroundOpacity = 100,
    )

    private fun subtitles(backdrop: TextBackdrop, position: String = Constants.BOTTOM_CENTER) = MediaSettings(
        backdrop = backdrop,
        position = position,
        backgroundColor = "#101820",
        backgroundOpacity = 100,
    )

    private companion object {
        const val SECTION = "captionBackdrop"

        /** Two segments, so a plate drawn per line is visible as a stack rather than as one band. */
        val SEGMENTS = listOf(
            STTSegment(
                id = 1,
                timestamp = "00:00",
                text = "And he said unto them,",
                start = 0.0,
                end = 1.0,
                completed = true,
            ),
            STTSegment(
                id = 2,
                timestamp = "00:01",
                text = "Go ye into all the world.",
                start = 1.0,
                end = 2.0,
                completed = true,
            ),
        )

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

        /** `enabled` defaults to false, and without it `isVisible` is false and nothing is stroked. */
        val GLYPH_STROKE = TextOutline(enabled = true, width = 6, color = "#FFD54F")
    }
}
