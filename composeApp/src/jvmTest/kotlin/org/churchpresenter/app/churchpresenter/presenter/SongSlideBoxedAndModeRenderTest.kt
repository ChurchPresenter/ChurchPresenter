package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongElementPosition
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withTranslationSettings
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SongSlideBoxedAndModeRenderTest {

    private val current = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace", "how sweet the sound"),
        chordLines = listOf("[G]Amazing [C]grace", "how [D]sweet the sound"),
        translations = listOf(
            SectionTranslation(title = "Sublime gracia", lines = listOf("Sublime gracia", "del Senor")),
        ),
    )
    private val next = LyricSection(
        header = "[Verse 2]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Twas grace that taught"),
        chordLines = listOf("[G]Twas grace that taught"),
    )
    private val empty = LyricSection(header = "[Tag]", title = "Amazing Grace", type = Constants.SECTION_TYPE_VERSE)

    private val verse = Constants.SONG_DISPLAY_MODE_VERSE
    private val line = Constants.SONG_DISPLAY_MODE_LINE
    private val box = TextBox(enabled = true, xPercent = 5f, yPercent = 5f, widthPercent = 60f, heightPercent = 40f)

    private fun ComposeUiTest.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun SongSettings.boxing(vararg elements: SongStyleElement, lowerThird: Boolean = false) =
        copy(layoutExtras = layoutExtras.copy(textBoxes = elements.associate { songBoxKey(it, lowerThird) to box }))

    private fun slide(
        settings: SongSettings,
        isLowerThird: Boolean = false,
        lookAhead: Boolean = false,
        lineIndex: Int = -1,
        showChords: Boolean = false,
        sections: List<LyricSection> = listOf(current, next),
        index: Int = 0,
        languages: List<Int> = emptyList(),
        check: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                SongPresenter(
                    lyricSection = sections[index],
                    appSettings = AppSettings(songSettings = settings),
                    isLowerThird = isLowerThird,
                    displayLineIndex = lineIndex,
                    lookAheadEnabled = lookAhead,
                    allLyricSections = sections,
                    displaySectionIndex = index,
                    showChords = showChords,
                    languageSelection = languages,
                )
            }
        }
        waitForIdle()
        check()
    }

    @Test
    fun `boxed lyrics in verse mode draw the section in the box`() =
        slide(SongSettings(fullscreenDisplayMode = verse).boxing(SongStyleElement.LYRICS)) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `boxed lyrics in line mode fit every line of the song`() =
        slide(SongSettings(fullscreenDisplayMode = line).boxing(SongStyleElement.LYRICS), lineIndex = -1) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `boxed lyrics fitted per slide in line mode`() {
        val settings = SongSettings(fullscreenDisplayMode = line).boxing(SongStyleElement.LYRICS).let {
            it.copy(layoutExtras = it.layoutExtras.copy(autoFitEachSlide = true))
        }
        slide(settings, lineIndex = 1) { assertTrue(shows("sweet")) }
    }

    @Test
    fun `boxed look-ahead previews the next section in a box`() =
        slide(
            SongSettings(lookAheadDisplayMode = verse)
                .boxing(SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION),
            lookAhead = true,
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `boxed look-ahead in line mode on the last section`() =
        slide(
            SongSettings(lookAheadDisplayMode = line).boxing(SongStyleElement.LOOK_AHEAD),
            lookAhead = true,
            sections = listOf(current, next),
            index = 1,
            lineIndex = 0,
        ) {
            assertTrue(shows("Twas grace"))
        }

    @Test
    fun `a look-ahead skips a following section with no lines`() =
        slide(
            SongSettings(lookAheadDisplayMode = verse).boxing(SongStyleElement.LOOK_AHEAD),
            lookAhead = true,
            sections = listOf(current, empty),
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `boxed lyrics on a lower third with each slide fitted`() {
        val settings = SongSettings(lowerThirdDisplayMode = verse)
            .boxing(SongStyleElement.LYRICS, lowerThird = true)
            .let { it.copy(layoutExtras = it.layoutExtras.copy(autoFitEachSlideLowerThird = true)) }
        slide(settings, isLowerThird = true) { assertTrue(shows("Amazing grace")) }
    }

    @Test
    fun `boxed lyrics on a lower third with a look-ahead in line mode`() =
        slide(
            SongSettings(lowerThirdLookAheadDisplayMode = line).boxing(SongStyleElement.LOOK_AHEAD, lowerThird = true),
            isLowerThird = true,
            lookAhead = true,
            lineIndex = 0,
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `boxed title and number show on every page`() =
        slide(
            SongSettings(titleDisplay = Constants.EVERY_PAGE, showNumber = Constants.EVERY_PAGE)
                .boxing(SongStyleElement.TITLE, SongStyleElement.NUMBER, SongStyleElement.SECTION_LABEL),
            index = 1,
        ) {
            assertTrue(shows("Twas grace"))
        }

    @Test
    fun `a boxed next section keeps only the lyrics on the slide`() =
        slide(
            SongSettings(lookAheadDisplayMode = verse).boxing(SongStyleElement.NEXT_SECTION),
            lookAhead = true,
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `the title and number below the lyrics with a section label`() =
        slide(
            SongSettings(
                titleDisplay = Constants.EVERY_PAGE,
                titlePosition = Constants.BELOW_LYRICS,
                showNumber = Constants.EVERY_PAGE,
                songNumberPosition = Constants.BELOW_LYRICS,
                songNumberCorner = Constants.NONE,
            ).let {
                it.copy(layoutExtras = it.layoutExtras.copy(sectionLabel = SongSectionLabel(enabled = true)))
            },
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `the number below the verse with no corner`() =
        slide(
            SongSettings(
                showNumber = Constants.EVERY_PAGE,
                songNumberPosition = Constants.BELOW_VERSE,
                songNumberCorner = Constants.NONE,
            ),
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `a second language with its own style titles the slide in its own look`() =
        slide(
            SongSettings(titleDisplay = Constants.EVERY_PAGE)
                .withTranslationSettings(0) { it.copy(overrideStyle = true) },
            languages = listOf(1),
        ) {
            assertTrue(shows("Sublime gracia"))
        }

    @Test
    fun `a chart in line mode with the next line in the look-ahead`() =
        slide(
            SongSettings(fullscreenDisplayMode = line, lookAheadDisplayMode = line),
            lookAhead = true,
            lineIndex = 0,
            showChords = true,
        ) {
            assertTrue(shows("Amazing"))
        }

    @Test
    fun `a chart in verse mode whose next section is previewed line by line`() =
        slide(
            SongSettings(fullscreenDisplayMode = verse, lookAheadDisplayMode = line),
            lookAhead = true,
            showChords = true,
        ) {
            assertTrue(shows("Amazing"))
        }

    @Test
    fun `a chart on the last section has no next to preview`() =
        slide(
            SongSettings(lookAheadDisplayMode = verse),
            lookAhead = true,
            showChords = true,
            index = 1,
        ) {
            assertTrue(shows("Twas"))
        }

    @Test
    fun `boxed lyrics with auto-fit off and next-section position on the lyrics`() =
        slide(
            SongSettings(lyricsFontSizeAutoFit = false, lookAheadFontSizeAutoFit = false)
                .boxing(SongStyleElement.LYRICS)
                .let {
                    it.copy(
                        layoutExtras = it.layoutExtras.copy(
                            nextSectionPosition = SongElementPosition(fullScreen = Constants.ABOVE_LYRICS),
                        ),
                    )
                },
            lookAhead = true,
        ) {
            assertTrue(shows("Amazing grace"))
        }

    @Test
    fun `look-ahead with auto-fit off on a two-language side-by-side slide`() =
        slide(
            SongSettings(
                lyricsFontSizeAutoFit = false,
                lookAheadFontSizeAutoFit = false,
                bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE,
            ),
            lookAhead = true,
            languages = listOf(0, 1),
        ) {
            assertTrue(shows("Amazing grace"))
            assertTrue(shows("Sublime gracia"))
        }
}
