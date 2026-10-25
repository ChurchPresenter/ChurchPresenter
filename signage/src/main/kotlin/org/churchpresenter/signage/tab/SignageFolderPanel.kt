package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
        ZoneLabel(
            stringResource(Res.string.signage_playlists),
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
        )
        PlaylistList(state, actions.desktop, Modifier.fillMaxWidth().weight(1f, fill = false))
    }
}

@Composable
private fun PlaylistList(state: SignageFolderState, desktop: SignageDesktop, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
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
                PlaylistRow(playlist, onDelete = { deleting = playlist })
                if (playlist != state.playlists.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    deleting?.let { playlist ->
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

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.signage_playlist_counts, playlist.pictures, playlist.videos),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Delete),
            text = stringResource(Res.string.signage_delete_playlist),
            onClick = onDelete,
        )
    }
}

private val PICTURE_SECONDS = 1..600
