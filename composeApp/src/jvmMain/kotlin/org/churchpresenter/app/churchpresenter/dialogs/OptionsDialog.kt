package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.dialogSizeWithin
import org.churchpresenter.app.churchpresenter.primaryScreenSizeDp
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DictionarySettingsTab
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.appearance
import org.churchpresenter.resources.generated.resources.background
import org.churchpresenter.resources.generated.resources.bible
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.symbol_cancel
import org.churchpresenter.resources.generated.resources.symbol_ok
import org.churchpresenter.resources.generated.resources.display_lower_third
import org.churchpresenter.resources.generated.resources.apply
import org.churchpresenter.resources.generated.resources.ok
import org.churchpresenter.resources.generated.resources.options
import org.churchpresenter.resources.generated.resources.projection
import org.churchpresenter.resources.generated.resources.server_settings
import org.churchpresenter.resources.generated.resources.song
import org.churchpresenter.resources.generated.resources.obs_settings
import org.churchpresenter.resources.generated.resources.atem_settings
import org.churchpresenter.resources.generated.resources.companion_satellite_settings
import org.churchpresenter.resources.generated.resources.stage_monitor
import org.churchpresenter.resources.generated.resources.tab_dictionary
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.companionserver.CompanionServer
import org.churchpresenter.app.churchpresenter.dialogs.tabs.AtemSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CompanionSatelliteSettingsTab
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.dialogs.tabs.OBSSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SystemSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DetectedScreen
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProjectionSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.detectScreensFromAwt
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ServerSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LowerThirdSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.StageMonitorSettingsTab
import org.churchpresenter.ui.TabStripBackArrow
import org.churchpresenter.ui.TabStripForwardArrow
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

private const val TAB_BACKGROUND = 3
private const val TAB_PROJECTION = 4
private const val TAB_LOWER_THIRD = 5
private const val TAB_SERVER = 6
private const val TAB_STAGE_MONITOR = 7
private const val TAB_ATEM = 8
private const val TAB_DICTIONARY = 9
private const val TAB_INTEGRATIONS = 10

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settingsManager: SettingsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    presenterManager: PresenterManager,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit = {},
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    obsManager: OBSWebSocketManager? = null,
    companionSatelliteViewModel: CompanionSatelliteViewModel? = null,
    initialTab: Int = 0,
    initialSettings: AppSettings? = null
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    // 1400x900 is bigger than a 1366x768 laptop panel in both directions, so on one this dialog
    // opened with its own edges — and the Save/Cancel row along the bottom — off the screen, with no
    // window edge left to drag it back by. It is resizable and every tab scrolls, so giving it less
    // room costs a scroll; giving it more than the display has costs the controls.
    val size = remember {
        val screen = primaryScreenSizeDp()
        dialogSizeWithin(1400.dp, 900.dp, screen.width, screen.height)
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, size.width, size.height),
            width = size.width,
            height = size.height
        ),
        title = stringResource(Res.string.options),
        resizable = true
    ) {
        OptionsDialogContent(
            theme = theme,
            settingsManager = settingsManager,
            companionServer = companionServer,
            remoteClientManager = remoteClientManager,
            presenterManager = presenterManager,
            onDismiss = onDismiss,
            onSave = onSave,
            onIdentifyScreen = onIdentifyScreen,
            onIdentifyBrowserSource = onIdentifyBrowserSource,
            scenes = scenes,
            onOpenLottieGen = onOpenLottieGen,
            obsManager = obsManager,
            companionSatelliteViewModel = companionSatelliteViewModel,
            initialTab = initialTab,
            initialSettings = initialSettings,
        )
    }
}

@Composable
internal fun OptionsDialogContent(
    theme: ThemeMode,
    settingsManager: SettingsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    presenterManager: PresenterManager,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit = {},
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    obsManager: OBSWebSocketManager? = null,
    companionSatelliteViewModel: CompanionSatelliteViewModel? = null,
    initialTab: Int = 0,
    initialSettings: AppSettings? = null,
    detectScreens: () -> List<DetectedScreen> = ::detectScreensFromAwt
) {
    var currentSettings by remember { mutableStateOf(initialSettings ?: settingsManager.loadSettings()) }
    val companionSatelliteTabIndex = if (obsManager != null) 11 else 10
    val tabCount = companionSatelliteTabIndex + 1
    var selectedTabIndex by remember(initialTab) { mutableStateOf(initialTab) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
    val tabScrollState = remember { ScrollState(0) }

        AppThemeWrapper(theme = theme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab Row — with the same overflow arrows as the main window's tab strip, since
                    // a dozen tabs outrun the dialog's width long before the window is narrow.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabStripBackArrow(tabScrollState)
                        PrimaryScrollableTabRow(
                            selectedTabIndex = safeTabIndex,
                            modifier = Modifier.weight(1f),
                            scrollState = tabScrollState,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            edgePadding = 0.dp
                        ) {
                            Tab(
                                selected = safeTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text(stringResource(Res.string.appearance)) }
                            )
                            Tab(
                                selected = safeTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text(stringResource(Res.string.bible)) }
                            )
                            Tab(
                                selected = safeTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text(stringResource(Res.string.song)) }
                            )
                            Tab(
                                selected = safeTabIndex == 3,
                                onClick = { selectedTabIndex = 3 },
                                text = { Text(stringResource(Res.string.background)) }
                            )
                            Tab(
                                selected = safeTabIndex == 4,
                                onClick = { selectedTabIndex = 4 },
                                text = { Text(stringResource(Res.string.projection)) }
                            )
                            Tab(
                                selected = safeTabIndex == 5,
                                onClick = { selectedTabIndex = 5 },
                                text = { Text(stringResource(Res.string.display_lower_third)) }
                            )
                            Tab(
                                selected = safeTabIndex == 6,
                                onClick = { selectedTabIndex = 6 },
                                text = { Text(stringResource(Res.string.server_settings)) }
                            )
                            Tab(
                                selected = safeTabIndex == 7,
                                onClick = { selectedTabIndex = 7 },
                                text = { Text(stringResource(Res.string.stage_monitor)) }
                            )
                            Tab(
                                selected = safeTabIndex == 8,
                                onClick = { selectedTabIndex = 8 },
                                text = { Text(stringResource(Res.string.atem_settings)) }
                            )
                            Tab(
                                selected = safeTabIndex == 9,
                                onClick = { selectedTabIndex = 9 },
                                text = { Text(stringResource(Res.string.tab_dictionary)) }
                            )
                            if (obsManager != null) {
                                Tab(
                                    selected = safeTabIndex == 10,
                                    onClick = { selectedTabIndex = 10 },
                                    text = { Text(stringResource(Res.string.obs_settings)) }
                                )
                            }
                            Tab(
                                selected = safeTabIndex == companionSatelliteTabIndex,
                                onClick = { selectedTabIndex = companionSatelliteTabIndex },
                                text = { Text(stringResource(Res.string.companion_satellite_settings)) }
                            )
                        }
                        TabStripForwardArrow(tabScrollState)
                    }

                    // Tab Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (safeTabIndex) {
                            0 -> SystemSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                companionServer = companionServer
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
                            TAB_BACKGROUND -> BackgroundSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            TAB_PROJECTION -> ProjectionSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                companionServer = companionServer,
                                onIdentifyScreen = { onIdentifyScreen() },
                                onIdentifyBrowserSource = { index -> onIdentifyBrowserSource(index) },
                                scenes = scenes,
                                detectScreens = detectScreens
                            )
                            TAB_LOWER_THIRD -> LowerThirdSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                onOpenLottieGen = onOpenLottieGen
                            )
                            TAB_SERVER -> ServerSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                companionServer = companionServer,
                                remoteClientManager = remoteClientManager
                            )
                            TAB_STAGE_MONITOR -> StageMonitorSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            TAB_ATEM -> AtemSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            TAB_DICTIONARY -> DictionarySettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                }
                            )
                            TAB_INTEGRATIONS -> if (obsManager != null) {
                                OBSSettingsTab(
                                    settings = currentSettings,
                                    onSettingsChange = { updateFn ->
                                        currentSettings = updateFn(currentSettings)
                                    },
                                    obsManager = obsManager
                                )
                            } else {
                                CompanionSatelliteSettingsTab(
                                    settings = currentSettings,
                                    onSettingsChange = { updateFn ->
                                        currentSettings = updateFn(currentSettings)
                                    },
                                    viewModel = companionSatelliteViewModel
                                )
                            }
                            // Past index 10 the numbering depends on whether the OBS tab is
                            // present, so this is matched by its computed index rather than by a
                            // literal that would be right in only one of the two cases.
                            companionSatelliteTabIndex -> CompanionSatelliteSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                viewModel = companionSatelliteViewModel
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
                            shape = RoundedCornerShape(6.dp),
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            shape = RoundedCornerShape(6.dp),
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
                            shape = RoundedCornerShape(6.dp),
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
