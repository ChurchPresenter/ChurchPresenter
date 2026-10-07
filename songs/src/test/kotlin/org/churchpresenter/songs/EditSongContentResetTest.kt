@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertTrue

class EditSongContentResetTest {

    private fun song(number: String, title: String, lyrics: List<String> = listOf("[Verse 1]", "line of $title")) =
        SongItem(number = number, title = title, songbook = "Hymnal", lyrics = lyrics)

    private fun ComposeUiTest.has(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `a new song, tuning, names or reopening start the editor afresh`() = runComposeUiTest {
        var current by mutableStateOf(song("1", "Amazing Grace"))
        var tuning by mutableStateOf(SongTuning())
        var names by mutableStateOf(emptyList<String>())
        var visible by mutableStateOf(true)
        setContent {
            MaterialTheme {
                EditSongContent(
                    backgroundButton = testBackgroundButton,
                    song = current,
                    songbooks = listOf("Hymnal"),
                    existingSongs = emptyList(),
                    isNewSong = false,
                    theme = ThemeMode.LIGHT,
                    tuning = tuning,
                    showTuningFields = true,
                    isVisible = visible,
                    languageNames = names,
                    onLanguageNamesChange = {},
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }
        waitForIdle()
        assertTrue(has("Amazing Grace"))

        current = song("2", "Be Thou My Vision", listOf("[G]Be thou my [C]vision"))
        waitForIdle()
        assertTrue(has("Be Thou My Vision"))

        tuning = SongTuning(bpm = 96, capo = 2)
        waitForIdle()
        names = listOf("English", "Español")
        waitForIdle()
        visible = false
        waitForIdle()
        visible = true
        waitForIdle()

        assertTrue(has("Be Thou My Vision"))
    }

    @Test
    fun `a hidden dialog, or one with no song, draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val hidden = song("1", "Hidden")
                EditSongDialog(
                    isVisible = false, song = hidden, backgroundButton = testBackgroundButton,
                    theme = ThemeMode.LIGHT, onDismiss = {},
                ) { _, _ -> }
                EditSongDialog(
                    isVisible = true, song = null, backgroundButton = testBackgroundButton,
                    theme = ThemeMode.LIGHT, onDismiss = {},
                ) { _, _ -> }
            }
        }
        waitForIdle()

        assertTrue(!has("Hidden"))
    }
}
