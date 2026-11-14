@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import org.churchpresenter.settings.languageLabel
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.languageDisplayOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Songs tab's language Display order (#650): always offered, since languages are named and
 * added there too, and what a move in it changes.
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
    fun `a song in one language still offers the menu, where languages are added and named`() =
        songsTab(songs) { _, _ ->
            clickRow("Be Thou My Vision")

            assertTrue(shows("Languages"))
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

    // ── Naming and adding languages ─────────────────────────────────────────────────────────────

    private fun ComposeUiTest.openMenu() {
        onAllNodes(hasText("Languages", substring = true, ignoreCase = true))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.nameField(index: Int) =
        onAllNodes(hasSetTextAction() and hasContentDescription("Language name"))[index]

    private fun ComposeUiTest.nameFieldCount() =
        onAllNodes(hasSetTextAction() and hasContentDescription("Language name")).fetchSemanticsNodes().size

    @Test
    fun `a language renamed in the menu is named for the whole install`() =
        songsTab(songs, songSettings = SongSettings(languageNames = listOf("English"))) { _, reports ->
            clickRow("Amazing Grace")
            openMenu()

            nameField(1).performClick()
            nameField(1).performTextReplacement("Español")
            nameField(0).performClick()
            waitForIdle()

            assertEquals("Español", reports.settingsAfterChange?.songSettings?.languageLabel(1))
            assertEquals("English", reports.settingsAfterChange?.songSettings?.languageLabel(0))
        }

    @Test
    fun `a language is added from the menu with no song written in it yet`() = songsTab(songs) { _, _ ->
        clickRow("Be Thou My Vision")
        openMenu()
        assertEquals(1, nameFieldCount(), "a one-language song lists only its own")

        onAllNodes(hasText("Add a language"))[0].performClick()
        waitForIdle()

        assertEquals(2, nameFieldCount(), "the added language is there to name")
        assertTrue(shows("Language 2"))
    }

    @Test
    fun `every named language is listed, whatever song is selected`() =
        songsTab(songs, songSettings = SongSettings(languageNames = listOf("English", "", "Deutsch"))) { _, _ ->
            clickRow("Be Thou My Vision")
            openMenu()

            assertEquals(3, nameFieldCount(), "up to the last named language, a blank one in between included")
        }

    @Test
    fun `no more languages can be added than a song can hold`() =
        songsTab(songs, songSettings = SongSettings(languageNames = listOf("A", "B", "C", "D"))) { _, _ ->
            clickRow("Be Thou My Vision")
            openMenu()

            assertEquals(4, nameFieldCount())
            assertTrue(!shows("Add a language"))
        }
}
