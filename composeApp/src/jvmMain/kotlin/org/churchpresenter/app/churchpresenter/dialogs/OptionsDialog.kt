package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.dialogs.DialogFrame
import org.churchpresenter.dialogs.DialogFrameSpec
import org.churchpresenter.dialogs.appDialogFrame
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
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SwitchVideo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.theme.AppShape
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.sharedui.utils.dialogSizeWithin
import org.churchpresenter.sharedui.utils.primaryScreenSizeDp
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.appearance
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.symbol_cancel
import org.churchpresenter.strings.generated.resources.symbol_ok
import org.churchpresenter.strings.generated.resources.apply
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.options
import org.churchpresenter.strings.generated.resources.projection
import org.churchpresenter.strings.generated.resources.server_settings
import org.churchpresenter.strings.generated.resources.output_profiles_tab
import org.churchpresenter.strings.generated.resources.obs_settings
import org.churchpresenter.strings.generated.resources.atem_settings
import org.churchpresenter.strings.generated.resources.companion_satellite_settings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.lowerthird.AtemSettingsTab
import org.churchpresenter.companionsurface.CompanionSatelliteSettingsTab
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.obs.OBSSettingsTab
import org.churchpresenter.appsettings.SystemSettingsTab
import org.churchpresenter.profiles.BackgroundSettingsTab
import org.churchpresenter.profiles.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.builtInRelayEndpoints
import org.churchpresenter.liveoutput.settings.DetectedScreen
import org.churchpresenter.profiles.ProfilesSettingsTab
import org.churchpresenter.liveoutput.settings.ProjectionSettingsTab
import org.churchpresenter.liveoutput.settings.detectScreensFromAwt
import org.churchpresenter.serverui.ServerSettingsTab
import org.churchpresenter.app.churchpresenter.composables.LabeledTab
import org.churchpresenter.app.churchpresenter.composables.LabeledTabIndicator
import org.churchpresenter.app.churchpresenter.composables.labeledTabMinWidth
import org.churchpresenter.app.churchpresenter.composables.TabStripBackArrow
import org.churchpresenter.app.churchpresenter.composables.TabStripForwardArrow
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.obs.OBSWebSocketManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.profiles.LocalSettingsDevMode
import org.churchpresenter.profiles.LocalApplySettings

// Values must equal each tab's own position in SettingsTabStrip's row: PrimaryScrollableTabRow's
// selectedTabIndex/tabIndicatorOffset key off *position*, so a value that outruns its row slot
// draws the selection indicator over a different tab than the one whose content is showing.
private const val TAB_BACKGROUND = 2
private const val TAB_PROFILES = 3
private const val TAB_PROJECTION = 4
private const val TAB_SERVER = 5
private const val TAB_ATEM = 6
private const val TAB_INTEGRATIONS = 7

/** Where [page] sits in the dialog's tab row — what [OptionsDialog]'s `initialTab` takes. */
internal fun optionsTabIndexOf(page: SettingsPage): Int = when (page) {
    SettingsPage.SYSTEM -> 0
    SettingsPage.BIBLE -> 1
    SettingsPage.BACKGROUND -> TAB_BACKGROUND
    SettingsPage.PROFILES -> TAB_PROFILES
    SettingsPage.PROJECTION -> TAB_PROJECTION
    SettingsPage.SERVER -> TAB_SERVER
    SettingsPage.ATEM -> TAB_ATEM
    SettingsPage.INTEGRATIONS -> TAB_INTEGRATIONS
}

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    settingsManager: SettingsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    onDismiss: () -> Unit,
    calendarSync: CalendarSyncService? = null,
    onSave: (AppSettings) -> Unit = {},
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
    onIdentifyNdi: (Int) -> Unit = {},
    onIdentifyOmt: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    obsManager: OBSWebSocketManager? = null,
    companionSatelliteViewModel: CompanionSatelliteViewModel? = null,
    initialTab: Int = 0,
    initialSettings: AppSettings? = null,
    devMode: Boolean = false,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
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
    frame(
        DialogFrameSpec(
            onClose = onDismiss,
            state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, size.width, size.height),
            width = size.width,
            height = size.height
        ),
            title = stringResource(Res.string.options),
            resizable = true,
        ),
    ) {
        OptionsDialogContent(
            theme = theme,
            settingsManager = settingsManager,
            companionServer = companionServer,
            remoteClientManager = remoteClientManager,
            calendarSync = calendarSync,
            onDismiss = onDismiss,
            onSave = onSave,
            onIdentifyScreen = onIdentifyScreen,
            onIdentifyBrowserSource = onIdentifyBrowserSource,
            onIdentifyNdi = onIdentifyNdi,
            onIdentifyOmt = onIdentifyOmt,
            scenes = scenes,
            obsManager = obsManager,
            companionSatelliteViewModel = companionSatelliteViewModel,
            initialTab = initialTab,
            initialSettings = initialSettings,
            devMode = devMode,
        )
    }
}

@Composable
internal fun OptionsDialogContent(
    theme: ThemeMode,
    settingsManager: SettingsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    onDismiss: () -> Unit,
    calendarSync: CalendarSyncService? = null,
    onSave: (AppSettings) -> Unit = {},
    onIdentifyScreen: () -> Unit = {},
    onIdentifyBrowserSource: (Int) -> Unit = {},
    onIdentifyNdi: (Int) -> Unit = {},
    onIdentifyOmt: (Int) -> Unit = {},
    scenes: List<Scene> = emptyList(),
    obsManager: OBSWebSocketManager? = null,
    companionSatelliteViewModel: CompanionSatelliteViewModel? = null,
    initialTab: Int = 0,
    initialSettings: AppSettings? = null,
    detectScreens: () -> List<DetectedScreen> = ::detectScreensFromAwt,
    devMode: Boolean = false,
) {
    var currentSettings by remember { mutableStateOf(initialSettings ?: settingsManager.loadSettings()) }
    val companionSatelliteTabIndex = if (obsManager != null) 8 else 7
    val tabCount = companionSatelliteTabIndex + 1
    var selectedTabIndex by remember(initialTab) { mutableStateOf(initialTab) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabCount - 1)
    val tabScrollState = remember { ScrollState(0) }

    // What the Apply button below does, for a nested dialog to offer as well.
    val applySettings = {
        settingsManager.saveSettings(currentSettings)
        onSave(currentSettings)
    }

    AppWindowRoot(theme = theme) {
        // This window's own spotlight: the helper's tours point at its tabs from the main window.
        GuideSpotlightHost(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsTabStrip(
                        selectedIndex = safeTabIndex,
                        scrollState = tabScrollState,
                        labelStyle = currentSettings.tabLabelStyle,
                        labelMargin = currentSettings.tabLabelMargin,
                        hasObs = obsManager != null,
                        companionSatelliteTabIndex = companionSatelliteTabIndex,
                        onSelect = { selectedTabIndex = it },
                    )

                    // Tab Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        CompositionLocalProvider(
                            LocalApplySettings provides applySettings,
                            LocalSettingsDevMode provides devMode,
                        ) {
                            SettingsTabContent(
                                tabIndex = safeTabIndex,
                                settings = currentSettings,
                                onSettingsChange = { updateFn -> currentSettings = updateFn(currentSettings) },
                                settingsManager = settingsManager,
                                companionServer = companionServer,
                                remoteClientManager = remoteClientManager,
                                calendarSync = calendarSync,
                                onIdentifyScreen = onIdentifyScreen,
                                onIdentifyBrowserSource = onIdentifyBrowserSource,
                                onIdentifyNdi = onIdentifyNdi,
                                onIdentifyOmt = onIdentifyOmt,
                                scenes = scenes,
                                obsManager = obsManager,
                                companionSatelliteViewModel = companionSatelliteViewModel,
                                companionSatelliteTabIndex = companionSatelliteTabIndex,
                                detectScreens = detectScreens,
                            )
                        }
                    }

                    SettingsDialogButtons(
                        onCancel = onDismiss,
                        onApply = applySettings,
                        onOk = {
                            applySettings()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The tab row — with the same overflow arrows as the main window's tab strip, since a dozen tabs
 * outrun the dialog's width long before the window is narrow.
 */
@Composable
private fun SettingsTabStrip(
    selectedIndex: Int,
    scrollState: ScrollState,
    labelStyle: TabLabelStyle,
    labelMargin: TabLabelMargin,
    hasObs: Boolean,
    companionSatelliteTabIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabStripBackArrow(scrollState)
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            scrollState = scrollState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 0.dp,
            minTabWidth = labeledTabMinWidth(labelStyle, labelMargin),
            indicator = { LabeledTabIndicator(selectedIndex) },
        ) {
            // No Song, Stage Monitor or Dictionary tab: the first two are per-profile now and are
            // edited on the Profiles tab, and the dictionary's look is one setting per install,
            // reached from the gear on the Dictionary tab itself.
            listOfNotNull(
                StripTab(0, stringResource(Res.string.appearance), Icons.Filled.Palette, SettingsPage.SYSTEM),
                StripTab(1, stringResource(Res.string.bible), Icons.AutoMirrored.Filled.MenuBook, SettingsPage.BIBLE),
                StripTab(
                    TAB_BACKGROUND,
                    stringResource(Res.string.background),
                    Icons.Filled.Wallpaper,
                    SettingsPage.BACKGROUND,
                ),
                StripTab(
                    TAB_PROFILES,
                    stringResource(Res.string.output_profiles_tab),
                    Icons.Filled.Tune,
                    SettingsPage.PROFILES,
                ),
                StripTab(
                    TAB_PROJECTION,
                    stringResource(Res.string.projection),
                    Icons.Filled.DesktopWindows,
                    SettingsPage.PROJECTION,
                ),
                StripTab(TAB_SERVER, stringResource(Res.string.server_settings), Icons.Filled.Dns, SettingsPage.SERVER),
                StripTab(
                    TAB_ATEM,
                    stringResource(Res.string.atem_settings),
                    Icons.Filled.SwitchVideo,
                    SettingsPage.ATEM,
                ),
                StripTab(
                    TAB_INTEGRATIONS,
                    stringResource(Res.string.obs_settings),
                    Icons.Filled.Videocam,
                    SettingsPage.INTEGRATIONS,
                )
                    .takeIf { hasObs },
                StripTab(
                    companionSatelliteTabIndex,
                    stringResource(Res.string.companion_satellite_settings),
                    Icons.Filled.SettingsRemote,
                ),
            ).forEach { tab ->
                SettingsTab(tab, selectedIndex, labelStyle, labelMargin, onSelect)
            }
        }
        TabStripForwardArrow(scrollState)
    }
}

@Composable
private fun SettingsTabContent(
    tabIndex: Int,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    settingsManager: SettingsManager,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    calendarSync: CalendarSyncService?,
    onIdentifyScreen: () -> Unit,
    onIdentifyBrowserSource: (Int) -> Unit,
    onIdentifyNdi: (Int) -> Unit,
    onIdentifyOmt: (Int) -> Unit,
    scenes: List<Scene>,
    obsManager: OBSWebSocketManager?,
    companionSatelliteViewModel: CompanionSatelliteViewModel?,
    companionSatelliteTabIndex: Int,
    detectScreens: () -> List<DetectedScreen>,
) {
    when (tabIndex) {
        0 -> SystemSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            companionServer = companionServer,
        )
        1 -> BibleSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
        )
        TAB_PROFILES -> ProfilesSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onIdentify = onIdentifyScreen,
        )
        TAB_BACKGROUND -> BackgroundSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            bibleLowerThirdsDir = settingsManager.bibleLowerThirdsDir,
        )
        TAB_PROJECTION -> ProjectionSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            companionServer = companionServer,
            onIdentifyScreen = { onIdentifyScreen() },
            onIdentifyBrowserSource = { index -> onIdentifyBrowserSource(index) },
            onIdentifyNdi = { index -> onIdentifyNdi(index) },
            onIdentifyOmt = { index -> onIdentifyOmt(index) },
            scenes = scenes,
            detectScreens = detectScreens,
            isRelease = BuildConfig.IS_RELEASE,
        )
        TAB_SERVER -> ServerSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            companionServer = companionServer,
            remoteClientManager = remoteClientManager,
            calendarSync = calendarSync,
            builtInRelayUrl = builtInRelayEndpoints.relayUrl,
        )
        TAB_ATEM -> AtemSettingsTab(settings = settings, onSettingsChange = onSettingsChange)
        TAB_INTEGRATIONS -> if (obsManager != null) {
            OBSSettingsTab(settings = settings, onSettingsChange = onSettingsChange, obsManager = obsManager)
        } else {
            CompanionSatelliteSettingsTab(
                settings = settings,
                onSettingsChange = onSettingsChange,
                viewModel = companionSatelliteViewModel
            )
        }
        // Past the OBS tab the numbering depends on whether it is
        // present, so this is matched by its computed index rather than by a
        // literal that would be right in only one of the two cases.
        companionSatelliteTabIndex -> CompanionSatelliteSettingsTab(
            settings = settings,
            onSettingsChange = onSettingsChange,
            viewModel = companionSatelliteViewModel
        )
    }
}

@Composable
private fun SettingsDialogButtons(onCancel: () -> Unit, onApply: () -> Unit, onOk: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostButton(
            shape = AppShape(6.dp),
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
        }

        Spacer(modifier = Modifier.width(8.dp))

        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(stringResource(Res.string.apply))
        }

        Spacer(modifier = Modifier.width(8.dp))

        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onOk,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
        }
    }
}

/** One tab of [SettingsTabStrip]: where it leads, and what it is called and drawn with. */
private class StripTab(val index: Int, val name: String, val icon: ImageVector, val page: SettingsPage? = null)

@Composable
private fun SettingsTab(
    tab: StripTab,
    selectedIndex: Int,
    labelStyle: TabLabelStyle,
    labelMargin: TabLabelMargin,
    onSelect: (Int) -> Unit,
) {
    val index = tab.index
    LabeledTab(
        name = tab.name,
        icon = tab.icon,
        selected = selectedIndex == index,
        labelStyle = labelStyle,
        labelMargin = labelMargin,
        onClick = { onSelect(index) },
        modifier = tab.page?.let { Modifier.guideTarget(GuideTargets.settingsPage(it)) } ?: Modifier,
        // The main window's tab labels, so the two tab rows read as one family.
        textStyle = MaterialTheme.typography.titleSmall.copy(
            fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal
        ),
    )
}
