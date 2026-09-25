package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCue
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every 4th pixel's colour, coarse enough to be fast and fine enough to catch a card or glyph. */
private const val SAMPLE_STEP = 4

private fun distinctColors(bitmap: ImageBitmap): Set<Color> {
    val map = bitmap.toPixelMap()
    return (0 until map.width step SAMPLE_STEP)
        .flatMap { x -> (0 until map.height step SAMPLE_STEP).map { y -> map[x, y] } }
        .toSet()
}

/**
 * What matters here: a blank cue draws nothing (the card only appears when there is text), a real
 * cue draws its card in the configured colour, and the key output forces white regardless of
 * [MediaSettings] -- the same three invariants [MediaPresenterRenderTest] pins for the video frame
 * itself. Glyph shape and position are not asserted (font metrics differ across platforms, per this
 * suite's own convention); only whether *something* was drawn, and in what colour.
 */
@OptIn(ExperimentalTestApi::class)
class SubtitleOverlayRenderTest {

    private val cue = SubtitleCue(startMs = 0, endMs = 5_000, text = "Hello")
    private val blank = SubtitleCue(startMs = 0, endMs = 5_000, text = "   ")

    @Test
    fun `a blank cue draws nothing over the background`() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)
            ) {
                SubtitleOverlay(cue = blank, mediaSettings = MediaSettings())
            }
        }

        val colors = distinctColors(onNodeWithTag("overlay").captureToImage())
        assertEquals(setOf(Color.Black), colors, "nothing but the plain backdrop should have been drawn")
    }

    // ── Two tracks at once, which is how a bilingual output is built (#612) ─────────────────────

    /** Renders [cues] over black and returns how tall the drawn cards come to, in sampled pixels. */
    private fun drawnHeight(cues: List<SubtitleCue>): Int {
        var height = 0
        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)) {
                    SubtitleOverlay(
                        cues = cues,
                        mediaSettings = MediaSettings(backgroundColor = "#FF0000", backgroundOpacity = 100),
                    )
                }
            }
            val map = onNodeWithTag("overlay").captureToImage().toPixelMap()
            height = (0 until map.height).count { y ->
                (0 until map.width step SAMPLE_STEP).any { x -> map[x, y] != Color.Black }
            }
        }
        return height
    }

    @Test
    fun `a second routed track stacks below the first rather than replacing it`() {
        val one = drawnHeight(listOf(cue))
        val two = drawnHeight(listOf(cue, SubtitleCue(startMs = 0, endMs = 5_000, text = "Hola")))

        // Each keeps its own card, so two of them are taller than one -- a bilingual pair reads as
        // two lines. Asserted as a relation rather than an exact height: glyph metrics differ across
        // the three platforms, which is this suite's standing convention.
        assertTrue(two > one, "two cards ($two px) should be taller than one ($one px)")
    }

    @Test
    fun `a blank second cue is dropped rather than drawn as an empty card`() {
        val one = drawnHeight(listOf(cue))
        val withBlank = drawnHeight(listOf(cue, blank))

        // One track's cue window can easily be empty while the other's is not, and an empty card
        // under a real one is a coloured band with nothing in it.
        assertEquals(one, withBlank)
    }

    @Test
    fun `no cues at all draws nothing`() {
        assertEquals(0, drawnHeight(emptyList()))
    }

    @Test
    fun `a real cue draws its card, more than just the background`() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)
            ) {
                SubtitleOverlay(
                    cue = cue,
                    mediaSettings = MediaSettings(backgroundColor = "#FF0000", backgroundOpacity = 100),
                )
            }
        }

        val colors = distinctColors(onNodeWithTag("overlay").captureToImage())
        assertTrue(colors.size > 1, "a card and its text should draw more than the plain backdrop")
    }

    @Test
    fun `bold italic underline shadow, a transparent background and left-right alignment all render`() =
        runComposeUiTest {
            setContent {
                Box(
                    modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)
                ) {
                    SubtitleOverlay(
                        cue = cue,
                        mediaSettings = MediaSettings(
                            bold = true,
                            italic = true,
                            underline = true,
                            shadow = true,
                            backgroundColor = "transparent",
                            position = Constants.BOTTOM_LEFT,
                        ),
                    )
                }
            }

            val colors = distinctColors(onNodeWithTag("overlay").captureToImage())
            assertTrue(colors.size > 1, "styled text over the transparent-background card should still draw")
        }

    @Test
    fun `right-aligned position renders without error`() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)
            ) {
                SubtitleOverlay(
                    cue = cue,
                    mediaSettings = MediaSettings(position = Constants.BOTTOM_RIGHT),
                )
            }
        }

        val colors = distinctColors(onNodeWithTag("overlay").captureToImage())
        assertTrue(colors.size > 1, "a right-aligned cue should still draw its card")
    }

    @Test
    fun `the key output role forces a white card regardless of the configured colour`() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier.testTag("overlay").size(200.dp).background(Color.Black)
            ) {
                SubtitleOverlay(
                    cue = cue,
                    mediaSettings = MediaSettings(backgroundColor = "#FF0000", backgroundOpacity = 100),
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                )
            }
        }

        val colors = distinctColors(onNodeWithTag("overlay").captureToImage())
        assertTrue(Color.White in colors, "the key output's card must be white, not the configured red")
        assertTrue(Color(0xFFFF0000) !in colors, "the configured colour must not leak into the key output")
    }
}
