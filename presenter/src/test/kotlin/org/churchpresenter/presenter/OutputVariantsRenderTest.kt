package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTitleSlideNumber
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OutputVariantsRenderTest {

    private val section = LyricSection(
        header = "[Verse 1]", title = "Amazing Grace", songNumber = 42, type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace how sweet the sound"),
    )

    private val verse = SelectedVerse(
        translationFileName = "kjv.spb", bibleAbbreviation = "KJV", bibleName = "KJV",
        bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world",
    )

    private fun ComposeUiTest.shows(text: String, content: @Composable () -> Unit) {
        setContent { Box(Modifier.size(960.dp, 540.dp)) { content() } }
        waitForIdle()
        val nodes = onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "\"$text\" is on screen")
    }

    private fun sharedRow(alignment: String, numberFirst: Boolean) = SongSettings(
        titlePosition = Constants.ABOVE_VERSE,
        songNumberPosition = Constants.ABOVE_VERSE,
        songNumberCorner = Constants.NONE,
        titleHorizontalAlignment = alignment,
        songNumberHorizontalAlignment = alignment,
        songNumberBeforeTitle = numberFirst,
        titleDisplay = Constants.EVERY_PAGE,
        showNumber = Constants.EVERY_PAGE,
    )

    private fun songShows(text: String, song: SongSettings) = runComposeUiTest {
        shows(text) { SongPresenter(lyricSection = section, appSettings = AppSettings(songSettings = song)) }
    }

    @Test
    fun `a number before the title shares its row on the left`() =
        songShows("Amazing Grace", sharedRow(Constants.LEFT, numberFirst = true))

    @Test
    fun `a number after the title shares its row in the centre`() =
        songShows("Amazing Grace", sharedRow(Constants.CENTER, numberFirst = false))

    @Test
    fun `a number aligned apart from the title keeps its own side of the row`() =
        songShows(
            "Amazing Grace",
            sharedRow(Constants.LEFT, numberFirst = true).copy(songNumberHorizontalAlignment = Constants.RIGHT),
        )

    @Test
    fun `a song with every fade off draws without a fade layer`() =
        songShows("Amazing grace", SongSettings(fadeIn = false, fadeOut = false, crossfade = false))

    private val dimmedBlurredBand =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#336699", dim = 40, blur = 20)

    @Test
    fun `a song band over a dimmed, blurred background still draws its lyric`() = runComposeUiTest {
        val settings = AppSettings(
            songSettings = SongSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE),
            backgroundSettings = BackgroundSettings(songLowerThirdBackground = dimmedBlurredBand),
        )
        shows("Amazing grace") { SongPresenter(lyricSection = section, appSettings = settings, isLowerThird = true) }
    }

    @Test
    fun `a Bible band over a dimmed, blurred background still draws its verse`() = runComposeUiTest {
        val settings =
            AppSettings(backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = dimmedBlurredBand))
        shows("For God so loved") {
            BiblePresenter(selectedVerses = listOf(verse), appSettings = settings, isLowerThird = true)
        }
    }

    @Test
    fun `a Bible passage with every fade off draws without a fade layer`() = runComposeUiTest {
        val settings = AppSettings(bibleSettings = BibleSettings(fadeIn = false, fadeOut = false, crossfade = false))
        shows("For God so loved") { BiblePresenter(selectedVerses = listOf(verse), appSettings = settings) }
    }

    @Test
    fun `a title slide with its number pinned to a corner draws the title`() = runComposeUiTest {
        val song = SongSettings(
            layoutExtras = SongLayoutExtras(titleSlideNumber = SongTitleSlideNumber(corner = Constants.TOP_RIGHT)),
        )
        val slide = titleSlideSection(SongItem(number = "42", title = "Amazing Grace"), SongTuning(), song)
        shows("Amazing Grace") { SongPresenter(lyricSection = slide, appSettings = AppSettings(songSettings = song)) }
    }
}
