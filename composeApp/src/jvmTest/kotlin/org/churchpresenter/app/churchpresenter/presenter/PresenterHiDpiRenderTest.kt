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
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
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
 * Retina Mac or Windows at 150% drew everything `density` times too large -- and the auto-fitted
 * text, fitted correctly in the 1920x1080 reference space, then ran off the side of the screen.
 *
 * Songs and scripture scale every size they draw to the output, so the invariant there is the same
 * pixels twice: drawn onto a 1920x1080 *pixel* canvas as 1920x1080 dp at density 1 and as 960x540 dp
 * at density 2, the two must produce the same picture. Type a density makes bigger fails that
 * whether it overflows or not, which is what makes it catch the bug rather than the one line of the
 * one song that showed it.
 *
 * An announcement scales nothing -- its configured size is absolute `sp` and its inset is absolute
 * dp -- so the same pixels legitimately draw a different picture at a different density. What must
 * hold for it is the narrower claim the bug broke: the size auto-fit chose fits the slide it was
 * fitted to.
 *
 * The Q&A card takes the same fix and has no case here: its inset is absolute dp, which cancels the
 * doubling in the width its auto-fit measures, so the wrong units left the question the same size
 * on this canvas and no assertion over it separates the two. Only its height allowance was really
 * wrong, and a question tall enough to be bound by it wraps rather than growing.
 */
@OptIn(ExperimentalTestApi::class)
class PresenterHiDpiRenderTest {

    private val tag = "output"

    /** The longest line of the song that reported this, which is what made the overflow visible. */
    private val longLine = "Great is Thy faithfulness O God my Father"

    /** Fade-in is a first-appearance animation; off, the shot does not depend on the clock. */
    private val songSettings = SongSettings(fadeIn = false)
    private val bibleSettings = BibleSettings(fadeIn = false)

    private val sections = listOf(
        LyricSection(
            header = "[Verse 1]",
            title = "Great Is Thy Faithfulness",
            songNumber = 1,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf(longLine, "There is no shadow of turning with Thee"),
        ),
    )

    private val verse = SelectedVerse(
        translationFileName = "",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world that he gave his only begotten Son, that " +
            "whosoever believeth in him should not perish but have everlasting life",
    )

    // ── The invariant ───────────────────────────────────────────────────────────

    /** Draws [content] on the same pixel canvas at both densities and fails if the picture moved. */
    private fun assertSameAtEitherDensity(what: String, content: @Composable () -> Unit) {
        val atOne = inkAt(density = 1f, content = content) ?: error("$what drew nothing at density 1")
        val atTwo = inkAt(density = DENSITY_TWO, content = content) ?: error("$what drew nothing at density 2")
        val moved = listOf(
            atOne.left to atTwo.left,
            atOne.top to atTwo.top,
            atOne.right to atTwo.right,
            atOne.bottom to atTwo.bottom,
        ).any { (one, two) -> abs(one - two) > EDGE_TOLERANCE_PX }
        assertTrue(!moved, "$what is drawn differently at density 2: $atOne against $atTwo")
    }

    /** Fails if [content] draws outside the box it was fitted to, [inset] pixels in from the edge. */
    private fun assertFitsInsideAtDensityTwo(what: String, inset: Int, content: @Composable () -> Unit) {
        val ink = inkAt(density = DENSITY_TWO, content = content) ?: error("$what drew nothing at density 2")
        val edge = inset - EDGE_TOLERANCE_PX
        assertTrue(ink.left >= edge, "$what overruns the left of its box: $ink")
        assertTrue(ink.top >= edge, "$what overruns the top of its box: $ink")
        assertTrue(ink.right <= CANVAS_WIDTH - edge, "$what overruns the right of its box: $ink")
        assertTrue(ink.bottom <= CANVAS_HEIGHT - edge, "$what overruns the bottom of its box: $ink")
    }

    /** A window the size of the reference output, so nothing is clipped by the test's own frame. */
    private fun inkAt(density: Float, content: @Composable () -> Unit): Ink? {
        var ink: Ink? = null
        runDesktopComposeUiTest(width = CANVAS_WIDTH, height = CANVAS_HEIGHT) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    Box(
                        Modifier
                            .testTag(tag)
                            .size((CANVAS_WIDTH / density).dp, (CANVAS_HEIGHT / density).dp)
                            .background(Color.Black),
                    ) { content() }
                }
            }
            ink = inkBounds()
        }
        return ink
    }

    /** The bounds of what was actually drawn, or null for a blank shot. */
    private fun ComposeUiTest.inkBounds(): Ink? {
        val map = onNodeWithTag(tag).captureToImage().toPixelMap()
        assertEquals(CANVAS_WIDTH, map.width, "the capture must be the whole canvas")
        assertEquals(CANVAS_HEIGHT, map.height, "the capture must be the whole canvas")
        var ink: Ink? = null
        (0 until map.width * map.height).forEach { index ->
            val x = index % map.width
            val y = index / map.width
            if (map[x, y].isInk()) ink = ink?.reaching(x, y) ?: Ink(x, y, x, y)
        }
        return ink
    }

    /** The type is white in every case here, and every background behind it is dark. */
    private fun Color.isInk(): Boolean = red > 0.5f && green > 0.5f && blue > 0.5f

    // ── The presenters it was wrong in ──────────────────────────────────────────

    @Test
    fun `a song is drawn the same on the same pixels at either density`() =
        assertSameAtEitherDensity("the lyrics") {
            SongPresenter(
                lyricSection = sections.first(),
                appSettings = AppSettings(songSettings = songSettings),
                allLyricSections = sections,
                displaySectionIndex = 0,
            )
        }

    @Test
    fun `a verse is drawn the same on the same pixels at either density`() =
        assertSameAtEitherDensity("the verse") {
            BiblePresenter(
                selectedVerses = listOf(verse),
                appSettings = AppSettings(bibleSettings = bibleSettings),
            )
        }

    @Test
    fun `an auto-fitted announcement fits its slide at density 2`() =
        assertFitsInsideAtDensityTwo("the announcement", inset = ANNOUNCEMENT_INSET_PX) {
            AnnouncementsPresenter(
                text = "The evening service starts at six o'clock in the main hall",
                appSettings = AppSettings(
                    announcementsSettings = AnnouncementsSettings(
                        animationType = Constants.ANIMATION_FADE,
                        fontSize = UNREACHED_CAP,
                    ),
                ),
            )
        }

    private data class Ink(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun reaching(x: Int, y: Int) =
            Ink(minOf(left, x), minOf(top, y), maxOf(right, x), maxOf(bottom, y))
    }

    private companion object {
        /** The reference output, in pixels: the test window, and the canvas drawn in it. */
        const val CANVAS_WIDTH = 1920
        const val CANVAS_HEIGHT = 1080

        /**
         * A configured size the auto-fit cannot reach, so the fit itself decides both shots.
         * The announcement and Q&A caps are absolute `sp` rather than reference units, so a cap
         * that binds draws twice as many pixels at density 2 by design.
         */
        const val UNREACHED_CAP = 400

        /** Antialiasing can put a glyph's outermost pixel either side of the boundary. */
        const val EDGE_TOLERANCE_PX = 4

        /** The inset the announcement draws inside, in pixels at density 2. */
        val ANNOUNCEMENT_INSET_PX = (TEXT_PADDING_HORIZONTAL.value * DENSITY_TWO).toInt()

        /** The density every case is checked at, and the reason this file exists. */
        const val DENSITY_TWO = 2f

    }
}
