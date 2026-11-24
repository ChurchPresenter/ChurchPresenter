package org.churchpresenter.songs

import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Reorder
import org.churchpresenter.settings.SongColumnId
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import org.churchpresenter.strings.generated.resources.song_columns_show_in_list
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
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.field.DropdownMenu
import org.churchpresenter.hosanna.composables.model.MenuEntry
import org.churchpresenter.strings.generated.resources.toggle_menu_always
import org.churchpresenter.strings.generated.resources.toggle_menu_count
import org.churchpresenter.strings.generated.resources.toggle_menu_show_all
import androidx.compose.foundation.layout.offset

/** The floating column filter button, on the right. */
@Composable
internal fun SongListScope.SongColumnsButton(
    allColLabels: Map<String, String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        val columnsStr = stringResource(Res.string.song_columns)
        Tooltip(columnsStr) {
            IconToggleButton(
                rememberVectorPainter(Icons.Default.Tune),
                columnsStr,
                expanded,
                { onExpandedChange(true) },
                hierarchy = ButtonHierarchy.Tertiary,
            )
        }
        if (expanded) ColumnsMenu(allColLabels) { onExpandedChange(false) }
    }
}

/** Right-click menu — toggle column visibility, opened where the click was. */
@Composable
internal fun SongListScope.SongColumnsContextMenu(
    allColLabels: Map<String, String>,
    expanded: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit,
) {
    if (!expanded) return
    Box(Modifier.offset(offset.x, offset.y)) { ColumnsMenu(allColLabels, onDismiss) }
}

/**
 * A count header and Show all, then one checkable line per column with the column's own icon; the
 * row buttons (Add to Schedule, Favorites) follow the data columns after a divider. Title cannot be
 * hidden, so it is shown ticked and locked rather than offered as a check that does nothing. The
 * menu stays open while columns are ticked.
 */
@Composable
private fun SongListScope.ColumnsMenu(allColLabels: Map<String, String>, onDismiss: () -> Unit) {
    val dataCols = availableCols.filter { it !in actionCols }
    val rowButtonCols = availableCols.filter { it in actionCols }
    val shown = availableCols.count { it !in columns.hidden }
    val always = stringResource(Res.string.toggle_menu_always)

    @Composable
    fun line(colId: String): MenuEntry.Item {
        val visible = colId !in columns.hidden
        // A Title hidden by an older version can still be turned back on.
        val locked = colId == SongColumnId.TITLE && visible
        return MenuEntry.Item(
            key = colId,
            label = allColLabels[colId] ?: colId,
            count = if (locked) always else null,
            enabled = !locked,
            checked = visible,
            icon = songColumnPainter(colId),
        )
    }
    val entries = buildList {
        add(
            MenuEntry.Header(
                stringResource(Res.string.song_columns_show_in_list) + "  " +
                    stringResource(Res.string.toggle_menu_count, shown, availableCols.size),
            ),
        )
        add(
            MenuEntry.Item(
                SHOW_ALL_KEY,
                stringResource(Res.string.toggle_menu_show_all),
                enabled = shown < availableCols.size,
            ),
        )
        dataCols.forEach { add(line(it)) }
        if (dataCols.isNotEmpty() && rowButtonCols.isNotEmpty()) add(MenuEntry.Divider)
        rowButtonCols.forEach { add(line(it)) }
    }
    DropdownMenu(
        entries = entries,
        onPick = { key ->
            columns.hidden = when (key) {
                SHOW_ALL_KEY -> columns.hidden - availableCols.toSet()
                in columns.hidden -> columns.hidden - key
                else -> columns.hidden + key
            }
            onSaveHiddenColumns()
        },
        onDismiss = onDismiss,
        width = COLUMNS_MENU_WIDTH,
    )
}

private const val SHOW_ALL_KEY = "\u0000showAll"
private val COLUMNS_MENU_WIDTH = 240.dp

/** The icon for a column in the menu; the row-button columns use the icon their header draws. */
@Composable
private fun songColumnPainter(colId: String): Painter = when (colId) {
    SongColumnId.NUMBER -> rememberVectorPainter(Icons.Default.Tag)
    SongColumnId.ALIAS -> rememberVectorPainter(Icons.AutoMirrored.Filled.Label)
    SongColumnId.TITLE -> rememberVectorPainter(Icons.Default.Title)
    SongColumnId.SONGBOOK -> rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)
    SongColumnId.TUNE -> rememberVectorPainter(Icons.Default.MusicNote)
    SongColumnId.PLAY_COUNT -> rememberVectorPainter(Icons.Default.PlayArrow)
    SongColumnId.AUTHOR -> rememberVectorPainter(Icons.Default.Person)
    SongColumnId.COMPOSER -> rememberVectorPainter(Icons.Default.LibraryMusic)
    SongColumnId.ORDER -> rememberVectorPainter(Icons.Default.Reorder)
    SongColumnId.ADD_TO_SCHEDULE -> painterResource(IconRes.drawable.ic_playlist_add)
    SongColumnId.FAVORITES -> painterResource(IconRes.drawable.ic_star)
    else -> rememberVectorPainter(Icons.Default.Tune)
}
