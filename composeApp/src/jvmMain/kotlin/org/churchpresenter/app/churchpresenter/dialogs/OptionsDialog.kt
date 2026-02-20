package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.background_images
import churchpresenter.composeapp.generated.resources.background_images_tab_content
import churchpresenter.composeapp.generated.resources.background_tab_content
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.folders
import churchpresenter.composeapp.generated.resources.folders_tab_content
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.options
import churchpresenter.composeapp.generated.resources.other
import churchpresenter.composeapp.generated.resources.other_tab_content
import churchpresenter.composeapp.generated.resources.projection
import churchpresenter.composeapp.generated.resources.projection_tab_content
import churchpresenter.composeapp.generated.resources.song
import churchpresenter.composeapp.generated.resources.text_settings_and_colors
import churchpresenter.composeapp.generated.resources.text_settings_tab_content
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProjectionSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit = {}
) {
    if (!isVisible) return

    var currentSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    Dialog(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 1000.dp, height = 700.dp),
        title = stringResource(Res.string.options),
        resizable = true
    ) {
        AppThemeWrapper(theme = theme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab Row
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text(stringResource(Res.string.bible)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text(stringResource(Res.string.song)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text(stringResource(Res.string.background)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text(stringResource(Res.string.projection)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 4,
                            onClick = { selectedTabIndex = 4 },
                            text = { Text(stringResource(Res.string.other)) }
                        )
                    }

                    // Tab Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (selectedTabIndex) {
                            0 -> BibleSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )

                            1 -> SongSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            2 -> BackgroundSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            3 -> ProjectionSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            4 -> PlaceholderTab(stringResource(Res.string.other_tab_content))
                        }
                    }

                    // Button Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("✗ ${stringResource(Res.string.cancel)}")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                settingsManager.saveSettings(currentSettings)
                                onSave(currentSettings)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("✓ ${stringResource(Res.string.ok)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
