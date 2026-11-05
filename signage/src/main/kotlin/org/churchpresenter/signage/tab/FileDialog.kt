package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.strings.generated.resources.signage_add_file_desc
import org.churchpresenter.strings.generated.resources.signage_file_caption
import org.churchpresenter.strings.generated.resources.signage_folder_caption
import org.churchpresenter.strings.generated.resources.signage_folder_subfolders
import org.churchpresenter.strings.generated.resources.signage_folder_subtitle
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
    val shown = chosen?.toString() ?: initial.path
    val kind = if (SignageFolder.kindOf(Path.of(shown)) == MediaKind.VIDEO) TileKind.VIDEO else TileKind.PICTURE
    NameDialog(
        title = stringResource(Res.string.signage_file_title_edit),
        subtitle = stringResource(Res.string.signage_add_file_desc),
        kind = kind,
        initialName = initial.name,
        onSave = { onSave(it, chosen) },
        onDismiss = onDismiss,
    ) {
        SourceCard(stringResource(Res.string.signage_file_caption), kind, shown) {
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
        subtitle = stringResource(Res.string.signage_folder_subtitle),
        kind = TileKind.FOLDER,
        initialName = initial.name,
        onSave = { onSave(it, seconds) },
        onDismiss = onDismiss,
    ) {
        SourceCard(stringResource(Res.string.signage_folder_caption), TileKind.FOLDER, initial.path) {
            KeyButton(
                onClick = { location?.let { scope.launch(Dispatchers.IO) { desktop.openFolder(it) } } },
                enabled = location != null,
            ) { Text(stringResource(Res.string.signage_open_folder)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberSettingsTextField(
                label = stringResource(Res.string.signage_picture_seconds),
                initialText = initial.pictureSeconds,
                range = PickedFolderPlaylist.PICTURE_SECONDS,
                onValueChange = { seconds = it },
            )
            Text(
                stringResource(Res.string.signage_folder_subfolders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What a playlist plays from, as a card: [kind]'s tile, its [path], and [action] at the end. */
@Composable
private fun SourceCard(caption: String, kind: TileKind, path: String, action: @Composable () -> Unit) {
    Column {
        FieldCaption(caption)
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = CARD_WASH), shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindTile(kind, size = 38.dp)
            Text(
                path,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            action()
        }
    }
}

/** A playlist's name field, with [content] under it, saved through [onSave]. */
@Composable
private fun NameDialog(
    title: String,
    subtitle: String,
    kind: TileKind,
    initialName: String,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { SignageDialogTitle(title, subtitle, kind = kind) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(DIALOG_WIDTH)) {
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

/** The name field, whose caption is drawn apart from the box a test types into. */
internal const val FILE_NAME_TAG = "signage-file-name"
private val DIALOG_WIDTH = 540.dp
private const val CARD_WASH = 0.03f
