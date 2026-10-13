package org.churchpresenter.songs

import androidx.compose.foundation.background
import org.churchpresenter.settings.SongColumnId
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Title
import androidx.compose.ui.graphics.painter.Painter
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ContextMenuDivider
import org.churchpresenter.sharedui.composables.ToggleMenuHeader
import org.churchpresenter.sharedui.composables.ToggleMenuItem
import org.churchpresenter.sharedui.composables.ToolbarKey
import org.churchpresenter.sharedui.composables.ToolbarKeyStyle
import org.churchpresenter.strings.generated.resources.song_columns_show_in_list
import org.churchpresenter.theme.semantic
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.filter
import org.churchpresenter.icons.generated.resources.ic_star
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.strings.generated.resources.song_columns
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.bibleListCardFill

/** The floating column filter button, on the right. */
@Composable
internal fun SongListScope.SongColumnsButton(
    allColLabels: Map<String, String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    // Floating column filter button — right side
    Box(modifier = modifier.background(bibleListCardFill())) {
        // Styled as the schedule's options button and the tab bar's: all three open a toggle menu.
        ToolbarKey(
            painter = rememberVectorPainter(Icons.Default.Tune),
            text = stringResource(Res.string.song_columns),
            onClick = { onExpandedChange(true) },
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            open = expanded,
            buttonSize = 36.dp,
        )
        ContextMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            width = COLUMNS_MENU_WIDTH,
        ) {
            ColumnVisibilityItems(allColLabels)
        }
    }
}

/** Right-click dropdown — toggle column visibility. */
@Composable
internal fun SongListScope.SongColumnsContextMenu(
    allColLabels: Map<String, String>,
    expanded: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit,
) {
    ContextMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        width = COLUMNS_MENU_WIDTH,
    ) {
        ColumnVisibilityItems(allColLabels)
    }
}

private val COLUMNS_MENU_WIDTH = 240.dp

/**
 * A count header, then one item per column with the column's own icon; the row buttons (Add to
 * Schedule, Favorites) follow the data columns after a divider. Title cannot be hidden, so it is
 * locked rather than offered as a check that does nothing.
 */
@Composable
private fun SongListScope.ColumnVisibilityItems(allColLabels: Map<String, String>) {
    val dataCols = availableCols.filter { it !in actionCols }
    val rowButtonCols = availableCols.filter { it in actionCols }
    ToggleMenuHeader(
        title = stringResource(Res.string.song_columns_show_in_list),
        shown = availableCols.count { it !in columns.hidden },
        total = availableCols.size,
        onShowAll = {
            columns.hidden = columns.hidden - availableCols.toSet()
            onSaveHiddenColumns()
        },
    )
    dataCols.forEach { ColumnVisibilityItem(it, allColLabels) }
    if (dataCols.isNotEmpty() && rowButtonCols.isNotEmpty()) ContextMenuDivider()
    rowButtonCols.forEach { ColumnVisibilityItem(it, allColLabels) }
}

@Composable
private fun SongListScope.ColumnVisibilityItem(colId: String, allColLabels: Map<String, String>) {
    val isVisible = colId !in columns.hidden
    ToggleMenuItem(
        label = allColLabels[colId] ?: colId,
        checked = isVisible,
        onCheckedChange = { visible ->
            columns.hidden = if (visible) columns.hidden - colId else columns.hidden + colId
            onSaveHiddenColumns()
        },
        icon = songColumnPainter(colId),
        accent = songColumnAccent(colId),
        // A Title hidden by an older version can still be turned back on.
        locked = colId == SongColumnId.TITLE && isVisible,
    )
}

/** The icon for a column in the menu; the row-button columns use the icon their header draws. */
@Composable
private fun songColumnPainter(colId: String): Painter = when (colId) {
    SongColumnId.NUMBER -> rememberVectorPainter(Icons.Default.Tag)
    SongColumnId.TITLE -> rememberVectorPainter(Icons.Default.Title)
    SongColumnId.SONGBOOK -> rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)
    SongColumnId.TUNE -> rememberVectorPainter(Icons.Default.MusicNote)
    SongColumnId.PLAY_COUNT -> rememberVectorPainter(Icons.Default.PlayArrow)
    SongColumnId.AUTHOR -> rememberVectorPainter(Icons.Default.Person)
    SongColumnId.COMPOSER -> rememberVectorPainter(Icons.Default.LibraryMusic)
    SongColumnId.ADD_TO_SCHEDULE -> painterResource(IconRes.drawable.ic_playlist_add)
    SongColumnId.FAVORITES -> painterResource(IconRes.drawable.ic_star)
    else -> rememberVectorPainter(Icons.Default.Tune)
}

/** Each column takes its own hue, so the list is easy to scan. */
@Composable
private fun songColumnAccent(colId: String): Color {
    val semantic = MaterialTheme.semantic
    return when (colId) {
        SongColumnId.NUMBER, SongColumnId.TITLE -> semantic.contentBible
        SongColumnId.SONGBOOK -> semantic.contentPictures
        SongColumnId.TUNE -> semantic.contentSongs
        SongColumnId.PLAY_COUNT -> semantic.contentLowerThird
        SongColumnId.AUTHOR -> semantic.contentCalendar
        SongColumnId.COMPOSER -> semantic.contentPresentation
        SongColumnId.ADD_TO_SCHEDULE -> semantic.success
        SongColumnId.FAVORITES -> semantic.favorite
        else -> MaterialTheme.colorScheme.primary
    }
}
