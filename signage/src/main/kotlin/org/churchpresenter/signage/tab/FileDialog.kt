package org.churchpresenter.signage.tab

import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_choose_file
import org.churchpresenter.strings.generated.resources.signage_file_change
import org.churchpresenter.strings.generated.resources.signage_file_title_edit
import org.churchpresenter.strings.generated.resources.signage_folder_title_edit
import org.churchpresenter.strings.generated.resources.signage_link_name
import org.churchpresenter.strings.generated.resources.signage_open_folder
import org.churchpresenter.strings.generated.resources.signage_picture_seconds
import org.churchpresenter.strings.generated.resources.signage_pictures_and_videos
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

/**
 * Edits a picture or video added on its own: its name, and which file it plays. A file chosen here
 * is copied into the signage folder on save, in place of the one it played.
 */
@Composable
internal fun FileDialog(
    initial: FilePlaylist,
    desktop: SignageDesktop,
    onSave: (name: String, chosen: Path?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<Path?>(null) }
    val scope = rememberCoroutineScope()
    val chooseTitle = stringResource(Res.string.signage_choose_file)
    val filterName = stringResource(Res.string.signage_pictures_and_videos)
    NameDialog(
        title = stringResource(Res.string.signage_file_title_edit),
        initialName = initial.name,
        onSave = { onSave(it, chosen) },
        onDismiss = onDismiss,
    ) {
        PathText(chosen?.toString() ?: initial.path)
        KeyButton(
            onClick = {
                scope.launch {
                    val filters = listOf(picturesAndVideosFilter(filterName))
                    desktop.fileChooser
                        .chooseSingle(chosen?.parent, filters, chooseTitle, selectDirectory = false)
                        ?.let { chosen = it }
                }
            },
        ) { Text(stringResource(Res.string.signage_file_change)) }
    }
}

/**
 * Edits a folder playlist's name and how long each picture stays up; its copy in the signage folder
 * opens in the file manager.
 */
@Composable
internal fun FolderDialog(
    initial: PickedFolderPlaylist,
    /** Where the copy is on this computer; null when it cannot be found. */
    location: Path?,
    desktop: SignageDesktop,
    onSave: (name: String, pictureSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableStateOf(initial.pictureSeconds) }
    NameDialog(
        title = stringResource(Res.string.signage_folder_title_edit),
        initialName = initial.name,
        onSave = { onSave(it, seconds) },
        onDismiss = onDismiss,
    ) {
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_picture_seconds),
            initialText = initial.pictureSeconds,
            range = PickedFolderPlaylist.PICTURE_SECONDS,
            onValueChange = { seconds = it },
        )
        PathText(initial.path)
        KeyButton(
            onClick = { location?.let { scope.launch(Dispatchers.IO) { desktop.openFolder(it) } } },
            enabled = location != null,
        ) { Text(stringResource(Res.string.signage_open_folder)) }
    }
}

/** A playlist's name field, with [content] under it, saved through [onSave]. */
@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(DIALOG_WIDTH)) {
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(Res.string.signage_link_name),
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(FILE_NAME_TAG),
                )
                content()
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    onSave(name)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun PathText(path: String) = Text(
    path,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** The name field, whose caption is drawn apart from the box a test types into. */
internal const val FILE_NAME_TAG = "signage-file-name"
private val DIALOG_WIDTH = 480.dp
