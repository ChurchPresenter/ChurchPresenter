package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongElementPosition
import org.churchpresenter.settings.SongElementShift
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.songElementShiftKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the section label, the title and the next-section lines land for each of the four places a
 * song element can sit (#656): the content area's top and bottom edge, or held directly above or
 * below the lyrics, moving wherever the lyrics' alignment puts them.
 *
 * Asserted as relations between drawn bounds -- above, below, touching, far apart -- rather than as
 * pixel values, which differ across the platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class SongElementPlacementRenderTest {

    private val verse = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("First lyric line", "Last lyric line"),
    )
    private val chorus = LyricSection(
        header = "[Chorus]",
        type = Constants.SECTION_TYPE_CHORUS,
        lines = listOf("Coming next"),
    )

    /** The drawn bounds of each text in [texts], on a 1920x1080 output. */
    private fun bounds(
        song: SongSettings,
        vararg texts: String,
        lowerThird: Boolean = false,
        lookAhead: Boolean = false,
    ): List<Rect> {
        var found = emptyList<Rect>()
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                        SongPresenter(
                            lyricSection = verse,
                            appSettings = AppSettings(songSettings = song),
                            isLowerThird = lowerThird,
                            allLyricSections = listOf(verse, chorus),
                            displaySectionIndex = 0,
                            lookAheadEnabled = lookAhead,
                        )
                    }
                }
            }
            found = texts.map { boundsOf(it) }
        }
        return found
    }

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot

    /** Middle-aligned lyrics with the label on, placed at [position] on both outputs. */
    private fun labelled(position: String, shift: SongElementShift? = null, lowerThird: Boolean = false) = SongSettings(
        lyricsAlignment = Constants.MIDDLE,
        layoutExtras = SongLayoutExtras(
            sectionLabel = SongSectionLabel(enabled = true, position = position, lowerThirdPosition = position),
            elementShifts = shift?.let { mapOf(songElementShiftKey("SECTION_LABEL", lowerThird) to it) }.orEmpty(),
        ),
    )

    @Test
    fun `above the lyrics, the label sits directly on the middle-aligned verse`() {
        val (label, first) = bounds(labelled(Constants.ABOVE_LYRICS), "Verse 1", "First lyric line")
        assertTrue(label.bottom <= first.top + 1, "label $label must end above the first line $first")
        assertTrue(first.top - label.bottom < label.height, "label $label must touch the verse $first")
    }

    @Test
    fun `below the lyrics, the label sits directly under the verse`() {
        val (label, last) = bounds(labelled(Constants.BELOW_LYRICS), "Verse 1", "Last lyric line")
        assertTrue(label.top >= last.bottom - 1, "label $label must start below the last line $last")
        assertTrue(label.top - last.bottom < label.height, "label $label must touch the verse $last")
    }

    @Test
    fun `at the top edge, the label stays at the top while the verse sits in the middle`() {
        val (label, first) = bounds(labelled(Constants.ABOVE_VERSE), "Verse 1", "First lyric line")
        assertTrue(label.bottom < first.top, "label $label above the verse $first")
        assertTrue(label.top < HEIGHT / 4f, "label $label at the top of the output")
        assertTrue(first.top - label.bottom > label.height * 2, "the verse is centred, well away from it")
    }

    @Test
    fun `at the bottom edge, the label stays at the bottom`() {
        val (label, last) = bounds(labelled(Constants.BELOW_VERSE), "Verse 1", "Last lyric line")
        assertTrue(label.top > last.bottom, "label $label below the verse $last")
        assertTrue(label.bottom > HEIGHT * 3 / 4f, "label $label at the bottom of the output")
    }

    @Test
    fun `on a lower third, the label sits directly on the verse in the band`() {
        val (label, first) = bounds(
            labelled(Constants.ABOVE_LYRICS),
            "Verse 1",
            "First lyric line",
            lowerThird = true,
        )
        assertTrue(label.bottom <= first.top + 1, "label $label above the first line $first")
        assertTrue(first.top - label.bottom < label.height, "label $label touches the verse $first")
        assertTrue(label.top > HEIGHT / 2f, "label $label is in the band, not at the top of the output")
    }

    @Test
    fun `a label moved on its own is drawn that far from where the layout puts it`() {
        val (placed) = bounds(labelled(Constants.ABOVE_LYRICS), "Verse 1")
        val (moved) = bounds(labelled(Constants.ABOVE_LYRICS, SongElementShift(x = 200, y = -100)), "Verse 1")
        assertTrue(moved.left - placed.left > 150, "moved right: $placed -> $moved")
        assertTrue(placed.top - moved.top > 50, "moved up: $placed -> $moved")
    }

    @Test
    fun `a label switched off draws nothing`() {
        var count = -1
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                        SongPresenter(lyricSection = verse, appSettings = AppSettings())
                    }
                }
            }
            count = onAllNodesWithText("Verse 1").fetchSemanticsNodes().size
        }
        assertTrue(count == 0, "no label while it is off")
    }

    @Test
    fun `a title held above the lyrics moves with the middle-aligned verse`() {
        val song = SongSettings(
            lyricsAlignment = Constants.MIDDLE,
            titleDisplay = Constants.EVERY_PAGE,
            titlePosition = Constants.ABOVE_LYRICS,
        )
        val (title, first) = bounds(song, "Amazing Grace", "First lyric line")
        assertTrue(title.bottom <= first.top + 1, "title $title above the first line $first")
        assertTrue(first.top - title.bottom < title.height, "title $title touches the verse $first")
        assertTrue(title.top > HEIGHT / 4f, "title $title moved down with the verse, off the top edge")
    }

    @Test
    fun `the next section can be held above the lyrics`() {
        val song = SongSettings(
            layoutExtras = SongLayoutExtras(
                nextSectionPosition = SongElementPosition(fullScreen = Constants.ABOVE_LYRICS),
            ),
        )
        val (next, first) = bounds(song, "Coming next", "First lyric line", lookAhead = true)
        assertTrue(next.bottom <= first.top + 1, "next section $next above the lyrics $first")
    }

    @Test
    fun `the next section stays under the lyrics by default`() {
        val (next, last) = bounds(SongSettings(), "Coming next", "Last lyric line", lookAhead = true)
        assertTrue(next.top >= last.bottom - 1, "next section $next below the lyrics $last")
    }

    @Test
    fun `the next section at the bottom edge sits at the bottom of the output`() {
        val song = SongSettings(
            lyricsAlignment = Constants.TOP,
            layoutExtras = SongLayoutExtras(
                nextSectionPosition = SongElementPosition(fullScreen = Constants.BELOW_VERSE),
            ),
        )
        val (next, last) = bounds(song, "Coming next", "Last lyric line", lookAhead = true)
        assertTrue(next.top > last.bottom, "next section $next below the lyrics $last")
        assertTrue(next.bottom > HEIGHT * 3 / 4f, "next section $next at the bottom edge")
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
