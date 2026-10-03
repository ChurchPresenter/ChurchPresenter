@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongListPaneDirectCoverage2Test {

    private val songs = listOf(
        SongItem(number = "1", title = "Grace", songbook = "Hymnal"),
        SongItem(number = "", title = "Untitled Hymn", songbook = "Hymnal"),
    )

    private class Seen {
        val byDetails = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        val queries = mutableListOf<String>()
    }

    @Composable
    private fun RowScope.Pane(v: Int, seen: Seen) {
        val columns = remember(v) {
            SongTableColumns(
                Density(1f),
                mapOf(SongColumnId.NUMBER to 40f, SongColumnId.TITLE to 120f),
                listOf(SongColumnId.FAVORITES, SongColumnId.NUMBER, SongColumnId.TITLE, SongColumnId.ADD_TO_SCHEDULE),
                emptySet(),
            )
        }
        val dialogs = remember(v) { SongDialogRequests() }
        val live = remember(v) { SongLiveState() }
        val focus = remember(v) { FocusRequester() }
        SongListPane(
            columns = columns,
            dialogs = dialogs,
            live = live,
            filteredSongs = if (v == 2) emptyList() else songs.take(1 + v % 2),
            selectedSongIndex = v % 2,
            searchQuery = if (v == 0) "" else "g",
            isLoading = v >= 2,
            isPresenting = v == 1,
            songbooks = listOf("Hymnal", "Choruses $v"),
            songbookOptions = listOf("All", "Hymnal", "Choruses $v"),
            selectedSongbook = "",
            allSongBooksText = "All",
            containsText = "Contains $v",
            filterType = if (v == 0) Constants.CONTAINS else "unknown",
            filterTypes = listOf("Contains $v"),
            filterTypeMap = mapOf("Contains $v" to Constants.CONTAINS),
            filterTypeDisplayMap = mapOf(Constants.CONTAINS to "Contains $v"),
            currentSortColumn = if (v == 0) "" else Constants.SORT_TITLE,
            currentSortAscending = v == 0,
            actionCols = setOf(SongColumnId.FAVORITES, SongColumnId.ADD_TO_SCHEDULE),
            availableCols = listOf(SongColumnId.NUMBER, SongColumnId.TITLE),
            visibleCols = listOf(SongColumnId.FAVORITES, SongColumnId.NUMBER, SongColumnId.TITLE),
            favorites = setOf(songs[0].songId, "v$v"),
            favoritesExpanded = true,
            favPanelHeightPx = 120f + v,
            tabFocusRequester = focus,
            favoriteSongs = { songs.take(1 + v % 2) },
            playCountFor = { v },
            searchMatchFor = { null },
            onSearchQueryChange = { seen.queries += "$v:$it" },
            onSearchFocusChanged = { v },
            onFilterTypeChange = { v },
            onSongbookChange = { v },
            onSortChange = { v },
            onSelectSong = { v },
            onSelectSongByDetails = { _, title, _, _ -> seen.byDetails += "$v:$title" },
            onSelectSection = { v },
            onToggleFavorite = { v },
            onClearFavorites = { v },
            onReloadSongs = { v },
            onSaveColumnWidths = { v },
            onSaveColumnOrder = { v },
            onSaveHiddenColumns = { v },
            onSaveFavPanelHeight = { v },
            onFavoritesExpandedChange = { v },
            onFavPanelHeightChange = { v },
            onAddToSchedule = { _, title, _, _ -> seen.scheduled += "$v:$title" },
            onPresenting = { v },
            sendToPresenter = { v },
        )
    }

    @Test
    fun `the pane recomposes with every value and callback changed, then with none`() = runComposeUiTest {
        val seen = Seen()
        var version by mutableStateOf(0)
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Text("tick $tick")
                Row(Modifier.fillMaxSize()) { Pane(version, seen) }
            }
        }
        waitForIdle()
        version = 1
        waitForIdle()
        tick++
        waitForIdle()

        onAllNodes(hasText("1. Grace"))[0].performClick()
        waitForIdle()
        assertEquals("1:Grace", seen.byDetails.last())

        onAllNodes(hasText("Untitled Hymn")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }

        val buttons = onAllNodes(hasContentDescription("Add to Schedule"), useUnmergedTree = true)
        buttons[buttons.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
        assertTrue(seen.scheduled.isNotEmpty())
    }

    @Test
    fun `while indexing an empty library shows progress, and a loaded one still lists its songs`() =
        runComposeUiTest {
            var version by mutableStateOf(2)
            setContent { MaterialTheme { Row(Modifier.fillMaxSize()) { Pane(version, Seen()) } } }
            waitForIdle()
            val indexing = onAllNodes(hasText("Indexing", substring = true)).fetchSemanticsNodes()
            assertTrue(indexing.isNotEmpty())

            version = 3
            waitForIdle()
            assertTrue(onAllNodes(hasText("Untitled Hymn")).fetchSemanticsNodes().isNotEmpty())
        }
}
