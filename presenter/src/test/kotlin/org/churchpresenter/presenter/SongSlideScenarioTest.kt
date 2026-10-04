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
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTitleSlideNumber
import org.churchpresenter.settings.TextBox
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SongSlideScenarioTest {

    private val screen = Modifier.size(960.dp, 540.dp)

    private fun section(
        vararg lines: String,
        header: String = "[Verse 1]",
        languages: Int = 1,
        chords: Boolean = false,
    ) = LyricSection(
        header = header,
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines.toList(),
        chordLines = if (chords) lines.map { "[G]$it" } else emptyList(),
        translations = (1 until languages).map { n ->
            SectionTranslation(title = "Title $n", lines = lines.map { "$it ($n)" })
        },
    )

    private fun ComposeUiTest.shows(text: String) {
        waitForIdle()
        assertTrue(
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(),
            "\"$text\" is on screen",
        )
    }

    private fun slide(
        song: SongSettings,
        current: LyricSection,
        isLowerThird: Boolean = false,
        vertical: Boolean = false,
        lookAhead: Boolean = false,
        next: LyricSection? = null,
        lineIndex: Int = -1,
        language: String = Constants.SONG_LANG_BOTH,
        chords: Boolean = false,
        expect: String = "Amazing grace",
    ) = runComposeUiTest {
        val sections = listOfNotNull(current, next)
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(songSettings = song),
                    isLowerThird = isLowerThird,
                    isLowerThirdVertical = vertical,
                    lookAheadEnabled = lookAhead,
                    allLyricSections = sections,
                    displaySectionIndex = 0,
                    displayLineIndex = lineIndex,
                    languageOverride = language,
                    showChords = chords,
                )
            }
        }
        shows(expect)
    }

    @Test
    fun `two languages side by side draw both`() =
        slide(SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE), section("Amazing grace", languages = 2))

    @Test
    fun `four languages in a two-by-two grid draw every one`() = slide(
        SongSettings(bilingualLayout = Constants.BILINGUAL_GRID_2X2),
        section("Amazing grace", languages = 4),
        expect = "Amazing grace (3)",
    )

    @Test
    fun `three languages stacked top to bottom draw every one`() = slide(
        SongSettings(bilingualLayout = Constants.BILINGUAL_GRID_3X1),
        section("Amazing grace", languages = 3),
        expect = "Amazing grace (2)",
    )

    @Test
    fun `a vertical lower third stacks languages a row layout would put side by side`() = slide(
        SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE),
        section("Amazing grace", languages = 2),
        isLowerThird = true,
        vertical = true,
    )

    @Test
    fun `lyrics with auto-fit off keep their configured size`() = slide(
        SongSettings(lyricsFontSizeAutoFit = false, lookAheadNextFontSizeAutoFit = false),
        section("Amazing grace", languages = 2),
        lookAhead = true,
        next = section("That saved a wretch", header = "[Verse 2]", languages = 2),
    )

    @Test
    fun `look-ahead previews the next section's chords when it has them`() = slide(
        SongSettings(),
        section("Amazing grace", chords = true),
        lookAhead = true,
        next = section("That saved a wretch", header = "[Verse 2]", chords = true),
        chords = true,
        expect = "That saved a wretch",
    )

    @Test
    fun `look-ahead in line mode previews the next line of the same section`() = slide(
        SongSettings(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE),
        section("Amazing grace", "how sweet the sound"),
        lookAhead = true,
        next = section("That saved a wretch", header = "[Verse 2]"),
        lineIndex = 0,
        expect = "how sweet the sound",
    )

    @Test
    fun `a number set below the lyrics is drawn under them`() = slide(
        SongSettings(songNumberPosition = Constants.BELOW_LYRICS),
        section("Amazing grace"),
        expect = "42",
    )

    @Test
    fun `a lower-third look-ahead with auto-fit off draws the next section`() = slide(
        SongSettings(
            lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
            lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
            lowerThirdLookAheadNextFontSizeAutoFit = false,
            lyricsLowerThirdFontSizeAutoFit = false,
        ),
        section("Amazing grace", languages = 2),
        isLowerThird = true,
        lookAhead = true,
        next = section("That saved a wretch", header = "[Verse 2]", languages = 2),
    )

    @Test
    fun `the secondary language alone draws its own lines`() = slide(
        SongSettings(),
        section("Amazing grace", languages = 2),
        language = Constants.SONG_LANG_SECONDARY,
        expect = "Amazing grace (1)",
    )

    @Test
    fun `a line-mode look-ahead fits languages whose translations run short or are missing`() = runComposeUiTest {
        val first = LyricSection(
            header = "[Verse 1]", title = "Amazing Grace", songNumber = 0, type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace", "how sweet the sound", "that saved a wretch"),
            translations = listOf(SectionTranslation(lines = listOf("uno")), SectionTranslation()),
        )
        val second = first.copy(header = "[Verse 2]", lines = listOf("I once was lost"))
        val song = SongSettings(
            lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE,
            fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE,
            songNumberPosition = Constants.ABOVE_VERSE,
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = first,
                    appSettings = AppSettings(songSettings = song),
                    lookAheadEnabled = true,
                    allLyricSections = listOf(first, second),
                    displaySectionIndex = 0,
                    displayLineIndex = 1,
                    languageOverride = Constants.SONG_LANG_BOTH,
                )
            }
        }
        shows("how sweet the sound")
    }

    @Test
    fun `a hidden title and number take no room from the lyrics`() = slide(
        SongSettings(titleDisplay = Constants.NONE, showNumber = Constants.NONE),
        section("Amazing grace"),
    )

    @Test
    fun `a lower-third line-mode look-ahead across languages draws the current line`() = slide(
        SongSettings(
            lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_LINE,
            lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE,
            titleLowerThirdPosition = Constants.ABOVE_VERSE,
            songNumberLowerThirdPosition = Constants.ABOVE_VERSE,
        ),
        section("Amazing grace", "how sweet the sound", languages = 2),
        isLowerThird = true,
        lookAhead = true,
        next = section("That saved a wretch", header = "[Verse 2]", languages = 2),
        lineIndex = 0,
    )

    @Test
    fun `a title slide with the number before the title draws both in their own faces`() = runComposeUiTest {
        val bold = SongCreditStyle(bold = true, italic = true)
        val song = SongSettings(
            titleSlideNumberBeforeTitle = true,
            titleBold = true,
            titleItalic = true,
            titleSlideAuthor = bold,
            layoutExtras = SongLayoutExtras(
                titleSlideNumber = SongTitleSlideNumber(fullScreen = bold, lowerThird = bold),
            ),
        )
        val item = SongItem(number = "42", title = "Amazing Grace", author = "John Newton")
        val slide = titleSlideSection(item, SongTuning(), song)
        setContent {
            Box(screen) {
                SongPresenter(lyricSection = slide, appSettings = AppSettings(songSettings = song))
            }
        }
        shows("Amazing Grace")
    }

    @Test
    fun `a title below the verse and a numberless song reserve no heading room`() = slide(
        SongSettings(
            titlePosition = Constants.BELOW_VERSE,
            songNumberPosition = Constants.ABOVE_VERSE,
            songNumberCorner = Constants.NONE,
            showNumber = Constants.EVERY_PAGE,
        ),
        section("Amazing grace").copy(songNumber = 0),
    )

    @Test
    fun `a boxed title and number take nothing from the lyrics' fit`() = slide(
        SongSettings(
            songNumberCorner = Constants.NONE,
            songNumberPosition = Constants.ABOVE_VERSE,
            showNumber = Constants.EVERY_PAGE,
            titleDisplay = Constants.EVERY_PAGE,
            layoutExtras = SongLayoutExtras(
                textBoxes = mapOf(
                    "TITLE" to TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, 20f),
                    "NUMBER" to TextBox(enabled = true, xPercent = 60f, yPercent = 0f, widthPercent = 30f, 20f),
                ),
            ),
        ),
        section("Amazing grace"),
    )

    @Test
    fun `two languages one above the other draw both`() = slide(
        SongSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM),
        section("Amazing grace", languages = 2),
        expect = "Amazing grace (1)",
    )

    @Test
    fun `a one-language song under a side-by-side layout draws as a single column`() = slide(
        SongSettings(bilingualLayout = Constants.BILINGUAL_GRID_1X3),
        section("Amazing grace"),
    )
}
