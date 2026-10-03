package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Cursor
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.sharedui.models.Presenting
import androidx.compose.ui.unit.Density

/**
 * Everything the song list's pieces read, for one composition: the pane's parameters and the column
 * helpers they share.
 */
@Suppress("LongParameterList")
internal class SongListScope(
    val columns: SongTableColumns,
    val dialogs: SongDialogRequests,
    val live: SongLiveState,
    val filteredSongs: List<SongItem>,
    val selectedSongIndex: Int,
    val searchQuery: String,
    val isLoading: Boolean,
    val isPresenting: Boolean,
    val songbooks: List<String>,
    val songbookOptions: List<String>,
    val selectedSongbook: String,
    val allSongBooksText: String,
    val containsText: String,
    val filterType: String,
    val filterTypes: List<String>,
    val filterTypeMap: Map<String, String>,
    val filterTypeDisplayMap: Map<String, String>,
    val currentSortColumn: String,
    val currentSortAscending: Boolean,
    val actionCols: Set<String>,
    val availableCols: List<String>,
    val visibleCols: List<String>,
    val favorites: Set<String>,
    val favoritesExpanded: Boolean,
    val favPanelHeightPx: Float,
    val tabFocusRequester: FocusRequester,
    val favoriteSongs: () -> List<SongItem>,
    val playCountFor: (String) -> Int?,
    /** Where the current search found a song, for the line under its row; null draws no line. */
    val searchMatchFor: (SongItem) -> SongSearchMatch?,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchFocusChanged: (Boolean) -> Unit,
    val onFilterTypeChange: (String) -> Unit,
    val onSongbookChange: (String) -> Unit,
    val onSortChange: (String) -> Unit,
    val onSelectSong: (Int) -> Unit,
    val onSelectSongByDetails: (number: Int, title: String, songbook: String, songId: String) -> Unit,
    val onSelectSection: (Int) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onClearFavorites: () -> Unit,
    val onReloadSongs: () -> Unit,
    val onSaveColumnWidths: () -> Unit,
    val onSaveColumnOrder: () -> Unit,
    val onSaveHiddenColumns: () -> Unit,
    val onSaveFavPanelHeight: () -> Unit,
    val onFavoritesExpandedChange: (Boolean) -> Unit,
    val onFavPanelHeightChange: (Float) -> Unit,
    val onAddToSchedule: ((Int, String, String, String) -> Unit)?,
    val onPresenting: (Presenting) -> Unit,
    val sendToPresenter: (goLive: Boolean) -> Unit,
    val density: Density,
) {
    fun colWidth(id: String) = columns.widthOf(id)

    fun setColWidth(id: String, px: Float) = columns.setWidth(id, px)

    fun sortKey(id: String) = songColumnSortKey(id)

    // NOTE: operates on visibleCols (set after state vars), returns index within visibleCols
    fun computeNewIdx(draggedId: String, accumX: Float, visibleCols: List<String>): Int =
        draggedColumnIndex(
            draggedId, accumX, visibleCols,
            columnWidthPx = ::colWidth,
            handleWidthPx = with(density) { 6.dp.toPx() },
        )

    @Composable
    fun DragHandle(colId: String, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        val currentOnDrag by rememberUpdatedState(onDrag)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(colId) {
                    detectHorizontalDragGestures(onDragEnd = { currentOnDragEnd() }) { _, amount ->
                        currentOnDrag(amount)
                    }
                }
        )
    }
}
