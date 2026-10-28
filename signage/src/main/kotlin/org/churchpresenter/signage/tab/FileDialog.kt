package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_choose_file
import org.churchpresenter.strings.generated.resources.signage_file_change
import org.churchpresenter.strings.generated.resources.signage_file_title_edit
import org.churchpresenter.strings.generated.resources.signage_link_name
import org.churchpresenter.strings.generated.resources.signage_pictures_and_videos
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path
import kotlin.io.path.Path

/** Edits a picture or video added on its own: its name, and which file it plays. */
@Composable
internal fun FileDialog(
    initial: FilePlaylist,
    desktop: SignageDesktop,
    onSave: (name: String, file: Path) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var file by remember { mutableStateOf(Path(initial.path)) }
    val scope = rememberCoroutineScope()
    val chooseTitle = stringResource(Res.string.signage_choose_file)
    val filterName = stringResource(Res.string.signage_pictures_and_videos)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_file_title_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(DIALOG_WIDTH)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.signage_link_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    file.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val filters = listOf(picturesAndVideosFilter(filterName))
                            desktop.fileChooser
                                .chooseSingle(file.parent, filters, chooseTitle, selectDirectory = false)
                                ?.let { file = it }
                        }
                    },
                ) { Text(stringResource(Res.string.signage_file_change)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, file)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private val DIALOG_WIDTH = 480.dp
