package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.ui.theme.setTheme
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
                            text = { Text(stringResource(Res.string.text_settings_and_colors)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text(stringResource(Res.string.background)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 4,
                            onClick = { selectedTabIndex = 4 },
                            text = { Text(stringResource(Res.string.background_images)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 5,
                            onClick = { selectedTabIndex = 5 },
                            text = { Text(stringResource(Res.string.folders)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 6,
                            onClick = { selectedTabIndex = 6 },
                            text = { Text(stringResource(Res.string.projection)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 7,
                            onClick = { selectedTabIndex = 7 },
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

                            2 -> PlaceholderTab(stringResource(Res.string.text_settings_tab_content))
                            3 -> PlaceholderTab(stringResource(Res.string.background_tab_content))
                            4 -> PlaceholderTab(stringResource(Res.string.background_images_tab_content))
                            5 -> PlaceholderTab(stringResource(Res.string.folders_tab_content))
                            6 -> PlaceholderTab(stringResource(Res.string.projection_tab_content))
                            7 -> PlaceholderTab(stringResource(Res.string.other_tab_content))
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
