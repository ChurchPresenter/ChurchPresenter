@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongLyricsPanelStatesTest {

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.has(matcher: androidx.compose.ui.test.SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private val searchHint = "Searching —"

    private val bilingual = listOf(
        SongFixture(
            number = "1",
            title = "Amazing Grace",
            lyrics = listOf("[Verse 1]", "first line", "second line"),
            secondaryTitle = "Gnade",
            secondaryLyrics = listOf("[Verse 1]", "erste Zeile", "zweite Zeile"),
        ),
    )

    @Test
    fun `an empty library shows the no-database card`() = songsTab(songs = emptyList()) { _, _ ->
        assertTrue(shows("No Song Database Configured"), rendered().toString())
    }

    @Test
    fun `an empty library offers a new song but nothing to edit`() = songsTab(songs = emptyList()) { _, _ ->
        assertTrue(has(hasContentDescription("New Song")))
        assertFalse(has(hasContentDescription("Edit Song")))
    }

    @Test
    fun `a search matching nothing says there are no lyrics rather than no database`() = songsTab { _, _ ->
        search("zzzz-nothing")

        assertTrue(shows("No lyrics available for this song"), rendered().toString())
        assertFalse(shows("No Song Database Configured"))
    }

    @Test
    fun `a song with no lyrics says so`() =
        songsTab(songs = listOf(SongFixture(number = "9", title = "Silent", lyrics = emptyList()))) { _, _ ->
            clickRow("Silent")

            assertTrue(shows("No lyrics available for this song"), rendered().toString())
        }

    @Test
    fun `with nowhere to schedule the selected song has no add button`() =
        songsTab(withOnAddToSchedule = false) { _, _ ->
            clickRow("Amazing Grace")

            assertFalse(has(hasTestTag(SONGS_ADD_SELECTED_TAG)))
        }

    @Test
    fun `the caret in the search box raises the hint, and clicking it hands the keys back`() = songsTab { _, _ ->
        searchBox().performClick()
        waitForIdle()
        assertTrue(has(hasText(searchHint, substring = true)))

        onNodeWithText(searchHint, substring = true).performClick()
        waitForIdle()

        assertFalse(has(hasText(searchHint, substring = true)))
    }

    @Test
    fun `the navigation hint is not shown in verse mode`() = songsTab(songSettings = verseMode()) { _, _ ->
        clickRow("Amazing Grace")

        assertFalse(has(hasText("to navigate lines", substring = true)))
    }

    @Test
    fun `a two-language song shows both languages side by side`() = songsTab(songs = bilingual) { _, _ ->
        clickRow("Amazing Grace")

        assertTrue(shows("first line"))
        assertTrue(shows("erste Zeile"))
    }

    @Test
    fun `clicking a line in the second language selects that line`() = songsTab(songs = bilingual) { vm, reports ->
        clickRow("Amazing Grace")
        onNodeWithText("zweite Zeile").performClick()
        waitForIdle()

        assertEquals(1, vm.selectedLineIndex.value)
        assertEquals(1, reports.lineIndex)
    }

    @Test
    fun `a title slide for a song with no author shows only its heading`() =
        songsTab(
            songs = listOf(SongFixture(number = "9", title = "Lonely")),
            songSettings = SongSettings(titleSlideEnabled = true),
        ) { _, _ ->
            clickRow("Lonely")

            assertTrue(shows("9 – Lonely"), rendered().toString())
        }

    @Test
    fun `the title slide needs lyrics to be offered`() =
        songsTab(
            songs = listOf(SongFixture(number = "9", title = "Silent", lyrics = emptyList())),
            songSettings = SongSettings(titleSlideEnabled = true),
        ) { _, _ ->
            clickRow("Silent")

            assertFalse(shows("SONG TITLE SLIDE"))
        }
}
