package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_song_samples
import churchpresenter.composeapp.generated.resources.bible_storage_directory
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.conversion_complete
import churchpresenter.composeapp.generated.resources.conversion_complete_message
import churchpresenter.composeapp.generated.resources.conversion_complete_with_errors
import churchpresenter.composeapp.generated.resources.convert
import churchpresenter.composeapp.generated.resources.folder_already_exists
import churchpresenter.composeapp.generated.resources.folder_overwrite_confirm
import churchpresenter.composeapp.generated.resources.set_all_directories
import churchpresenter.composeapp.generated.resources.lower_third_storage_directory
import churchpresenter.composeapp.generated.resources.media_storage_directory
import churchpresenter.composeapp.generated.resources.pictures_storage_directory
import churchpresenter.composeapp.generated.resources.presentation_storage_directory
import churchpresenter.composeapp.generated.resources.theme
import churchpresenter.composeapp.generated.resources.detected_files_label
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_files_detected
import churchpresenter.composeapp.generated.resources.reset_settings
import churchpresenter.composeapp.generated.resources.clear_lottie_cache_confirm
import churchpresenter.composeapp.generated.resources.export_settings
import churchpresenter.composeapp.generated.resources.import_settings
import churchpresenter.composeapp.generated.resources.import_settings_confirm
import churchpresenter.composeapp.generated.resources.reset_settings_confirm
import churchpresenter.composeapp.generated.resources.settings_export_failed
import churchpresenter.composeapp.generated.resources.settings_exported
import churchpresenter.composeapp.generated.resources.settings_import_failed
import churchpresenter.composeapp.generated.resources.song_folder_with_count
import churchpresenter.composeapp.generated.resources.song_samples
import churchpresenter.composeapp.generated.resources.song_samples_copied
import churchpresenter.composeapp.generated.resources.song_samples_overwrite_confirm
import churchpresenter.composeapp.generated.resources.songs_storage_directory
import churchpresenter.composeapp.generated.resources.sps_file_not_supported
import churchpresenter.composeapp.generated.resources.tooltip_directory_not_found
import churchpresenter.composeapp.generated.resources.tooltip_directory_not_writable
import churchpresenter.composeapp.generated.resources.tooltip_directory_writable
import org.churchpresenter.app.churchpresenter.composables.ThemeSegmentedButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.SpsConverter
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Window
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

private val exportJsonFormat = kotlinx.serialization.json.Json {
    encodeDefaults = true
    prettyPrint = true
}

private val importJsonFormat = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
}

@Composable
fun SystemSettingsTab(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    settings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {}
) {
    val fileManager = FileManager()
    val setAllText = stringResource(Res.string.set_all_directories)
    val folderAlreadyExistsTitle = stringResource(Res.string.folder_already_exists)
    val songSamplesTitle = stringResource(Res.string.song_samples)
    val songSamplesOverwriteMsg = stringResource(Res.string.song_samples_overwrite_confirm)
    val songSamplesCopiedFmt = stringResource(Res.string.song_samples_copied)
    val folderOverwriteConfirmFmt = stringResource(Res.string.folder_overwrite_confirm)
    val conversionCompleteTitle = stringResource(Res.string.conversion_complete)
    val conversionCompleteMsgFmt = stringResource(Res.string.conversion_complete_message)
    val conversionCompleteErrorsFmt = stringResource(Res.string.conversion_complete_with_errors)

    val setAllDirectories: (String) -> Unit = { dir ->
        onSettingsChange { s ->
            s.copy(
                bibleSettings = s.bibleSettings.copy(storageDirectory = dir),
                songSettings = s.songSettings.copy(storageDirectory = dir),
                pictureSettings = s.pictureSettings.copy(storageDirectory = dir),
                streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir),
                presentationStorageDirectory = dir,
                mediaStorageDirectory = dir
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
    ) {
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
        DetectedFilesList(
            files = fileManager.getBibleFilesInDirectory(settings.bibleSettings.storageDirectory),
            directorySet = settings.bibleSettings.storageDirectory.isNotEmpty(),
            detectedLabel = stringResource(Res.string.detected_files_label),
            noFilesText = stringResource(Res.string.no_files_detected)
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
        if (settings.songSettings.storageDirectory.isNotEmpty()) {
            var convertingFile by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()
            val spsFiles = fileManager.getSongFilesInDirectory(settings.songSettings.storageDirectory)
            val songFolders = fileManager.getSongFoldersInDirectory(settings.songSettings.storageDirectory)

            // Add Song Samples button
            val sampleScope = rememberCoroutineScope()
            var copyingSamples by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.padding(top = 2.dp, start = 2.dp).height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (spsFiles.isEmpty() && songFolders.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_files_detected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.detected_files_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                    TextButton(
                        onClick = {
                            val samplesDir = java.io.File(settings.songSettings.storageDirectory, "Song Samples")
                            val proceed = if (samplesDir.exists()) {
                                JOptionPane.showConfirmDialog(
                                    null,
                                    songSamplesOverwriteMsg,
                                    folderAlreadyExistsTitle,
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                                ) == JOptionPane.YES_OPTION
                            } else true
                            if (proceed) {
                                copyingSamples = true
                                sampleScope.launch {
                                    val count = withContext(Dispatchers.IO) {
                                        copySongSamples(settings.songSettings.storageDirectory)
                                    }
                                    copyingSamples = false
                                    JOptionPane.showMessageDialog(
                                        null,
                                        String.format(songSamplesCopiedFmt, count),
                                        songSamplesTitle,
                                        JOptionPane.INFORMATION_MESSAGE
                                    )
                                }
                            }
                        },
                        enabled = !copyingSamples,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(Res.string.add_song_samples), style = MaterialTheme.typography.labelSmall)
                    }
                if (copyingSamples) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            for (spsFile in spsFiles) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp).height(32.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.sps_file_not_supported, spsFile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                    if (convertingFile == spsFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp).size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = {
                                val converter = SpsConverter()
                                val spsPath = java.io.File(settings.songSettings.storageDirectory, spsFile).absolutePath

                                // Check if target folder already exists
                                if (converter.targetFolderExists(spsPath, settings.songSettings.storageDirectory)) {
                                    val folderName = converter.getTargetFolderName(spsPath, settings.songSettings.storageDirectory) ?: spsFile
                                    val confirm = JOptionPane.showConfirmDialog(
                                        null,
                                        String.format(folderOverwriteConfirmFmt, folderName),
                                        folderAlreadyExistsTitle,
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE
                                    )
                                    if (confirm != JOptionPane.YES_OPTION) return@TextButton
                                }

                                convertingFile = spsFile
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        converter.convertSpsToSongFiles(spsPath, settings.songSettings.storageDirectory)
                                    }
                                    convertingFile = null
                                    if (result.errors.isEmpty()) {
                                        JOptionPane.showMessageDialog(
                                            null,
                                            String.format(conversionCompleteMsgFmt, result.songsConverted, java.io.File(result.songbookFolder).name),
                                            conversionCompleteTitle,
                                            JOptionPane.INFORMATION_MESSAGE
                                        )
                                    } else {
                                        JOptionPane.showMessageDialog(
                                            null,
                                            String.format(conversionCompleteErrorsFmt, result.songsConverted, result.errors.joinToString("\n")),
                                            conversionCompleteTitle,
                                            JOptionPane.WARNING_MESSAGE
                                        )
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(Res.string.convert), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            for (folder in songFolders) {
                Text(
                    text = stringResource(Res.string.song_folder_with_count, folder.first, folder.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                )
            }
        }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Presentation Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.presentation_storage_directory),
            currentPath = settings.presentationStorageDirectory,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(presentationStorageDirectory = dir)
                }
            },
            onSetAll = setAllDirectories
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Media Storage Directory
        DirectoryPicker(
            label = stringResource(Res.string.media_storage_directory),
            currentPath = settings.mediaStorageDirectory,
            noDirectoryText = stringResource(Res.string.no_directory_selected),
            browseText = stringResource(Res.string.browse_directory),
            setAllText = setAllText,
            fileManager = fileManager,
            onDirectorySelected = { dir ->
                onSettingsChange { s ->
                    s.copy(mediaStorageDirectory = dir)
                }
            },
            onSetAll = setAllDirectories
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Export / Import / Reset Settings
        val resetConfirmMsg = stringResource(Res.string.reset_settings_confirm)
        val resetTitle = stringResource(Res.string.reset_settings)
        val clearLottieCacheMsg = stringResource(Res.string.clear_lottie_cache_confirm)
        val exportTitle = stringResource(Res.string.export_settings)
        val importTitle = stringResource(Res.string.import_settings)
        val importConfirmMsg = stringResource(Res.string.import_settings_confirm)
        val settingsExportedMsg = stringResource(Res.string.settings_exported)
        val settingsExportFailedMsg = stringResource(Res.string.settings_export_failed)
        val settingsImportFailedMsg = stringResource(Res.string.settings_import_failed)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Export Settings
            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        val chooser = JFileChooser().apply {
                            dialogTitle = exportTitle
                            fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
                            selectedFile = java.io.File("churchpresenter-settings.json")
                        }
                        val result = chooser.showSaveDialog(Window.getWindows().firstOrNull { it.isActive })
                        if (result == JFileChooser.APPROVE_OPTION) {
                            try {
                                val settingsManager = SettingsManager()
                                val currentSettings = settingsManager.loadSettings()
                                val json = exportJsonFormat.encodeToString(AppSettings.serializer(), currentSettings)
                                var file = chooser.selectedFile
                                if (!file.name.endsWith(".json")) {
                                    file = java.io.File(file.absolutePath + ".json")
                                }
                                file.writeText(json)
                                JOptionPane.showMessageDialog(
                                    Window.getWindows().firstOrNull { it.isActive },
                                    settingsExportedMsg,
                                    exportTitle,
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                            } catch (_: Exception) {
                                JOptionPane.showMessageDialog(
                                    Window.getWindows().firstOrNull { it.isActive },
                                    settingsExportFailedMsg,
                                    exportTitle,
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = exportTitle, style = MaterialTheme.typography.labelMedium)
            }

            // Import Settings
            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        val chooser = JFileChooser().apply {
                            dialogTitle = importTitle
                            fileFilter = FileNameExtensionFilter("JSON (*.json)", "json")
                        }
                        val result = chooser.showOpenDialog(Window.getWindows().firstOrNull { it.isActive })
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val confirmResult = JOptionPane.showConfirmDialog(
                                Window.getWindows().firstOrNull { it.isActive },
                                importConfirmMsg,
                                importTitle,
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            )
                            if (confirmResult == JOptionPane.YES_OPTION) {
                                try {
                                    val json = chooser.selectedFile.readText()
                                    val imported = importJsonFormat.decodeFromString(AppSettings.serializer(), json)
                                    val settingsManager = SettingsManager()
                                    settingsManager.saveSettings(imported)
                                    // Restart the application
                                    val javaBin = System.getProperty("java.home") + "/bin/java"
                                    val command = ProcessHandle.current().info().command().orElse(javaBin)
                                    val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
                                    try {
                                        ProcessBuilder(listOf(command) + args.toList()).start()
                                    } catch (_: Exception) {}
                                    Runtime.getRuntime().exit(0)
                                } catch (_: Exception) {
                                    JOptionPane.showMessageDialog(
                                        Window.getWindows().firstOrNull { it.isActive },
                                        settingsImportFailedMsg,
                                        importTitle,
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = importTitle, style = MaterialTheme.typography.labelMedium)
            }

            // Reset All Settings
            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        val result2 = JOptionPane.showConfirmDialog(
                            Window.getWindows().firstOrNull { it.isActive },
                            resetConfirmMsg,
                            resetTitle,
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        )
                        if (result2 == JOptionPane.YES_OPTION) {
                            val settingsManager = SettingsManager()
                            val clearCache = JOptionPane.showConfirmDialog(
                                Window.getWindows().firstOrNull { it.isActive },
                                clearLottieCacheMsg,
                                resetTitle,
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE
                            )
                            if (clearCache == JOptionPane.YES_OPTION) {
                                settingsManager.lottiePresetsDir.deleteRecursively()
                            }
                            settingsManager.saveSettings(AppSettings())
                            // Restart the application
                            val javaBin = System.getProperty("java.home") + "/bin/java"
                            val command = ProcessHandle.current().info().command().orElse(javaBin)
                            val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
                            try {
                                ProcessBuilder(listOf(command) + args.toList()).start()
                            } catch (_: Exception) {}
                            Runtime.getRuntime().exit(0)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = resetTitle, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()
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
        if (currentPath.isNotEmpty()) {
            val dirFile = remember(currentPath) { java.io.File(currentPath) }
            val dirExists = remember(currentPath) { dirFile.exists() }
            val isWritable = remember(currentPath) { dirExists && dirFile.canWrite() }
            TooltipArea(
                tooltip = {
                    Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                        Text(
                            when {
                                isWritable -> stringResource(Res.string.tooltip_directory_writable)
                                !dirExists -> stringResource(Res.string.tooltip_directory_not_found)
                                else -> stringResource(Res.string.tooltip_directory_not_writable)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.CursorPoint()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (isWritable) Color(0xFF4CAF50) else Color(0xFFF44336),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
        Button(
            onClick = {
                scope.launch {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val selectedDir = fileManager.chooseDirectory(
                        currentDirectory = currentPath
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

@Composable
private fun DetectedFilesList(
    files: List<String>,
    directorySet: Boolean,
    detectedLabel: String,
    noFilesText: String
) {
    if (!directorySet) return
    Text(
        text = if (files.isNotEmpty()) "$detectedLabel ${files.joinToString(", ")}"
               else noFilesText,
        style = MaterialTheme.typography.bodySmall,
        color = if (files.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

private suspend fun copySongSamples(storageDirectory: String): Int {
    val targetDir = java.io.File(storageDirectory, "Song Samples")
    if (!targetDir.exists()) targetDir.mkdirs()

    val indexBytes = churchpresenter.composeapp.generated.resources.Res.readBytes("files/song_samples/index.txt")
    val filenames = indexBytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    var count = 0
    for (filename in filenames) {
        try {
            val songBytes = churchpresenter.composeapp.generated.resources.Res.readBytes("files/song_samples/$filename")
            val targetFile = java.io.File(targetDir, filename)
            targetFile.writeBytes(songBytes)
            count++
        } catch (_: Exception) {
            // Skip files that can't be read
        }
    }
    return count
}
