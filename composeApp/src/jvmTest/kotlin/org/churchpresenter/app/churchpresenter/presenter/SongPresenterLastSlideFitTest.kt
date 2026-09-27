package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Auto → Each slide sizing the song's last slide like every other (#671).
 *
 * The fit used to leave room for the end-of-song marker on the last slide alone, and whether or not
 * the marker was on -- so under Each slide the last slide came out smaller than an identical one
 * before it. The marker's row is kept on every slide, so it is reserved on every slide, and only
 * while it is on. Two sections with the same words are compared by the drawn height of their line.
 *
 * Top and bottom margins are wide so that the height, not the width, is what the fit runs into:
 * that is the only case in which a reserved row can cost the text any size.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterLastSlideFitTest {

    private fun section(header: String, last: Boolean) = LyricSection(
        header = header,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Same words", "Same words again"),
        isLastSection = last,
    )

    private val first = section("[Verse 1]", last = false)
    private val last = section("[Verse 2]", last = true)

    /** The drawn height of the first line on slide [index], with the end marker [marker]. */
    private fun lineHeight(index: Int, marker: Boolean, lineMode: Boolean): Float {
        val shown = listOf(first, last)[index]
        var height = 0f
        runDesktopComposeUiTest(width = 1920, height = 1080) {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(1920.dp, 1080.dp)) {
                        SongPresenter(
                            lyricSection = shown,
                            appSettings = AppSettings(
                                songSettings = SongSettings(
                                    lyricsFontSize = 200,
                                    lyricsFontSizeAutoFit = true,
                                    marginTop = 330,
                                    marginBottom = 330,
                                    showEndOfSongIndicator = marker,
                                    fullscreenDisplayMode = if (lineMode) {
                                        Constants.SONG_DISPLAY_MODE_LINE
                                    } else {
                                        Constants.SONG_DISPLAY_MODE_VERSE
                                    },
                                    layoutExtras = SongLayoutExtras(autoFitEachSlide = true),
                                ),
                            ),
                            allLyricSections = listOf(first, last),
                            displaySectionIndex = index,
                            // The slide's last line: the one the marker belongs under on the last slide.
                            displayLineIndex = if (lineMode) 1 else -1,
                        )
                    }
                }
            }
            height = onAllNodesWithText("Same words again").fetchSemanticsNodes().first().boundsInRoot.height
        }
        return height
    }

    @Test
    fun `verse mode sizes the last slide like the one before it, marker off`() {
        assertEquals(lineHeight(0, marker = false, lineMode = false), lineHeight(1, marker = false, lineMode = false))
    }

    @Test
    fun `verse mode sizes the last slide like the one before it, marker on`() {
        assertEquals(lineHeight(0, marker = true, lineMode = false), lineHeight(1, marker = true, lineMode = false))
    }

    @Test
    fun `line mode sizes the last line like the one before it, marker off`() {
        assertEquals(lineHeight(0, marker = false, lineMode = true), lineHeight(1, marker = false, lineMode = true))
    }

    @Test
    fun `line mode sizes the last line like the one before it, marker on`() {
        assertEquals(lineHeight(0, marker = true, lineMode = true), lineHeight(1, marker = true, lineMode = true))
    }
}
