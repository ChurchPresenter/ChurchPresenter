@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfilePagesRecompositionTest {

    private class Churn(val settings: AppSettings, val writes: Int, val profile: OutputProfile)

    private fun churn(
        initial: AppSettings,
        edits: List<(AppSettings) -> AppSettings>,
        profile: OutputProfile = OutputProfile(id = "p", name = "P"),
        profileEdits: List<(OutputProfile) -> OutputProfile> = emptyList(),
        page: @Composable (
            AppSettings,
            ((AppSettings) -> AppSettings) -> Unit,
            OutputProfile,
            (OutputProfile) -> Unit,
        ) -> Unit,
    ): Churn {
        var result: Churn? = null
        runSkikoComposeUiTest(size = Size(1000f, 4000f), density = Density(1f)) {
            var state by mutableStateOf(initial)
            var current by mutableStateOf(profile)
            var tick by mutableIntStateOf(0)
            var writes = 0
            setContent {
                val onChange = remember { { t: (AppSettings) -> AppSettings -> writes++; state = t(state) } }
                val onProfile = remember { { p: OutputProfile -> writes++; current = p } }
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        @Suppress("UNUSED_EXPRESSION") tick
                        page(state, onChange, current, onProfile)
                    }
                }
            }
            waitForIdle()
            tick++
            waitForIdle()
            edits.forEach { edit -> state = edit(state); waitForIdle() }
            profileEdits.forEach { edit -> current = edit(current); waitForIdle() }
            state = state.copy(schedulePanelWidthDp = state.schedulePanelWidthDp + 1)
            waitForIdle()
            result = Churn(state, writes, current)
        }
        return result!!
    }

    private fun unrelated(s: AppSettings) = s.copy(scheduleItemZoomPercent = s.scheduleItemZoomPercent + 5)

    @Test
    fun `the captions page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated, { s -> s.copy(sttSettings = s.sttSettings.copy(maxLines = 5)) }),
        ) { d, c, _, _ -> ProfileCaptionsPage(d, c) }
        assertEquals(0, out.writes)
        assertEquals(5, out.settings.sttSettings.maxLines)
    }

    @Test
    fun `the subtitles page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated, { s -> s.copy(mediaSettings = s.mediaSettings.copy(maxLines = 4)) }),
        ) { d, c, _, _ -> ProfileSubtitlesPage(d, c) }
        assertEquals(0, out.writes)
        assertEquals(4, out.settings.mediaSettings.maxLines)
    }

    @Test
    fun `the Q and A page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated, { s -> s.copy(qaSettings = s.qaSettings.copy(fontSize = 60, bold = true)) }),
        ) { d, c, _, _ -> ProfileQaPage(d, c) }
        assertEquals(0, out.writes)
        assertEquals(60, out.settings.qaSettings.fontSize)
    }

    @Test
    fun `the dictionary page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated, { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(wordBold = true)) }),
        ) { d, c, _, _ -> ProfileDictionaryPage(d, c) }
        assertEquals(0, out.writes)
        assertEquals(true, out.settings.dictionarySettings.wordBold)
    }

    @Test
    fun `the stage page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated, { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.copy(fontSize = 50)) }),
        ) { d, c, _, _ -> ProfileStagePage(d, c) }
        assertEquals(0, out.writes)
        assertEquals(50, out.settings.stageMonitorSettings.fontSize)
    }

    @Test
    fun `the background page writes nothing while it recomposes`() {
        listOf(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL).forEach { mode ->
            val out = churn(
                AppSettings(),
                listOf(::unrelated),
                profile = OutputProfile(id = "p", displayMode = mode),
                profileEdits = listOf(
                    { p -> p.copy(name = "Renamed") },
                    { p -> p.copy(backgroundOverrides = p.backgroundOverrides + BackgroundScope.DEFAULT.name) },
                ),
            ) { d, c, p, onP -> ProfileBackgroundPage(d, p, onP, c) {} }
            assertEquals(0, out.writes, mode)
            assertEquals("Renamed", out.profile.name)
        }
    }

    @Test
    fun `the content page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            listOf(::unrelated),
            profileEdits = listOf(
                { p -> p.copy(showSongs = !p.showSongs) },
                { p -> p.copy(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL) },
            ),
        ) { d, _, p, onP -> ProfileContentPage(d, p, onP) }
        assertEquals(0, out.writes)
        assertEquals(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, out.profile.displayMode)
    }

    @Test
    fun `the general page writes nothing while it recomposes`() {
        val out = churn(
            AppSettings(),
            emptyList(),
            profileEdits = listOf({ p -> p.copy(name = "Side") }, { p -> p.copy(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR) }),
        ) { _, _, p, onP ->
            ProfileGeneralPage(
                profile = p,
                onProfileChange = onP,
                onRename = {},
                onDuplicate = {},
                onRequestDelete = {},
                modeLocked = p.name == "Side",
                modeSub = p.name,
                deleteBlockedNote = p.name.takeIf { it == "Side" },
            )
        }
        assertEquals(0, out.writes)
        assertEquals("Side", out.profile.name)
    }

    @Test
    fun `the songs and Bible pages write nothing while they recompose`() {
        val targets = SongTargets(
            element = Adjustable(CustomizeElement.SONG_LYRICS) {},
            slideElement = Adjustable(SongStyleElement.TITLE) {},
            language = Adjustable(null) {},
        )
        listOf(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL).forEach { mode ->
            val edits = listOf<(AppSettings) -> AppSettings>(
                ::unrelated,
                { s -> s.copy(songSettings = s.songSettings.copy(titleSlideEnabled = true, wordWrap = true)) },
                { s -> s.copy(bibleSettings = s.bibleSettings.copy(textBold = true)) },
            )
            val profile = OutputProfile(id = "p", displayMode = mode)
            val songs = churn(AppSettings(), edits, profile = profile) { d, c, p, onP ->
                ProfileSongsPage(d, p, CustomizeElement.SONG_LYRICS, {}, c, onP, {}, targets)
            }
            val bible = churn(AppSettings(), edits, profile = profile) { d, c, p, onP ->
                ProfileBiblePage(d, p, 0, {}, CustomizeElement.BIBLE_TEXT, {}, c, onP, {})
            }
            assertEquals(0, songs.writes + bible.writes, mode)
            assertEquals(true, bible.settings.bibleSettings.textBold)
        }
    }
}
