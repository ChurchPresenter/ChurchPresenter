package org.churchpresenter.signage.tab

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_add_file
import org.churchpresenter.strings.generated.resources.signage_add_file_desc
import org.churchpresenter.strings.generated.resources.signage_add_folder
import org.churchpresenter.strings.generated.resources.signage_add_folder_desc
import org.churchpresenter.strings.generated.resources.signage_add_link
import org.churchpresenter.strings.generated.resources.signage_add_link_desc
import org.churchpresenter.strings.generated.resources.signage_add_playlist
import org.churchpresenter.strings.generated.resources.signage_add_rotation
import org.churchpresenter.strings.generated.resources.signage_add_rotation_desc
import org.churchpresenter.strings.generated.resources.signage_add_smugmug
import org.churchpresenter.strings.generated.resources.signage_add_smugmug_desc
import org.churchpresenter.strings.generated.resources.signage_choose_file
import org.churchpresenter.strings.generated.resources.signage_choose_playlist_folder
import org.churchpresenter.strings.generated.resources.signage_pictures_and_videos
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Add a link, a single picture or video, a rotation, or a folder, the file and folder from anywhere. */
@Composable
internal fun AddPlaylistMenu(state: SignageFolderState, actions: FolderActions) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    var addingRotation by remember { mutableStateOf(false) }
    var addingSmugMug by remember { mutableStateOf(false) }
    val chooseTitle = stringResource(Res.string.signage_choose_file)
    val chooseFolderTitle = stringResource(Res.string.signage_choose_playlist_folder)
    val filterName = stringResource(Res.string.signage_pictures_and_videos)
    Box {
        RaisedButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.signage_add_playlist))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { AddItemText(Res.string.signage_add_link, Res.string.signage_add_link_desc) },
                leadingIcon = { KindTile(TileKind.WEB, size = 32.dp) },
                onClick = {
                    open = false
                    addingLink = true
                },
            )
            DropdownMenuItem(
                text = { AddItemText(Res.string.signage_add_file, Res.string.signage_add_file_desc) },
                leadingIcon = { KindTile(TileKind.PICTURE, size = 32.dp) },
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
                text = { AddItemText(Res.string.signage_add_rotation, Res.string.signage_add_rotation_desc) },
                leadingIcon = { KindTile(TileKind.ROTATION, size = 32.dp) },
                onClick = {
                    open = false
                    addingRotation = true
                },
            )
            DropdownMenuItem(
                text = { AddItemText(Res.string.signage_add_smugmug, Res.string.signage_add_smugmug_desc) },
                leadingIcon = { KindTile(TileKind.SMUGMUG, size = 32.dp) },
                onClick = {
                    open = false
                    addingSmugMug = true
                },
            )
            DropdownMenuItem(
                text = { AddItemText(Res.string.signage_add_folder, Res.string.signage_add_folder_desc) },
                leadingIcon = { KindTile(TileKind.FOLDER, size = 32.dp) },
                onClick = {
                    open = false
                    scope.launch {
                        actions.desktop.fileChooser
                            .chooseSingle(null, emptyList(), chooseFolderTitle, selectDirectory = true)
                            ?.let(actions.onAddFolder)
                    }
                },
            )
        }
    }
    if (addingLink) {
        LinkDialog(
            onAdd = actions.onAddLink,
            onDismiss = { addingLink = false },
            idlePlaylists = idlePlaylists(state, editing = null),
        )
    }
    if (addingRotation) {
        RotationDialog(
            entries = state.playlists.filter { it.kind != SourceKind.ROTATION },
            initial = null,
            onSave = actions.onAddRotation,
            onDismiss = { addingRotation = false },
        )
    }
    if (addingSmugMug) {
        SmugMugDialog(
            initial = null,
            apiKey = actions.smugMug.apiKey,
            onApiKey = actions.smugMug.onApiKey,
            lookUp = actions.smugMug.lookUp,
            onSave = actions.onSaveSmugMug,
            onDismiss = { addingSmugMug = false },
            openUrl = actions.smugMug.openUrl,
        )
    }
}

/** An Add menu entry: what it makes, and a line on what that is. */
@Composable
private fun AddItemText(label: StringResource, description: StringResource) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
