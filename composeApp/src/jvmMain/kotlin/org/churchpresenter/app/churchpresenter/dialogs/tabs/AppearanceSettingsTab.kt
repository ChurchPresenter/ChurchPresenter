package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_storage_directory
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.set_all_directories
import churchpresenter.composeapp.generated.resources.lower_third_storage_directory
import churchpresenter.composeapp.generated.resources.pictures_storage_directory
import churchpresenter.composeapp.generated.resources.theme
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.songs_storage_directory
import org.churchpresenter.app.churchpresenter.composables.ThemeSegmentedButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Window
import javax.swing.SwingUtilities

@Composable
fun AppearanceSettingsTab(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    settings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {}
) {
    val fileManager = FileManager()
    val setAllText = stringResource(Res.string.set_all_directories)

    val setAllDirectories: (String) -> Unit = { dir ->
        onSettingsChange { s ->
            s.copy(
                bibleSettings = s.bibleSettings.copy(storageDirectory = dir),
                songSettings = s.songSettings.copy(storageDirectory = dir),
                pictureSettings = s.pictureSettings.copy(storageDirectory = dir),
                streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir)
            )
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Theme section
        Text(
            text = stringResource(Res.string.theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ThemeSegmentedButton(
            selectedTheme = currentTheme,
            onThemeChange = onThemeChange
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Bible Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.bible_storage_directory),
            currentPath = settings.bibleSettings.storageDirectory,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(bibleSettings = s.bibleSettings.copy(storageDirectory = dir))
                }
            },
            onSetAll = setAllDirectories
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Songs Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.songs_storage_directory),
            currentPath = settings.songSettings.storageDirectory,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.copy(storageDirectory = dir))
                }
            },
            onSetAll = setAllDirectories
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pictures Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.pictures_storage_directory),
            currentPath = settings.pictureSettings.storageDirectory,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = dir))
                }
            },
            onSetAll = setAllDirectories
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lower Third Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.lower_third_storage_directory),
            currentPath = settings.streamingSettings.lowerThirdFolder,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir))
                }
            },
            onSetAll = setAllDirectories
        )
    }
}

@Composable
private fun DirectoryPicker(
    label: String,
    currentPath: String,
    noDirectoryText: String,
    browseText: String,
    setAllText: String,
    fileManager: FileManager,
    onDirectorySelected: (String) -> Unit,
    onSetAll: (String) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentPath.ifEmpty { noDirectoryText },
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val selectedDir = fileManager.chooseDirectory(
                        currentDirectory = currentPath,
                        parentWindow = parentWindow
                    )
                    selectedDir?.let { onDirectorySelected(it) }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = browseText, style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = { if (currentPath.isNotEmpty()) onSetAll(currentPath) },
            enabled = currentPath.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = setAllText, style = MaterialTheme.typography.labelMedium)
        }
    }
}
