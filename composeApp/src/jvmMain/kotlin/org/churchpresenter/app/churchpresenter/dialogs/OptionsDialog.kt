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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SwitchVideo
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.appearance
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.symbol_cancel
import churchpresenter.composeapp.generated.resources.symbol_ok
import churchpresenter.composeapp.generated.resources.apply
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.options
import churchpresenter.composeapp.generated.resources.projection
import churchpresenter.composeapp.generated.resources.server_settings
import churchpresenter.composeapp.generated.resources.song
import churchpresenter.composeapp.generated.resources.obs_settings
import churchpresenter.composeapp.generated.resources.atem_settings
import churchpresenter.composeapp.generated.resources.companion_satellite_settings
import churchpresenter.composeapp.generated.resources.stage_monitor
import churchpresenter.composeapp.generated.resources.tab_dictionary
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.TabLabelStyle
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.dialogs.tabs.AtemSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LocalApplySettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CompanionSatelliteSettingsTab
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.dialogs.tabs.OBSSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SystemSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DetectedScreen
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DictionarySettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProjectionSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.detectScreensFromAwt
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ServerSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.StageMonitorSettingsTab
import org.churchpresenter.app.churchpresenter.composables.LABELED_TAB_MIN_WIDTH
import org.churchpresenter.app.churchpresenter.composables.LabeledTab
import org.churchpresenter.app.churchpresenter.composables.LabeledTabIndicator
import org.churchpresenter.app.churchpresenter.composables.TabStripBackArrow
import org.churchpresenter.app.churchpresenter.composables.TabStripForwardArrow
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

private const val TAB_BACKGROUND = 3
private const val TAB_PROJECTION = 4
private const val TAB_SERVER = 5
private const val TAB_STAGE_MONITOR = 6
private const val TAB_ATEM = 7
private const val TAB_DICTIONARY = 8
private const val TAB_INTEGRATIONS = 9

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
    onIdentifyNdi: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
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
            onIdentifyNdi = onIdentifyNdi,
            scenes = scenes,
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
    onIdentifyNdi: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    obsManager: OBSWebSocketManager? = null,
    companionSatelliteViewModel: CompanionSatelliteViewModel? = null,
    initialTab: Int = 0,
    initialSettings: AppSettings? = null,
    detectScreens: () -> List<DetectedScreen> = ::detectScreensFromAwt
) {
    var currentSettings by remember { mutableStateOf(initialSettings ?: settingsManager.loadSettings()) }
    val companionSatelliteTabIndex = if (obsManager != null) 10 else 9
    val tabCount = companionSatelliteTabIndex + 1
    var selectedTabIndex by remember(initialTab) { mutableStateOf(initialTab) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
    val tabScrollState = remember { ScrollState(0) }

        AppWindowRoot(theme = theme) {
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
                            edgePadding = 0.dp,
                            minTabWidth = LABELED_TAB_MIN_WIDTH,
                            indicator = { LabeledTabIndicator(safeTabIndex) },
                        ) {
                            val labelStyle = currentSettings.tabLabelStyle
                            SettingsTab(
                                0,
                                stringResource(Res.string.appearance),
                                Icons.Filled.Palette,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                1,
                                stringResource(Res.string.bible),
                                Icons.Filled.MenuBook,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                2,
                                stringResource(Res.string.song),
                                Icons.Filled.MusicNote,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_BACKGROUND,
                                stringResource(Res.string.background),
                                Icons.Filled.Wallpaper,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_PROJECTION,
                                stringResource(Res.string.projection),
                                Icons.Filled.DesktopWindows,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_SERVER,
                                stringResource(Res.string.server_settings),
                                Icons.Filled.Dns,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_STAGE_MONITOR,
                                stringResource(Res.string.stage_monitor),
                                Icons.Filled.Tv,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_ATEM,
                                stringResource(Res.string.atem_settings),
                                Icons.Filled.SwitchVideo,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            SettingsTab(
                                TAB_DICTIONARY,
                                stringResource(Res.string.tab_dictionary),
                                Icons.Filled.Book,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
                            if (obsManager != null) {
                                SettingsTab(
                                    TAB_INTEGRATIONS,
                                    stringResource(Res.string.obs_settings),
                                    Icons.Filled.Videocam,
                                    safeTabIndex,
                                    labelStyle,
                                ) {
                                    selectedTabIndex = it
                                }
                            }
                            SettingsTab(
                                companionSatelliteTabIndex,
                                stringResource(Res.string.companion_satellite_settings),
                                Icons.Filled.SettingsRemote,
                                safeTabIndex,
                                labelStyle,
                            ) {
                                selectedTabIndex = it
                            }
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
                        // What the Apply button below does, for a nested dialog to offer as well.
                        val applySettings = {
                            settingsManager.saveSettings(currentSettings)
                            onSave(currentSettings)
                        }
                        CompositionLocalProvider(LocalApplySettings provides applySettings) {
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
                                presenterManager = presenterManager,
                                bibleLowerThirdsDir = settingsManager.bibleLowerThirdsDir,
                            )
                            2 -> SongSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                presenterManager = presenterManager,
                                bibleLowerThirdsDir = settingsManager.bibleLowerThirdsDir,
                            )
                            TAB_BACKGROUND -> BackgroundSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                bibleLowerThirdsDir = settingsManager.bibleLowerThirdsDir,
                            )
                            TAB_PROJECTION -> ProjectionSettingsTab(
                                settings = currentSettings,
                                onSettingsChange = { updateFn ->
                                    currentSettings = updateFn(currentSettings)
                                },
                                companionServer = companionServer,
                                onIdentifyScreen = { onIdentifyScreen() },
                                onIdentifyBrowserSource = { index -> onIdentifyBrowserSource(index) },
                                onIdentifyNdi = { index -> onIdentifyNdi(index) },
                                scenes = scenes,
                                detectScreens = detectScreens
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
                            // Past the OBS tab the numbering depends on whether it is
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

@Composable
private fun SettingsTab(
    index: Int,
    name: String,
    icon: ImageVector,
    selectedIndex: Int,
    labelStyle: TabLabelStyle,
    onSelect: (Int) -> Unit,
) {
    LabeledTab(
        name = name,
        icon = icon,
        selected = selectedIndex == index,
        labelStyle = labelStyle,
        onClick = { onSelect(index) },
    )
}
