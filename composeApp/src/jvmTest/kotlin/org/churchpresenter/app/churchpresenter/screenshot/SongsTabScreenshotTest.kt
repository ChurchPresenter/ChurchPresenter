@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.app.churchpresenter.tabs.SongFixture
import org.churchpresenter.app.churchpresenter.tabs.search
import org.churchpresenter.app.churchpresenter.tabs.songsTab
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import kotlin.test.Test

class SongsTabScreenshotTest {

    private val allColumns = emptySet<String>()

    private val bilingual = listOf(
        SongFixture(
            number = "1",
            title = "Amazing Grace",
            author = "John Newton",
            lyrics = listOf("[Verse 1]", "Amazing grace how sweet the sound"),
            secondaryTitle = "О благодать",
            secondaryLyrics = listOf("[Verse 1]", "О благодать, спасён тобой"),
        ),
    )

    private fun shoot(
        name: String,
        songSettings: SongSettings = SongSettings(),
        hiddenCols: Set<String>? = null,
        songBpm: Map<String, Int>? = null,
        stageMonitor: Boolean = false,
        isPresenting: Boolean = false,
        drive: ComposeUiTest.(SongsViewModel) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        songsTab(
            songSettings = songSettings,
            hiddenCols = hiddenCols,
            songBpm = songBpm,
            stageMonitor = stageMonitor,
            isPresenting = isPresenting,
            themeMode = mode,
        ) { vm, _ ->
            drive(vm)
            captureTo(file)
        }
    }

    private fun ComposeUiTest.clickSong(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    @Test
    fun browsing() = shoot("browsing")

    @Test
    fun `a song selected with its lyrics`() = shoot("song_selected") {
        clickSong("Amazing Grace")
    }

    @Test
    fun `a song live`() = shoot("song_live", isPresenting = true) {
        clickSong("Amazing Grace")
        onAllNodes(hasContentDescription("Go Live"))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `every column shown`() = shoot("columns_all_shown", hiddenCols = allColumns)

    @Test
    fun `only number and title shown`() =
        shoot("columns_minimal", hiddenCols = setOf("songbook", "tune", "play_count", "author", "composer"))

    @Test
    fun `the column picker`() = stackedThemes(SECTION, "columns_picker") { mode, file ->
        songsTab(hiddenCols = allColumns, themeMode = mode) { _, _ ->
            onNodeWithContentDescription("Filter columns").performClick()
            waitForIdle()
            captureTo(file, rootIndex = 1)
        }
    }

    @Test
    fun `searching by title`() = shoot("search_by_title") { search("Amazing") }

    @Test
    fun `searching by number`() = shoot("search_by_number") { search("12") }

    @Test
    fun `filtered to one songbook`() = shoot("songbook_filtered") { vm ->
        vm.updateSelectedSongbook("Chorus Book")
        waitForIdle()
    }

    @Test
    fun `the songbook picker`() = stackedThemes(SECTION, "songbook_picker") { mode, file ->
        songsTab(themeMode = mode) { _, _ ->
            onNodeWithText("SONG BOOK", substring = true).performClick()
            waitForIdle()
            captureTo(file, rootIndex = 1)
        }
    }

    @Test
    fun `the match-mode picker`() = stackedThemes(SECTION, "filter_mode_picker") { mode, file ->
        songsTab(themeMode = mode) { _, _ ->
            onNodeWithText("FILTER", substring = true).performClick()
            waitForIdle()
            captureTo(file, rootIndex = 1)
        }
    }

    @Test
    fun `starts-with match mode`() = shoot("search_starts_with") {
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Starts With").performClick()
        waitForIdle()
        search("Amazing")
    }

    @Test
    fun `search with no matches`() = shoot("search_no_matches") { search("zzzznotasong") }

    @Test
    fun `favourites panel`() = shoot("favourites") { vm ->
        vm.songsData.value.getSongs().take(2).forEach { vm.toggleFavorite(it.songId) }
        waitForIdle()
    }

    @Test
    fun `verse mode drops the line-navigation hint`() = shoot(
        "verse_mode",
        songSettings = SongSettings(
            fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
            lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
            lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
            lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
        ),
    ) {
        clickSong("Amazing Grace")
    }

    @Test
    fun `the title slide listed above the verses`() = shoot(
        "title_slide",
        songSettings = SongSettings(titleSlideEnabled = true),
    ) {
        clickSong("Amazing Grace")
    }

    @Test
    fun `the second song row selected`() = shoot("second_row_selected") {
        clickSong("Amazing Love")
    }

    @Test
    fun `a song in two languages`() = stackedThemes(SECTION, "song_two_languages") { mode, file ->
        songsTab(songs = bilingual, themeMode = mode) { _, _ ->
            clickSong("Amazing Grace")
            captureTo(file)
        }
    }

    @Test
    fun `an empty library`() = stackedThemes(SECTION, "empty_library") { mode, file ->
        songsTab(songs = emptyList<SongFixture>(), themeMode = mode) { _, _ -> captureTo(file) }
    }

    private companion object {
        const val SECTION = "songsTab"
    }
}
