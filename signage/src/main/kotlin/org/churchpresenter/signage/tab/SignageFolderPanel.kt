package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.ZoneLabel
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_add
import org.churchpresenter.strings.generated.resources.signage_add_file
import org.churchpresenter.strings.generated.resources.signage_add_folder
import org.churchpresenter.strings.generated.resources.signage_add_link
import org.churchpresenter.strings.generated.resources.signage_choose_file
import org.churchpresenter.strings.generated.resources.signage_file_missing
import org.churchpresenter.strings.generated.resources.signage_pictures_and_videos
import org.churchpresenter.strings.generated.resources.signage_remove
import org.churchpresenter.strings.generated.resources.signage_remove_playlist
import org.churchpresenter.strings.generated.resources.signage_remove_playlist_text
import org.churchpresenter.strings.generated.resources.signage_remove_playlist_title
import org.churchpresenter.strings.generated.resources.signage_link_title_edit
import org.churchpresenter.strings.generated.resources.signage_file_title_edit
import org.churchpresenter.strings.generated.resources.signage_rotation_title_edit
import org.churchpresenter.strings.generated.resources.signage_add_rotation
import org.churchpresenter.strings.generated.resources.signage_rotation_summary
import org.churchpresenter.strings.generated.resources.signage_rotation_minutes
import org.churchpresenter.strings.generated.resources.signage_rotation_seconds_short
import org.churchpresenter.strings.generated.resources.signage_change_folder
import org.churchpresenter.strings.generated.resources.signage_choose_folder
import org.churchpresenter.strings.generated.resources.signage_delete
import org.churchpresenter.strings.generated.resources.signage_delete_failed
import org.churchpresenter.strings.generated.resources.signage_delete_folder_trash
import org.churchpresenter.strings.generated.resources.signage_delete_for_good
import org.churchpresenter.strings.generated.resources.signage_delete_playlist
import org.churchpresenter.strings.generated.resources.signage_delete_playlist_title
import org.churchpresenter.strings.generated.resources.signage_delete_root_trash
import org.churchpresenter.strings.generated.resources.signage_folder
import org.churchpresenter.strings.generated.resources.signage_folder_empty
import org.churchpresenter.strings.generated.resources.signage_folder_hint
import org.churchpresenter.strings.generated.resources.signage_open_folder
import org.churchpresenter.strings.generated.resources.signage_picture_seconds
import org.churchpresenter.strings.generated.resources.signage_playlist_counts
import org.churchpresenter.strings.generated.resources.signage_playlists
import org.churchpresenter.strings.generated.resources.signage_reading_folder
import org.jetbrains.compose.resources.stringResource

/** The signage folder: where it is, how long a picture stays up, and the playlists it makes. */
@Composable
internal fun SignageFolderPanel(state: SignageFolderState, actions: FolderActions, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val chooseTitle = stringResource(Res.string.signage_choose_folder)
    Column(modifier = modifier) {
        ZoneLabel(stringResource(Res.string.signage_folder), modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        Column(
            modifier = Modifier.fillMaxWidth().bibleListCard().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                state.folder.toString(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) { actions.desktop.openFolder(state.folder) }
                }) { Text(stringResource(Res.string.signage_open_folder)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        actions.desktop.fileChooser
                            .chooseSingle(state.folder, emptyList(), chooseTitle, selectDirectory = true)
                            ?.let(actions.onChangeFolder)
                    }
                }) { Text(stringResource(Res.string.signage_change_folder)) }
            }
            NumberSettingsTextField(
                label = stringResource(Res.string.signage_picture_seconds),
                initialText = state.settings.pictureSeconds,
                range = PICTURE_SECONDS,
                onValueChange = actions.onPictureSeconds,
            )
            Text(
                stringResource(Res.string.signage_folder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
        ) {
            ZoneLabel(stringResource(Res.string.signage_playlists), modifier = Modifier.weight(1f))
            AddPlaylistMenu(state, actions)
        }
        PlaylistList(state, actions, Modifier.fillMaxWidth().weight(1f, fill = false))
    }
}

/** Add a link, a single picture or video from anywhere, or a folder (in the file manager). */
@Composable
private fun AddPlaylistMenu(state: SignageFolderState, actions: FolderActions) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    var addingRotation by remember { mutableStateOf(false) }
    val chooseTitle = stringResource(Res.string.signage_choose_file)
    val filterName = stringResource(Res.string.signage_pictures_and_videos)
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.signage_add))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.signage_add_link)) },
                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                onClick = {
                    open = false
                    addingLink = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.signage_add_file)) },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = {
                    open = false
                    scope.launch {
                        val filters = listOf(picturesAndVideosFilter(filterName))
                        actions.desktop.fileChooser
                            .chooseSingle(null, filters, chooseTitle, selectDirectory = false)
                            ?.let(actions.onAddFile)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.signage_add_rotation)) },
                leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                onClick = {
                    open = false
                    addingRotation = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.signage_add_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = {
                    open = false
                    scope.launch(Dispatchers.IO) { actions.desktop.openFolder(state.folder) }
                },
            )
        }
    }
    if (addingLink) LinkDialog(onAdd = actions.onAddLink, onDismiss = { addingLink = false })
    if (addingRotation) {
        RotationDialog(
            entries = state.playlists.filter { it.kind != SourceKind.ROTATION },
            initial = null,
            onSave = actions.onAddRotation,
            onDismiss = { addingRotation = false },
        )
    }
}

@Composable
private fun PlaylistList(state: SignageFolderState, actions: FolderActions, modifier: Modifier) {
    val desktop = actions.desktop
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var failed by remember { mutableStateOf(false) }
    Column(modifier = modifier.bibleListCard()) {
        val message = when {
            !state.ready -> stringResource(Res.string.signage_reading_folder)
            state.playlists.isEmpty() -> stringResource(Res.string.signage_folder_empty)
            else -> null
        }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        if (failed) {
            Text(
                stringResource(Res.string.signage_delete_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        LazyColumn {
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist, onRename = { renaming = playlist }, onDelete = { deleting = playlist })
                if (playlist != state.playlists.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    renaming?.takeIf { it.kind == SourceKind.ROTATION }?.let { playlist ->
        RotationDialog(
            entries = state.playlists.filter { it.kind != SourceKind.ROTATION },
            initial = state.settings.added.filterIsInstance<RotationPlaylist>().firstOrNull { it.id == playlist.id },
            onSave = { name, steps -> actions.onEditRotation(playlist.id, name, steps) },
            onDismiss = { renaming = null },
        )
    }
    when (val editing = renaming?.let { playlist -> state.settings.added.firstOrNull { it.id == playlist.id } }) {
        is LinkPlaylist -> LinkDialog(
            initial = editing,
            onAdd = { actions.onEditLink(editing.id, it) },
            onDismiss = { renaming = null },
        )
        is FilePlaylist -> FileDialog(
            initial = editing,
            desktop = desktop,
            onSave = { name, file -> actions.onEditFile(editing.id, name, file) },
            onDismiss = { renaming = null },
        )
        is RotationPlaylist, null -> Unit
    }
    deleting?.takeIf { it.added }?.let { playlist ->
        ConfirmDialog(
            title = stringResource(Res.string.signage_remove_playlist_title, playlist.name),
            text = stringResource(Res.string.signage_remove_playlist_text),
            confirm = stringResource(Res.string.signage_remove),
            onConfirm = { actions.onRemove(playlist.id) },
            onDismiss = { deleting = null },
        )
    }
    deleting?.takeUnless { it.added }?.let { playlist ->
        val text = when {
            !desktop.canTrash() -> stringResource(Res.string.signage_delete_for_good)
            playlist.id == SignageFolder.ROOT_ID -> stringResource(Res.string.signage_delete_root_trash)
            else -> stringResource(Res.string.signage_delete_folder_trash, playlist.name)
        }
        ConfirmDialog(
            title = stringResource(Res.string.signage_delete_playlist_title, playlist.name),
            text = text,
            confirm = stringResource(Res.string.signage_delete),
            onConfirm = {
                scope.launch {
                    failed = !withContext(Dispatchers.IO) { desktop.deletePlaylist(state.folder, playlist.id) }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** One playlist: its kind's icon and name, a folder's counts beside it, an added one's file or address under it. */
@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            iconOf(playlist.kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        if (playlist.added) {
            AddedPlaylistText(playlist, Modifier.weight(1f))
        } else {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(Res.string.signage_playlist_counts, playlist.pictures, playlist.videos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (playlist.added) {
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Edit),
                text = stringResource(
                    when (playlist.kind) {
                        SourceKind.WEB, SourceKind.STREAM -> Res.string.signage_link_title_edit
                        SourceKind.ROTATION -> Res.string.signage_rotation_title_edit
                        else -> Res.string.signage_file_title_edit
                    },
                ),
                onClick = onRename,
            )
        }
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Delete),
            text = stringResource(
                if (playlist.added) Res.string.signage_remove_playlist else Res.string.signage_delete_playlist,
            ),
            onClick = onDelete,
        )
    }
}

/** An added playlist's name, with its file or address under it (in red when the file is gone). */
@Composable
private fun AddedPlaylistText(playlist: PlaylistSummary, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                playlist.missing -> stringResource(Res.string.signage_file_missing, playlist.detail.orEmpty())
                playlist.kind == SourceKind.ROTATION -> stringResource(
                    Res.string.signage_rotation_summary,
                    playlist.steps,
                    if (playlist.seconds >= SECONDS_PER_MINUTE) {
                        stringResource(Res.string.signage_rotation_minutes, playlist.seconds / SECONDS_PER_MINUTE)
                    } else {
                        stringResource(Res.string.signage_rotation_seconds_short, playlist.seconds)
                    },
                )
                else -> playlist.detail.orEmpty()
            },
            style = MaterialTheme.typography.bodySmall,
            color = with(MaterialTheme.colorScheme) { if (playlist.missing) error else onSurfaceVariant },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun iconOf(kind: SourceKind): ImageVector = when (kind) {
    SourceKind.FOLDER -> Icons.Filled.Folder
    SourceKind.PICTURE -> Icons.Filled.Image
    SourceKind.VIDEO -> Icons.Filled.Movie
    SourceKind.WEB -> Icons.Filled.Language
    SourceKind.STREAM -> Icons.Filled.LiveTv
    SourceKind.ROTATION -> Icons.Filled.Repeat
}

private val PICTURE_SECONDS = 1..600

private const val SECONDS_PER_MINUTE = 60
