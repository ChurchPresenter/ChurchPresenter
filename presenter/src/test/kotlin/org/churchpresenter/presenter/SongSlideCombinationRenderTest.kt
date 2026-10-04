package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The slide's settings that combine rather than stand alone -- a lower third with a look-ahead, a
 * chord chart previewing the next section, each slide fitted in line mode, a key output, a vertical
 * band -- each drawn once, asserting that the line being sung is there and the preview is too.
 */
@OptIn(ExperimentalTestApi::class)
class SongSlideCombinationRenderTest {

    private val current = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace", "how sweet the sound"),
        chordLines = listOf("[G]Amazing [C]grace", "how [D]sweet the sound"),
    )
    private val next = LyricSection(
        header = "[Verse 2]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Twas grace that taught"),
        chordLines = listOf("[G]Twas grace that taught"),
    )

    private fun ComposeUiTest.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun slide(
        settings: SongSettings,
        isLowerThird: Boolean = false,
        isLowerThirdVertical: Boolean = false,
        lookAhead: Boolean = false,
        lineIndex: Int = -1,
        showChords: Boolean = false,
        role: String = Constants.OUTPUT_ROLE_NORMAL,
        check: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(songSettings = settings),
                    isLowerThird = isLowerThird,
                    isLowerThirdVertical = isLowerThirdVertical,
                    outputRole = role,
                    displayLineIndex = lineIndex,
                    lookAheadEnabled = lookAhead,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                    showChords = showChords,
                )
            }
        }
        waitForIdle()
        check()
    }

    private val verse = Constants.SONG_DISPLAY_MODE_VERSE
    private val line = Constants.SONG_DISPLAY_MODE_LINE

    @Test
    fun `a lower third with a look-ahead previews the next section`() = slide(
        SongSettings(lowerThirdDisplayMode = verse, lowerThirdLookAheadDisplayMode = verse),
        isLowerThird = true,
        lookAhead = true,
    ) {
        assertTrue(shows("Amazing grace"))
        assertTrue(shows("Twas grace that taught"), "the next section previewed on the band")
    }

    @Test
    fun `a chord chart previews the next section's chords, a whole section or one line`() {
        val wholeSections = SongSettings(fullscreenDisplayMode = verse, lookAheadDisplayMode = verse)
        slide(wholeSections, lookAhead = true, showChords = true) {
            assertTrue(shows("Amazing"))
            assertTrue(shows("Twas grace"), "the next section, whole")
        }
        slide(
            SongSettings(fullscreenDisplayMode = line, lookAheadDisplayMode = line),
            lookAhead = true,
            lineIndex = 1,
            showChords = true,
        ) {
            assertTrue(shows("sweet"), "the last line of the section")
            assertTrue(shows("Twas grace"), "and the next section's first line after it")
        }
    }

    @Test
    fun `each slide fitted in line mode still draws its line`() = slide(
        SongSettings(
            fullscreenDisplayMode = line,
            lookAheadDisplayMode = line,
            lyricsFontSizeAutoFit = true,
            lookAheadFontSizeAutoFit = true,
            layoutExtras = SongLayoutExtras(autoFitEachSlide = true),
        ),
        lookAhead = true,
        lineIndex = 0,
    ) {
        assertTrue(shows("Amazing grace"))
        assertTrue(shows("how sweet the sound"), "the next line previewed")
    }

    @Test
    fun `the key output and a vertical band draw the same words`() {
        slide(SongSettings(fullscreenDisplayMode = verse), role = Constants.OUTPUT_ROLE_KEY) {
            assertTrue(shows("Amazing grace"))
        }
        slide(SongSettings(lowerThirdDisplayMode = verse), isLowerThird = true, isLowerThirdVertical = true) {
            assertTrue(shows("Amazing grace"))
        }
    }
}
