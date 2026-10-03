@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongListPaneFiltersTest {

    private val onlyPlays = setOf(
        SongColumnId.SONGBOOK, SongColumnId.TUNE, SongColumnId.AUTHOR, SongColumnId.COMPOSER,
    )

    private val counts = SongPlayCounts { id ->
        when (id) {
            "Hymnal::1" -> 2
            "Hymnal::12" -> 7
            else -> 0
        }
    }

    private fun ComposeUiTest.clickLast(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openSongbooks() {
        onNodeWithText("SONG BOOK", substring = true).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.star(vm: SongsViewModel, title: String) {
        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == title }.songId)
        waitForIdle()
    }

    @Test
    fun `choosing a songbook narrows the list to it`() = songsTab { vm, _ ->
        openSongbooks()
        clickLast("Chorus Book")

        assertEquals("Chorus Book", vm.selectedSongbook.value)
        assertEquals(listOf("How Great Thou Art"), listedTitles())
    }

    @Test
    fun `choosing All Song Books lists every book again`() = songsTab { vm, _ ->
        openSongbooks()
        clickLast("Chorus Book")
        openSongbooks()
        clickLast(SongsLabel.ALL_SONGBOOKS)

        assertEquals(SongsLabel.ALL_SONGBOOKS, vm.selectedSongbook.value)
        assertEquals(4, listedTitles().size)
    }

    @Test
    fun `a library of one songbook offers no songbook filter`() =
        songsTab(songs = defaultSongs.filter { it.songbook == "Hymnal" }) { _, _ ->
            assertFalse(showsContaining("SONG BOOK"), rendered().toString())
            assertFalse(shows("Song Book"), "nor a songbook column")
        }

    @Test
    fun `the clear button empties the query and lists everything`() = songsTab { vm, _ ->
        search("Vision")
        assertEquals(listOf("Be Thou My Vision"), listedTitles())

        onNodeWithContentDescription("Clear search").performClick()
        waitForIdle()

        assertEquals("", vm.searchQuery.value)
        assertEquals(4, listedTitles().size)
    }

    @Test
    fun `an empty box offers no clear button`() = songsTab { _, _ ->
        assertTrue(
            onAllNodes(hasContentDescription("Clear search"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
        )
    }

    @Test
    fun `Enter in the search box hands the keyboard back and keeps the query`() =
        songsTab(searchIdleFocusMs = 600_000L) { vm, _ ->
            searchBox().performClick()
            waitForIdle()
            searchBox().assertIsFocused()
            search("Amazing")

            searchBox().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            searchBox().assertIsNotFocused()
            assertEquals("Amazing", vm.searchQuery.value)
        }

    @Test
    fun `the favorites header collapses the panel`() = songsTab { vm, _ ->
        star(vm, "Amazing Grace")
        assertTrue(shows("1. Amazing Grace"))

        onNodeWithText(SongsLabel.FAVORITES).performClick()
        waitForIdle()

        assertFalse(shows("1. Amazing Grace"), rendered().toString())
        assertTrue(shows(SongsLabel.FAVORITES), "the header stays")
    }

    @Test
    fun `the favorites header expands a collapsed panel again`() = songsTab { vm, _ ->
        star(vm, "Amazing Grace")
        onNodeWithText(SongsLabel.FAVORITES).performClick()
        waitForIdle()

        onNodeWithText(SongsLabel.FAVORITES).performClick()
        waitForIdle()

        assertTrue(shows("1. Amazing Grace"), rendered().toString())
    }

    @Test
    fun `dragging the favorites divider up makes the panel taller and saves it`() = songsTab { vm, reports ->
        star(vm, "Amazing Grace")
        val row = onNodeWithText("1. Amazing Grace").fetchSemanticsNode().boundsInRoot
        val y = row.top - with(density) { 3.dp.toPx() }
        val x = row.center.x

        onRoot().performMouseInput {
            moveTo(Offset(x, y))
            press()
            moveTo(Offset(x, y - 40f))
            moveTo(Offset(x, y - 80f))
            moveTo(Offset(x, y - 120f))
            release()
        }
        waitForIdle()

        val saved = assertNotNull(reports.settingsAfterChange?.songFavoritesPanelHeightDp, "a resize must be saved")
        assertTrue(saved > 120, "the panel starts at 120dp, was saved as $saved")
    }

    @Test
    fun `the plays column shows each song's count`() =
        songsTab(hiddenCols = onlyPlays, playCounts = counts) { _, _ ->
            assertTrue(shows("7"), rendered().toString())
        }

    @Test
    fun `a song never sung shows a blank count rather than zero`() =
        songsTab(hiddenCols = onlyPlays, playCounts = counts) { _, _ ->
            assertFalse(shows("0"), rendered().toString())
        }

    @Test
    fun `without play counts the plays column is blank`() = songsTab(hiddenCols = onlyPlays) { _, _ ->
        assertFalse(shows("7"))
    }

    @Test
    fun `sorting by plays puts the most-sung songs last, ascending`() =
        songsTab(hiddenCols = onlyPlays, playCounts = counts) { vm, _ ->
            onAllNodesWithText("Plays")[0].performClick()
            waitForIdle()

            assertEquals(Constants.SORT_PLAY_COUNT, vm.sortColumn.value)
            assertEquals(listOf("Amazing Grace", "Amazing Love"), listedTitles().takeLast(2))
        }

    @Test
    fun `sorting by plays again puts the most-sung songs first`() =
        songsTab(hiddenCols = onlyPlays, playCounts = counts) { _, _ ->
            onAllNodesWithText("Plays")[0].performClick()
            waitForIdle()
            onAllNodesWithText("Plays")[0].performClick()
            waitForIdle()

            assertEquals(listOf("Amazing Love", "Amazing Grace"), listedTitles().take(2))
        }
}
