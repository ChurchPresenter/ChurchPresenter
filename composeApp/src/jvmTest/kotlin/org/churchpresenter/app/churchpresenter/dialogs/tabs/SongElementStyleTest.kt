package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The lens the song settings tab edits through: one control set standing for ten stored profiles.
 *
 * Every combination has to round-trip, because a control writes the whole [SongElementStyle] back
 * and a field the writer forgot would be silently reset by any edit to its neighbour.
 */
class SongElementStyleTest {

    private val elements = SongStyleElement.entries
    private val targets = SongStyleTarget.entries
    private val combinations = elements.flatMap { element -> targets.map { element to it } }

    /** Every field set away from its default, so a writer that drops one is caught. */
    private fun distinct(seed: Int) = SongElementStyle(
        color = "#11223$seed",
        fontType = "Georgia$seed",
        fontSize = 40 + seed,
        bold = true,
        italic = true,
        underline = true,
        strikethrough = true,
        shadow = true,
        shadowColor = "#99887$seed",
        shadowSize = 50 + seed,
        shadowOpacity = 60 + seed,
        horizontalAlignment = Constants.CENTER,
        position = Constants.ABOVE_VERSE,
        letterSpacing = 3 + seed,
        wordSpacing = 5 + seed,
        transform = Constants.TEXT_TRANSFORM_UPPERCASE,
        chordColor = "#0011F$seed",
    )

    @Test
    fun `there are ten profiles`() {
        assertEquals(10, combinations.size)
    }

    @Test
    fun `every combination round-trips the fields it stores`() {
        combinations.forEachIndexed { index, (element, target) ->
            val style = distinct(index)
            val written = SongSettings().withElementStyle(element, target, style)

            // A profile with nowhere to keep a value drops it and reads the default back, which is
            // why the panel hides that control rather than showing an ineffective one.
            val expected = style.copy(
                position = if (element.hasPosition) style.position else SongElementStyle().position,
                chordColor = if (element.hasChordColor) style.chordColor else SongElementStyle().chordColor,
            )
            assertEquals(expected, written.elementStyle(element, target), "$element on $target")
        }
    }

    @Test
    fun `writing one combination leaves the other nine alone`() {
        combinations.forEachIndexed { index, (element, target) ->
            val written = SongSettings().withElementStyle(element, target, distinct(index))

            combinations.filterNot { it.first == element && it.second == target }.forEach { other ->
                assertEquals(
                    defaultSongElementStyle(other.first, other.second),
                    written.elementStyle(other.first, other.second),
                    "writing $element/$target must not touch ${other.first}/${other.second}",
                )
            }
        }
    }

    @Test
    fun `only the number and the title have a position`() {
        assertEquals(
            listOf(SongStyleElement.NUMBER, SongStyleElement.TITLE),
            elements.filter { it.hasPosition },
        )
    }

    @Test
    fun `only the lyrics carry a chord colour`() {
        assertEquals(listOf(SongStyleElement.LYRICS), elements.filter { it.hasChordColor })
    }

    @Test
    fun `the look-ahead slide is where two of the elements live`() {
        assertEquals(
            listOf(SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION),
            elements.filter { it.onLookAheadSlide },
        )
    }

    @Test
    fun `the defaults are read from the stored defaults rather than restated`() {
        assertEquals(
            SongSettings().lyricsLowerThirdFontSize,
            defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).fontSize,
        )
        assertNotEquals(
            defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN).fontSize,
            defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD).fontSize,
        )
    }

    @Test
    fun `the title now defaults to a position the presenter actually draws`() {
        // It defaulted to Middle, which the presenter draws the title row at neither of -- so the
        // title was configured to appear and then appeared nowhere.
        listOf(SongSettings().titlePosition, SongSettings().titleLowerThirdPosition).forEach { position ->
            assertTrue(
                position == Constants.ABOVE_VERSE || position == Constants.BELOW_VERSE,
                "the title must sit somewhere the presenter draws, was $position",
            )
        }
    }

    // ── Chunk: per element, because the look-ahead slide keeps its own ───────────────────────────

    @Test
    fun `the look-ahead elements read and write the look-ahead chunk`() {
        val settings = SongSettings().withChunk(
            SongStyleElement.NEXT_SECTION,
            SongStyleTarget.FULL_SCREEN,
            Constants.SONG_DISPLAY_MODE_LINE,
        )

        assertEquals(Constants.SONG_DISPLAY_MODE_LINE, settings.lookAheadDisplayMode)
        assertEquals(
            Constants.SONG_DISPLAY_MODE_LINE,
            settings.chunkFor(SongStyleElement.LOOK_AHEAD, SongStyleTarget.FULL_SCREEN),
            "both look-ahead elements share the one slide's setting",
        )
        assertEquals(
            SongSettings().fullscreenDisplayMode,
            settings.fullscreenDisplayMode,
            "and the ordinary slide is untouched",
        )
    }

    @Test
    fun `the other elements read and write the slide's own chunk`() {
        val settings = SongSettings().withChunk(
            SongStyleElement.LYRICS,
            SongStyleTarget.LOWER_THIRD,
            Constants.SONG_DISPLAY_MODE_VERSE,
        )

        assertEquals(Constants.SONG_DISPLAY_MODE_VERSE, settings.lowerThirdDisplayMode)
        assertEquals(SongSettings().lookAheadDisplayMode, settings.lookAheadDisplayMode)
        assertEquals(SongSettings().fullscreenDisplayMode, settings.fullscreenDisplayMode)
    }
}
