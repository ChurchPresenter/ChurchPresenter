package songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongField
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import songlibrary.OPTIONAL_COLUMNS
import songlibrary.SongLibraryState
import songlibrary.generated.resources.Res
import songlibrary.generated.resources.all_song_books
import songlibrary.generated.resources.batch_edit_menu
import songlibrary.generated.resources.clear
import songlibrary.generated.resources.columns
import songlibrary.generated.resources.columns_always
import songlibrary.generated.resources.columns_show_all
import songlibrary.generated.resources.delete
import songlibrary.generated.resources.done
import songlibrary.generated.resources.footer_songs
import songlibrary.generated.resources.loading
import songlibrary.generated.resources.new_song
import songlibrary.generated.resources.new_song_book_menu
import songlibrary.generated.resources.no_song_book
import songlibrary.generated.resources.revert
import songlibrary.generated.resources.save_changes
import songlibrary.generated.resources.save_failed
import songlibrary.generated.resources.search_placeholder
import songlibrary.generated.resources.selected_count
import songlibrary.generated.resources.subhead_counts
import songlibrary.generated.resources.subhead_filtered
import songlibrary.generated.resources.unsaved_changes
import songlibrary.generated.resources.window_title

// The frame around the table: the header and its filters, the selection bar, the footer.

@Composable
internal fun LibraryHeader(state: SongLibraryState, io: CoroutineDispatcher, onNewBook: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.window_title), style = LibraryType.title, color = scheme.onSurface)
                Text(subhead(state), style = LibraryType.small, color = scheme.onSurfaceVariant)
            }

            SearchField(state.view.query) { state.view = state.view.copy(query = it) }
            SongBookFilter(state, onNewBook)
            ColumnsMenu(state)
            val newSongTitle = stringResource(Res.string.new_song)
            val scope = rememberCoroutineScope()
            PrimaryButton(
                label = newSongTitle,
                enabled = !state.isWriting,
                onClick = { scope.launch { state.newSong(newSongTitle, io) } },
                icon = { Icon(Icons.Default.Add, null, Modifier.size(13.dp), tint = scheme.onPrimary) },
            )
        }
        Hairline()
    }
}

@Composable
private fun subhead(state: SongLibraryState): String =
    if (state.isLoading) stringResource(Res.string.loading)
    else if (state.view.isFiltered) stringResource(Res.string.subhead_filtered, state.rows.size, state.songs.size)
    else stringResource(Res.string.subhead_counts, state.songs.size, state.songbooks.size)

@Composable
internal fun BulkBar(state: SongLibraryState, onBatchEdit: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.selected_count, state.selected.size),
                style = LibraryType.bodyStrong,
                color = scheme.primary,
            )
            Box(Modifier.width(1.dp).height(16.dp).background(scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)))
            PrimaryButton(
                label = stringResource(Res.string.batch_edit_menu),
                onClick = onBatchEdit,
                icon = { Icon(Icons.Default.Edit, null, Modifier.size(12.dp), tint = scheme.onPrimary) },
            )
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(Res.string.delete), onClick = onDelete, danger = true)
            Text(
                stringResource(Res.string.clear),
                style = LibraryType.button,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { state.clearSelection() }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)))
    }
}

@Composable
internal fun LibraryFooter(state: SongLibraryState, io: CoroutineDispatcher, onClose: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(scheme.surfaceContainer).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.footer_songs, state.songs.size),
                style = LibraryType.small,
                color = scheme.onSurfaceVariant,
            )
            if (state.isDirty) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.semantic.warning))
                    Text(
                        stringResource(Res.string.unsaved_changes, state.changedCount),
                        style = LibraryType.button,
                        color = MaterialTheme.semantic.warning,
                    )
                }
            }
            state.lastOutcome?.errors?.firstOrNull()?.let {
                Text(
                    stringResource(Res.string.save_failed, it),
                    style = LibraryType.small,
                    color = scheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            val scope = rememberCoroutineScope()
            QuietButton(
                stringResource(Res.string.revert),
                onClick = { state.revert() },
                enabled = state.isDirty && !state.isWriting,
            )
            PrimaryButton(
                stringResource(Res.string.save_changes),
                onClick = { scope.launch { state.save(io) } },
                enabled = state.isDirty && !state.isWriting,
            )
            if (onClose != null) {
                Box(Modifier.width(1.dp).height(20.dp).background(scheme.outlineVariant))
                QuietButton(stringResource(Res.string.done), onClick = onClose)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.width(236.dp)
            .height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(LibraryMetrics.radius))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            null,
            tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(7.dp))
        PlainTextField(
            value = value,
            onValueChange = onChange,
            placeholder = stringResource(Res.string.search_placeholder),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SongBookFilter(state: SongLibraryState, onNewBook: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = when (val book = state.view.songbook) {
        null -> stringResource(Res.string.all_song_books)
        "" -> stringResource(Res.string.no_song_book)
        else -> book
    }
    LibraryDropdown(label = label, highlighted = state.view.songbook != null, menuWidth = 250.dp) { close ->
        MenuRow(
            stringResource(Res.string.all_song_books),
            selected = state.view.songbook == null,
            count = state.songs.size,
        ) {
            state.view = state.view.copy(songbook = null)
            close()
        }
        MenuRow(
            stringResource(Res.string.no_song_book),
            selected = state.view.songbook == "",
            count = state.counts[""] ?: 0,
        ) {
            state.view = state.view.copy(songbook = "")
            close()
        }
        MenuDivider()
        state.songbooks.forEach { book ->
            MenuRow(book, selected = state.view.songbook == book, count = state.counts[book] ?: 0) {
                state.view = state.view.copy(songbook = book)
                close()
            }
        }
        MenuDivider()
        MenuRow(
            label = stringResource(Res.string.new_song_book_menu),
            accent = true,
            leading = { Icon(Icons.Default.Add, null, tint = scheme.primary, modifier = Modifier.size(11.dp)) },
        ) {
            close()
            onNewBook()
        }
    }
}

@Composable
private fun ColumnsMenu(state: SongLibraryState) {
    val scheme = MaterialTheme.colorScheme
    LibraryDropdown(
        label = stringResource(Res.string.columns),
        highlighted = state.hiddenColumns.isNotEmpty(),
        menuWidth = 224.dp,
        leading = {
            Icon(Icons.Default.ViewColumn, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        },
    ) { _ ->
        MenuRow(stringResource(Res.string.columns_show_all), accent = true) { state.showAllColumns() }
        MenuDivider()
        // The title is always shown: a row identified only by its number is unreadable.
        MenuRow(
            label = columnLabel(SongField.TITLE),
            leading = { LibraryCheckbox(checked = true) },
            trailing = {
                Text(
                    stringResource(Res.string.columns_always),
                    style = LibraryType.columnHead,
                    color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                )
            },
            onClick = null,
        )
        OPTIONAL_COLUMNS.forEach { field ->
            MenuRow(
                label = columnLabel(field),
                leading = { LibraryCheckbox(checked = field !in state.hiddenColumns) },
            ) { state.toggleColumn(field) }
        }
    }
}
