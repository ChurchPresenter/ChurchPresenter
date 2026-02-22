package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear_folder
import churchpresenter.composeapp.generated.resources.lower_third_files_found
import churchpresenter.composeapp.generated.resources.lower_third_folder
import churchpresenter.composeapp.generated.resources.lower_third_folder_help
import churchpresenter.composeapp.generated.resources.lower_third_folder_hint
import churchpresenter.composeapp.generated.resources.lower_third_no_files
import churchpresenter.composeapp.generated.resources.select_folder
import churchpresenter.composeapp.generated.resources.streaming_settings
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun StreamingSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val lowerThirdFolder = settings.streamingSettings.lowerThirdFolder

    val jsonFileCount = remember(lowerThirdFolder) {
        if (lowerThirdFolder.isBlank()) 0
        else File(lowerThirdFolder).takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" }
            ?.size ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.streaming_settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        Spacer(modifier = Modifier.height(4.dp))

        // Section label
        Text(
            text = stringResource(Res.string.lower_third_folder),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Folder path display + buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = lowerThirdFolder.ifBlank { stringResource(Res.string.lower_third_folder_hint) },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    SwingUtilities.invokeLater {
                        val chooser = createFileChooser {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Select Lower Third Animations Folder"
                            if (lowerThirdFolder.isNotBlank()) {
                                currentDirectory = File(lowerThirdFolder)
                            }
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile.absolutePath
                            onSettingsChange { s ->
                                s.copy(
                                    streamingSettings = s.streamingSettings.copy(
                                        lowerThirdFolder = selected
                                    )
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(Res.string.select_folder),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (lowerThirdFolder.isNotBlank()) {
                TextButton(
                    onClick = {
                        onSettingsChange { s ->
                            s.copy(
                                streamingSettings = s.streamingSettings.copy(
                                    lowerThirdFolder = ""
                                )
                            )
                        }
                    }
                ) {
                    Text(
                        text = stringResource(Res.string.clear_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // File count feedback
        if (lowerThirdFolder.isNotBlank()) {
            Text(
                text = if (jsonFileCount > 0)
                    stringResource(Res.string.lower_third_files_found, jsonFileCount)
                else
                    stringResource(Res.string.lower_third_no_files),
                style = MaterialTheme.typography.bodySmall,
                color = if (jsonFileCount > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Help text
        Text(
            text = stringResource(Res.string.lower_third_folder_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

