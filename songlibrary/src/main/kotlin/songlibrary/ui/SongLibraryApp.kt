package songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import core.models.songs.SongItem
import java.io.File
import songlibrary.SongLibraryState
import songlibrary.generated.resources.delete

/**
 * The Song Library Manager: every song in the library folder in one grid, editable in place.
 *
 * The library is a folder of `.song` files and a person's only view of it is otherwise one song at
 * a time, so anything that spans songs — renumbering a book, filling in a missing composer, moving
 * a set into a new song book — means opening each of them. This is that work in one screen: type in
 * a cell, tick a row, and nothing touches the disk until Save.
 */
@Composable
fun SongLibraryApp(
    libraryFolder: File,
    onClose: (() -> Unit)? = null,
    /**
     * The editor a row's Edit button opens, supplied by whoever hosts this window.
     *
     * Inside ChurchPresenter that is the app's own Edit Song dialog, the same one the Songs tab
     * opens — one editor for a song, wherever it is opened from. Standalone there is no app to ask,
     * so the plain one below stands in.
     */
    songEditor: (@Composable (editing: SongEditorRequest) -> Unit)? = null,
) {
    val state = remember(libraryFolder) { SongLibraryState(libraryFolder) }
    LaunchedEffect(libraryFolder) { state.reloadAsync() }

    var newBookOpen by remember { mutableStateOf(false) }
    var batchOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LibraryHeader(state, onNewBook = { newBookOpen = true })
        if (state.selected.isNotEmpty()) {
            BulkBar(
                state = state,
                onBatchEdit = { batchOpen = true },
                onDelete = { pendingDelete = state.selectedSongs() },
            )
        }
        SongTable(
            state = state,
            modifier = Modifier.weight(1f),
            onEditRow = { state.editing = it.sourceFile },
            onDeleteRow = { pendingDelete = listOf(it) },
            onNewBook = { newBookOpen = true },
        )
        LibraryFooter(state, onClose)
    }

    if (newBookOpen) {
        NewSongBookDialog(
            existing = state.songbooks,
            selectedCount = state.selected.size,
            onDismiss = { newBookOpen = false },
            onCreate = { name, assign ->
                state.createSongbook(name, assign)
                newBookOpen = false
            },
        )
    }

    if (batchOpen) {
        BatchEditDialog(
            count = state.selected.size,
            songbooks = state.songbooks,
            onDismiss = { batchOpen = false },
            onApply = { fields ->
                state.editAll(fields)
                batchOpen = false
            },
        )
    }

    if (pendingDelete.isNotEmpty()) {
        DeleteConfirmDialog(
            songs = pendingDelete,
            onDismiss = { pendingDelete = emptyList() },
            onConfirm = {
                state.delete(pendingDelete)
                pendingDelete = emptyList()
            },
        )
    }

    state.editing?.let { sourceFile ->
        state.songOf(sourceFile)?.let { song ->
            val request = SongEditorRequest(
                song = song,
                songbooks = state.songbooks,
                allSongs = state.songs,
                onSave = {
                    state.replace(it)
                    state.editing = null
                },
                onDismiss = { state.editing = null },
            )
            if (songEditor != null) songEditor(request) else SongEditorDialog(request)
        }
    }
}
