@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.languageDisplayOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Songs tab's language Display order (#650): offered while the selected song has more than one
 * language, and what a move in it changes.
 */
class SongsTabLanguageOrderTest {

    private val songs = listOf(
        SongFixture(number = "1", title = "Amazing Grace", secondaryLyrics = listOf("[Verse 1]", "gracia")),
        SongFixture(number = "2", title = "Be Thou My Vision"),
    )

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.shows(text: String) =
        onAllNodes(hasText(text, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a two-language song offers its languages, by the names they were given`() =
        songsTab(songs, songSettings = SongSettings(languageNames = listOf("English", "Español"))) { _, _ ->
            clickRow("Amazing Grace")

            assertTrue(shows("Languages"), "the Display order button is there")
            assertTrue(shows("English"), "led by the first language's name")
        }

    @Test
    fun `a song in one language offers no order`() = songsTab(songs) { _, _ ->
        clickRow("Be Thou My Vision")

        assertTrue(!shows("Languages"))
    }

    @Test
    fun `moving a language up in the panel changes the install's order`() =
        songsTab(songs, songSettings = SongSettings(languageNames = listOf("English", "Español"))) { _, reports ->
            clickRow("Amazing Grace")
            onAllNodes(hasText("Languages", substring = true, ignoreCase = true))[0].performClick()
            waitForIdle()

            // The second row's up arrow: Español ahead of English.
            onAllNodes(hasContentDescription("Move language up"))[1].performClick()
            waitForIdle()

            assertEquals(listOf(1, 0, 2, 3), reports.settingsAfterChange?.songSettings?.languageDisplayOrder())
        }
}
