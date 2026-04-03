package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ScrollableTabRow
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
import churchpresenter.composeapp.generated.resources.appearance
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.symbol_cancel
import churchpresenter.composeapp.generated.resources.symbol_ok
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.apply
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.options
import churchpresenter.composeapp.generated.resources.projection
import churchpresenter.composeapp.generated.resources.server_settings
import churchpresenter.composeapp.generated.resources.song
import churchpresenter.composeapp.generated.resources.statistics
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SystemSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.MediaSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProjectionSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ServerSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LowerThirdSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.StatisticsTab
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settingsManager: SettingsManager,
    statisticsManager: StatisticsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    presenterManager: PresenterManager,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit = {},
    onIdentifyScreen: () -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit = {},
    scenes: List<org.churchpresenter.app.churchpresenter.models.Scene> = emptyList()
) {
    if (!isVisible) return

    var currentSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val isDarkTheme = when (theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
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
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        edgePadding = 0.dp
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text(stringResource(Res.string.appearance)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text(stringResource(Res.string.bible)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text(stringResource(Res.string.song)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text(stringResource(Res.string.background)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 4,
                            onClick = { selectedTabIndex = 4 },
                            text = { Text(stringResource(Res.string.pictures)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 5,
                            onClick = { selectedTabIndex = 5 },
                            text = { Text(stringResource(Res.string.projection)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 6,
                            onClick = { selectedTabIndex = 6 },
                            text = { Text(stringResource(Res.string.display_lower_third)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 7,
                            onClick = { selectedTabIndex = 7 },
                            text = { Text(stringResource(Res.string.server_settings)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 8,
                            onClick = { selectedTabIndex = 8 },
                            text = { Text(stringResource(Res.string.statistics)) }
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
                            0 -> SystemSettingsTab(
                                currentTheme = theme,
                                onThemeChange = onThemeChange,
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            1 -> BibleSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                presenterManager = presenterManager
                            )
                            2 -> SongSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                presenterManager = presenterManager
                            )
                            3 -> BackgroundSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            4 -> MediaSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            5 -> ProjectionSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                onIdentifyScreen = { onIdentifyScreen() },
                                scenes = scenes
                            )
                            6 -> LowerThirdSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                serverUrl = companionServer.serverUrl.value,
                                isDarkTheme = isDarkTheme
                            )
                            7 -> ServerSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                companionServer = companionServer,
                                remoteClientManager = remoteClientManager
                            )
                            8 -> StatisticsTab(
                                statisticsManager = statisticsManager
                            )
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
                            Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                settingsManager.saveSettings(currentSettings)
                                onSave(currentSettings)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(stringResource(Res.string.apply))
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
                            Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
                        }
                    }
                }
            }
        }
    }
}
