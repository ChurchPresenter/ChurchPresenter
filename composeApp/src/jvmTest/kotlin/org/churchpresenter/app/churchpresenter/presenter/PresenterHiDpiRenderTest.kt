package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the presenters draw on a HiDPI output.
 *
 * Every size a presenter draws is a `.dp` or a `.sp`, which the platform multiplies by the output's
 * density itself. Measuring the output in pixels to scale those counted the density twice, so a
 * Retina Mac or Windows at 150% drew everything `density` times too large and the auto-fitted text
 * -- fitted correctly, in the 1920x1080 reference space -- ran off the side of the screen.
 *
 * Each case renders onto the same 1920x1080 *pixel* canvas at two densities: 1920x1080 dp at
 * density 1, and 960x540 dp at density 2. The same pixels must produce the same picture.
 */
@OptIn(ExperimentalTestApi::class)
class PresenterHiDpiRenderTest {

    private val tag = "output"

    /** The longest line of the song that reported this, which is what made the overflow visible. */
    private val longLine = "Great is Thy faithfulness O God my Father"

    private fun section(lines: List<String>) = LyricSection(
        header = "[Verse 1]",
        title = "Great Is Thy Faithfulness",
        songNumber = 1,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
    )

    private fun verse(text: String) = SelectedVerse(
        translationFileName = "",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    /** A window the size of the reference output, so nothing is clipped by the test's own frame. */
    private fun onCanvas(block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = CANVAS_WIDTH, height = CANVAS_HEIGHT) { block() }

    /** The canvas is [CANVAS_WIDTH] x [CANVAS_HEIGHT] pixels at every density. */
    private fun ComposeUiTest.render(density: Float, content: @Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(
                Modifier
                    .testTag(tag)
                    .size((CANVAS_WIDTH / density).dp, (CANVAS_HEIGHT / density).dp)
                    .background(Color.Black),
            ) { content() }
        }
    }

    /** The bounds of what was actually drawn, or null for a blank shot. */
    private fun ComposeUiTest.inkBounds(): Ink? {
        val map = onNodeWithTag(tag).captureToImage().toPixelMap()
        assertEquals(CANVAS_WIDTH, map.width, "the capture must be the whole canvas")
        assertEquals(CANVAS_HEIGHT, map.height, "the capture must be the whole canvas")
        var left = map.width
        var top = map.height
        var right = -1
        var bottom = -1
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val pixel = map[x, y]
                if (pixel.red > 0.5f && pixel.green > 0.5f && pixel.blue > 0.5f) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else Ink(left, top, right, bottom)
    }

    private fun ComposeUiTest.assertFullyOnScreen(what: String) {
        val (left, top, right, bottom) = inkBounds() ?: error("$what drew nothing to measure")
        assertTrue(left > 0, "$what is cut off at the left edge (ink starts at $left)")
        assertTrue(top > 0, "$what is cut off at the top edge (ink starts at $top)")
        assertTrue(right < CANVAS_WIDTH - 1, "$what runs past the right edge (ink ends at $right)")
        assertTrue(bottom < CANVAS_HEIGHT - 1, "$what runs past the bottom edge (ink ends at $bottom)")
    }

    // ── Songs ───────────────────────────────────────────────────────────────────

    @Test
    fun `a song draws the same picture on the same pixels at either density`() {
        val sections = listOf(section(listOf(longLine, "There is no shadow of turning with Thee")))
        val song: @Composable () -> Unit = {
            SongPresenter(
                lyricSection = sections.first(),
                appSettings = AppSettings(),
                allLyricSections = sections,
                displaySectionIndex = 0,
            )
        }
        var atOne: Ink? = null
        onCanvas {
            render(density = 1f, content = song)
            atOne = inkBounds()
        }
        var atTwo: Ink? = null
        onCanvas {
            render(density = 2f, content = song)
            atTwo = inkBounds()
        }
        val one = atOne ?: error("nothing drawn at density 1")
        val two = atTwo ?: error("nothing drawn at density 2")
        listOf(one.left to two.left, one.top to two.top, one.right to two.right, one.bottom to two.bottom)
            .forEach { (atDensityOne, atDensityTwo) ->
                assertTrue(
                    abs(atDensityOne - atDensityTwo) <= EDGE_TOLERANCE_PX,
                    "the lyrics moved between densities: $one against $two",
                )
            }
    }

    @Test
    fun `a long lyric line stays on screen at density 2`() = onCanvas {
        val sections = listOf(section(listOf(longLine, "There is no shadow of turning with Thee")))
        render(density = 2f) {
            SongPresenter(
                lyricSection = sections.first(),
                appSettings = AppSettings(),
                allLyricSections = sections,
                displaySectionIndex = 0,
            )
        }
        assertFullyOnScreen("the auto-fitted lyrics")
    }

    // ── Scripture ───────────────────────────────────────────────────────────────

    @Test
    fun `a long verse stays on screen at density 2`() = onCanvas {
        render(density = 2f) {
            BiblePresenter(
                selectedVerses = listOf(
                    verse(
                        "For God so loved the world that he gave his only begotten Son, that " +
                            "whosoever believeth in him should not perish but have everlasting life",
                    ),
                ),
                appSettings = AppSettings(),
            )
        }
        assertFullyOnScreen("the auto-fitted verse")
    }

    // ── Announcements and questions ─────────────────────────────────────────────

    @Test
    fun `an auto-fitted announcement stays on screen at density 2`() = onCanvas {
        render(density = 2f) {
            AnnouncementsPresenter(
                text = "The evening service starts at six o'clock in the main hall",
                appSettings = AppSettings(
                    announcementsSettings = AnnouncementsSettings(
                        animationType = Constants.ANIMATION_FADE,
                        fontSize = 200,
                    ),
                ),
            )
        }
        assertFullyOnScreen("the auto-fitted announcement")
    }

    @Test
    fun `an auto-fitted question stays on screen at density 2`() = onCanvas {
        render(density = 2f) {
            QAPresenter(
                question = Question(
                    id = "q1",
                    text = "How do I start reading the Bible for the very first time?",
                    timestamp = 0L,
                ),
            )
        }
        assertFullyOnScreen("the auto-fitted question")
    }

    private data class Ink(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private companion object {
        /** The reference output, in pixels: the window, and the canvas drawn in it. */
        const val CANVAS_WIDTH = 1920
        const val CANVAS_HEIGHT = 1080

        /** Antialiasing puts a glyph's outermost pixel either side of the boundary. */
        const val EDGE_TOLERANCE_PX = 4
    }
}
