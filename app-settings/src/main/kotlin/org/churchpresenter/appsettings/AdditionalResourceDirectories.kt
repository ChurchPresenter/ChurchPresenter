package org.churchpresenter.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ResourceDirectory
import org.churchpresenter.settings.withAdditionalResourceDirectory
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.resource_directory_add
import org.churchpresenter.strings.generated.resources.resource_directory_remove
import org.churchpresenter.profiles.FileManager
import org.churchpresenter.settings.withPrimaryResourceDirectory
import org.churchpresenter.strings.generated.resources.resource_directory_default
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
internal fun AdditionalResourceDirectories(
    settings: AppSettings, section: ResourceDirectory,
    fileManager: FileManager, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    primaryDirectory: String,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(start = 180.dp, end = 16.dp)) {
        settings.additionalResourceDirectories[section].orEmpty().forEach { path ->
            Row(Modifier.fillMaxWidth()) {
                Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    onSettingsChange { it.withPrimaryResourceDirectory(section, path, primaryDirectory) }
                }) { Text(stringResource(Res.string.resource_directory_default)) }
                TextButton(onClick = {
                    onSettingsChange {
                        val remaining = it.additionalResourceDirectories[section].orEmpty()
                            .filterNot { dir -> dir == path }
                        it.copy(
                            additionalResourceDirectories = it.additionalResourceDirectories + (section to remaining)
                        )
                    }
                }) { Text(stringResource(Res.string.resource_directory_remove)) }
            }
        }
        TextButton(onClick = {
            scope.launch {
                val path = fileManager.chooseDirectory(currentDirectory = primaryDirectory) ?: return@launch
                val alreadyDefault = File(path).absoluteFile.normalize() ==
                    File(primaryDirectory).absoluteFile.normalize()
                if (alreadyDefault) return@launch
                onSettingsChange { it.withAdditionalResourceDirectory(section, path) }
            }
        }) { Text(stringResource(Res.string.resource_directory_add)) }
    }
}
