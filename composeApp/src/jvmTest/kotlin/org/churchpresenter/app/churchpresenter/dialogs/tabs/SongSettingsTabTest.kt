package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The song settings tab is the largest pure-View surface in the app (~1,400 lines). Its checkboxes
 * carry `testTag`s so each can be reached unambiguously and — because the tab is a long scroll —
 * `performScrollTo`'d into view before it is clicked (a node found in the tree but off-screen cannot
 * be clicked). This drives every checkbox and asserts the exact `SongSettings` flag it flips, so each
 * onCheckedChange runs in place rather than only rendering.
 */
@OptIn(ExperimentalTestApi::class)
class SongSettingsTabTest {

    private fun runTab(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                SongSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    presenterManager = null,
                )
            }
        }
        block { current }
    }

    @Test
    fun `the tab shows the title-slide section`() = runTab { _ ->
        onAllNodesWithText("Song Title Slide", substring = true).onFirst()
            .assertExists("the title-slide section must render when the tab opens")
    }

    /** Every always-visible checkbox, its stored flag, and the value it must hold after one click. */
    private data class Toggle(val tag: String, val read: (SongSettings) -> Boolean, val afterClick: Boolean)

    private val toggles = listOf(
        Toggle("song_titleSlideEnabled", { it.titleSlideEnabled }, afterClick = true),
        Toggle("song_fadeIn", { it.fadeIn }, afterClick = false),
        Toggle("song_fadeOut", { it.fadeOut }, afterClick = false),
        Toggle("song_crossfade", { it.crossfade }, afterClick = true),
        Toggle("song_wordWrap", { it.wordWrap }, afterClick = true),
        Toggle("song_lyricsFontSizeAutoFit", { it.lyricsFontSizeAutoFit }, afterClick = false),
        Toggle("song_lyricsLowerThirdFontSizeAutoFit", { it.lyricsLowerThirdFontSizeAutoFit }, afterClick = false),
        Toggle("song_lookAheadFontSizeAutoFit", { it.lookAheadFontSizeAutoFit }, afterClick = false),
        Toggle("song_lookAheadNextFontSizeAutoFit", { it.lookAheadNextFontSizeAutoFit }, afterClick = false),
        Toggle("song_lowerThirdLookAheadFontSizeAutoFit", { it.lowerThirdLookAheadFontSizeAutoFit }, afterClick = false),
        Toggle("song_lowerThirdLookAheadNextFontSizeAutoFit", { it.lowerThirdLookAheadNextFontSizeAutoFit }, afterClick = false),
    )

    @Test
    fun `every checkbox flips its own setting when clicked`() = runTab { get ->
        for (toggle in toggles) {
            onNodeWithTag(toggle.tag).performScrollTo().performClick()
            waitForIdle()
            assertEquals(
                toggle.afterClick,
                toggle.read(get().songSettings),
                "clicking ${toggle.tag} must set its flag to ${toggle.afterClick}",
            )
        }
    }

    @Test
    fun `the number-before-title checkbox appears when positions align and flips its flag`() {
        // It's shown only when the song-number position/alignment matches the title's; line them up.
        val aligned = AppSettings().let {
            it.copy(
                songSettings = it.songSettings.copy(
                    songNumberPosition = it.songSettings.titlePosition,
                    songNumberHorizontalAlignment = it.songSettings.titleHorizontalAlignment,
                ),
            )
        }
        runTab(initial = aligned) { get ->
            assertEquals(true, get().songSettings.songNumberBeforeTitle, "default is on when shown")
            onNodeWithTag("song_songNumberBeforeTitle").performScrollTo().assertIsOn().performClick()
            waitForIdle()
            assertEquals(false, get().songSettings.songNumberBeforeTitle, "clicking it clears the flag")
            onNodeWithTag("song_songNumberBeforeTitle").assertIsOff()
        }
    }
}
