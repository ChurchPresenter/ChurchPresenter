@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hiding a language from the Songs tab's lyrics list (#1024): the eye on each row of its Languages
 * menu, saved for the install, and the list leaving that language out while the outputs keep it.
 */
class SongsTabPanelLanguagesTest {

    private val songs = listOf(
        SongFixture(
            number = "1",
            title = "Amazing Grace",
            lyrics = listOf("[Verse 1]", "how sweet the sound"),
            secondaryLyrics = listOf("[Verse 1]", "que dulce es"),
        ),
    )

    private val named = SongSettings(languageNames = listOf("English", "Español"))

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openMenu() {
        onAllNodes(hasText("Languages", substring = true, ignoreCase = true))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.eye(name: String) = onNode(hasContentDescription("Show in the lyrics list: $name"))

    @Test
    fun `the list shows every language until one is hidden`() = songsTab(songs, songSettings = named) { _, _ ->
        clickRow("Amazing Grace")

        assertTrue(showsContaining("how sweet the sound"), rendered().toString())
        assertTrue(showsContaining("que dulce es"))
    }

    @Test
    fun `the eye hides a language for the whole install`() = songsTab(songs, songSettings = named) { _, reports ->
        clickRow("Amazing Grace")
        openMenu()

        eye("Español").performClick()
        waitForIdle()

        assertEquals(listOf(1), reports.settingsAfterChange?.songPanelHiddenLanguages)
        assertEquals(
            named.languageOrder,
            reports.settingsAfterChange?.songSettings?.languageOrder,
            "what the outputs show is untouched",
        )
    }

    @Test
    fun `a hidden language leaves the list`() =
        songsTab(songs, songSettings = named, panelHiddenLanguages = listOf(1)) { _, _ ->
            clickRow("Amazing Grace")

            assertTrue(showsContaining("how sweet the sound"), rendered().toString())
            assertFalse(showsContaining("que dulce es"), rendered().toString())
        }

    @Test
    fun `the primary can be hidden, leaving the other language`() =
        songsTab(songs, songSettings = named, panelHiddenLanguages = listOf(0)) { _, _ ->
            clickRow("Amazing Grace")

            assertFalse(showsContaining("how sweet the sound"), rendered().toString())
            assertTrue(showsContaining("que dulce es"))
        }

    @Test
    fun `the last language shown cannot be hidden`() =
        songsTab(songs, songSettings = named, panelHiddenLanguages = listOf(1)) { _, reports ->
            clickRow("Amazing Grace")
            openMenu()

            eye("English").assertIsNotEnabled()
            eye("English").performClick()
            waitForIdle()

            assertEquals(null, reports.settingsAfterChange, "nothing is saved")
        }

    @Test
    fun `a hidden language is shown again from its eye`() =
        songsTab(songs, songSettings = named, panelHiddenLanguages = listOf(1)) { _, reports ->
            clickRow("Amazing Grace")
            openMenu()

            eye("Español").performClick()
            waitForIdle()

            assertEquals(emptyList(), reports.settingsAfterChange?.songPanelHiddenLanguages)
        }
}
