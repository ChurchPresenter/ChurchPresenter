package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import org.churchpresenter.web.offscreen.OffscreenPage
import org.churchpresenter.web.presenter.CefManager
import org.churchpresenter.server.clearPresentationState
import org.churchpresenter.server.preloadData
import org.churchpresenter.server.updateApiKey
import org.churchpresenter.server.updateAtemConfig
import org.churchpresenter.server.updateFileUploadEnabled
import org.churchpresenter.server.updateMaxMediaUploadMb
import androidx.compose.ui.window.Window
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.Composable
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.LocalWickCorner
import org.churchpresenter.sharedui.guide.LocalWickPlace
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import org.churchpresenter.app.churchpresenter.composables.CrashGuardBanner
import org.churchpresenter.sharedui.utils.rememberScreenDevices
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.theme.LocalThemeCustomization
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.app_name
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.withWindowGeometry
import org.churchpresenter.app.churchpresenter.utils.windowPlacementFromSettings
import org.churchpresenter.app.churchpresenter.utils.windowPlacementToSettings
import org.churchpresenter.app.churchpresenter.utils.startClassWarmUp
import org.churchpresenter.app.churchpresenter.utils.warmStrings
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.appsettings.SetupWizardDialog
import org.churchpresenter.sharedui.language.LanguageProvider
import org.churchpresenter.theme.ThemeCustomization
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.composables.isJavaFxAvailable
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.ServiceAutoLoader
import org.churchpresenter.settings.calendarFolder
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.MacMenuBarActivationFix
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment
import java.util.Locale
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.sharedui.composables.LocalWentLive
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.schedule.LocalOpenCalendar
import org.churchpresenter.diagnostics.StartupProbe
import kotlin.system.exitProcess
import org.churchpresenter.sharedui.guide.LocalSpotlightHost
import androidx.compose.foundation.layout.BoxScope

/** The work done once the window is up: the server's first start, the update check and the story prompt. */
@Composable
internal fun AppRootState.StartupEffect() {
    LaunchedEffect(Unit) {
        beginStartupPrompts()
        // The window comes first. Nothing below is needed to draw it: the server and its preloaded
        // data serve phones, and the JavaFX toolkit drives nothing the window shows.
        appReady = true
        // Off the path to the window, and off the main thread: it costs ~200ms and its only
        // consumer is the crash tag. An absent tag on a very early report reads as "not yet known".
        launch(Dispatchers.IO) {
            preWarmJavaFX()
            CrashReporter.setTag("javafx.available", isJavaFxAvailable().toString())
        }
        withContext(Dispatchers.IO) {
            companionServer.preloadData(
                songStorageDir = appSettings.songSettings.storageDirectory,
                bibleStorageDir = appSettings.bibleSettings.storageDirectory,
                primaryBibleFileName = appSettings.bibleSettings.primaryBible
            )
            companionServer.updateApiKey(
                enabled = appSettings.serverSettings.apiKeyEnabled,
                key = appSettings.serverSettings.apiKey
            )
            companionServer.updateFileUploadEnabled(appSettings.serverSettings.fileUploadEnabled)
            companionServer.updateMaxMediaUploadMb(appSettings.serverSettings.maxMediaUploadMb)
            companionServer.updateAtemConfig(
                appSettings.atemSettings,
                appSettings.streamingSettings.lowerThirdFolder
            )
            if (appSettings.serverSettings.enabled) {
                companionServer.start(
                    port = appSettings.serverSettings.port,
                    hostOverride = appSettings.serverSettings.serverHost
                )
            }
        }
        openStartupPrompts()
    }
}

/**
 * The app's windows: the splash while it starts, then the main window and the outputs, and the
 * first-run dialogs over them.
 */
@Composable
internal fun AppRootState.AppWindows(
    effectiveAppSettings: AppSettings,
    calendarSync: CalendarSyncService,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String?,
    onThemeCustomizationChange: (ThemeCustomization) -> Unit,
) {
    val screens = rememberScreenDevices()
    val savedPlacement = windowPlacementFromSettings(appSettings.windowPlacement)
    val primaryBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds
    val isFloating = savedPlacement == WindowPlacement.Floating
    val (startX, startY) = startupWindowPosition(
        restoreGeometry = shouldRestoreWindowGeometry(isFloating, appSettings.windowX),
        savedX = appSettings.windowX, savedY = appSettings.windowY,
        primaryX = primaryBounds.x, primaryY = primaryBounds.y,
    )
    val (startWidth, startHeight) = startupWindowSize(
        isFloating = isFloating,
        savedWidth = appSettings.windowWidth, savedHeight = appSettings.windowHeight,
        primaryWidth = primaryBounds.width, primaryHeight = primaryBounds.height,
    )
    val state = rememberWindowState(
        placement = savedPlacement,
        position = WindowPosition(startX.dp, startY.dp),
        size = DpSize(startWidth.dp, startHeight.dp),
    )

    if (!appReady) {
        SplashWindow(theme = theme)
    }

    if (appReady && eulaAccepted) {
        MainWindow(state, effectiveAppSettings, calendarSync, tunnelStatus, tunnelUrl, onThemeCustomizationChange)

        LaunchedEffect(mediaViewModel.mediaFinished) {
            if (mediaViewModel.mediaFinished) {
                presenterManager.requestClearDisplay()
                mediaViewModel.clearFinished()
            }
        }

        PresenterWindows(
            screens = screens,
            presenterManager = presenterManager,
            mediaViewModel = mediaViewModel,
            appSettings = effectiveAppSettings,
            identifyingScreen = identifyingScreen,
            serverUrl = companionServer.serverUrl.collectAsState().value,
            qaDisplayUrl = qaDisplayUrl,
            sttManager = sttManager,
        )
    }

    FirstRunDialogs()
}

@Composable
private fun AppRootState.MainWindow(
    state: WindowState,
    effectiveAppSettings: AppSettings,
    calendarSync: CalendarSyncService,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String?,
    onThemeCustomizationChange: (ThemeCustomization) -> Unit,
) {
    val closeApp = {
        appSettings = appSettings.withWindowGeometry(
            placement = windowPlacementToSettings(state.placement),
            isFloating = state.placement == WindowPlacement.Floating,
            width = state.size.width.value.toInt(),
            height = state.size.height.value.toInt(),
            x = state.position.x.value.toInt(),
            y = state.position.y.value.toInt(),
        )
        settingsManager.saveSettings(appSettings)
        if (qaManager.sessionActive) qaManager.toggleSession()
        companionServer.clearPresentationState()
        companionServer.tunnelManager.shutdown()
        OffscreenPage.reportOpenSessions()
        exitApplication()
    }
    // Cmd+Q reaches the web engine before the window; it quits the same way the window's close does.
    val latestCloseApp = rememberUpdatedState(closeApp)
    DisposableEffect(Unit) {
        CefManager.onQuitRequested = { latestCloseApp.value() }
        onDispose { CefManager.onQuitRequested = null }
    }
    Window(
        onCloseRequest = closeApp,
        title = stringResource(Res.string.app_name),
        icon = painterResource(IconRes.drawable.ic_app_icon),
        state = state
    ) {
        val uiScale = LocalThemeCustomization.current.uiScale
        LaunchedEffect(uiScale) {
            window.minimumSize = scaledMainWindowMinimum(uiScale, screenMaxUiScale(window))
        }
        LaunchedEffect(Unit) {
            // The first frame the operator sees; the startup benchmark's end point (StartupProbe).
            withFrameNanos { }
            StartupProbe.firstFrame(exit = { exitProcess(0) })
            // UI stalls count from shortly after this: a first launch stalls while it loads.
            uiStallReporter.firstFrame()
            // render.api is what was asked for; skiko may draw with another API, or fall back to
            // drawing in software on a GPU it cannot use, so every report says which it got and a
            // stall groups on it.
            CrashReporter.setTag("render.api.actual", window.renderApi.name)
            uiStallReporter.renderApi(window.renderApi.name)
            // Each string's first stringResource reads the jar on this thread; read them all now, off it.
            withContext(Dispatchers.IO) { warmStrings() }
            // Then the classes behind the settings pages, profiles, outputs and server, loaded off
            // it at the lowest priority so their first opening does not read the jar.
            startClassWarmUp()
        }
        MacMenuBarActivationFix()
        LanguageProvider(language = currentLanguage) {
            AppWindowRoot(theme = theme) {
                CompositionLocalProvider(
                    LocalMediaViewModel provides mediaViewModel,
                    LocalMainWindowState provides state,
                    LocalOpenCalendar provides { showCalendarWindow = true },
                    LocalWentLive provides { item -> liveDurationLog.wentLive(item) },
                    LocalShortcuts provides remember(appSettings.keyboardShortcutSettings) {
                        ShortcutMap.from(appSettings.keyboardShortcutSettings)
                    },
                    LocalGuideSession provides helperState.session,
                    // Settings and the other windows Wick's tours reach carry the lamp too.
                    // Each window says where it is (LocalWickPlace), which picks what its lamp offers.
                    LocalWickCorner provides remember {
                        { placed: Modifier -> WickLamp(placed, LocalWickPlace.current) }
                    },
                    // A window in a module below the helper (the Bible catalogue) wraps itself in this.
                    LocalSpotlightHost provides remember {
                        { modifier: Modifier, content: @Composable BoxScope.() -> Unit ->
                            GuideSpotlightHost(modifier) { content() }
                        }
                    },
                ) {
                    GuideSpotlightHost(modifier = Modifier.fillMaxSize(), wick = false) {
                    MainWindowContent(
                        frame = this@Window,
                        bannerModifier = Modifier.align(Alignment.TopCenter),
                        helperModifier = Modifier.align(Alignment.BottomEnd),
                        effectiveAppSettings,
                        calendarSync,
                        tunnelStatus,
                        tunnelUrl,
                        onThemeCustomizationChange,
                    )
                    } // end GuideSpotlightHost (window content)
                }
            }
        }
    }
}

@Composable
private fun AppRootState.MainWindowContent(
    frame: FrameWindowScope,
    bannerModifier: Modifier,
    helperModifier: Modifier,
    effectiveAppSettings: AppSettings,
    calendarSync: CalendarSyncService,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String?,
    onThemeCustomizationChange: (ThemeCustomization) -> Unit,
) {
    val win = remember { MainWindowState(appSettings.calendarSync.enabled) }
    // And the other half of leaving it to run: the service that is about
    // to start loads itself into the Schedule. Reads calendar.json on a
    // background thread, so it works with the Calendar window closed.
    val calendarFolder =
        remember(appSettings.calendarStorageDirectory) { appSettings.calendarFolder() }
    val cueHost = remember(this) { calendarCueHost() }
    // Kept here rather than inside its loop so the Schedule tab can read what it
    // is about to load, and load it early.
    val serviceAutoLoader = remember(calendarFolder) {
        val store = CalendarStore(calendarFolder)
        ServiceAutoLoader(
            document = { withContext(Dispatchers.IO) { store.load().document } },
            host = cueHost.copy(
                loadIntoSchedule = { items, timing, replace, armed, startTime ->
                    UsageEvents.record(UsageEvent.CALENDAR_AUTO_LOADED)
                    loadFromCalendar(items, timing, replace, armed, startTime)
                },
            ),
            save = { document -> withContext(Dispatchers.IO) { store.save(document) } },
        )
    }
    val root = this
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val scope = remember(
        root, win, frame, bannerModifier, calendarSync, effectiveAppSettings, tunnelStatus, tunnelUrl,
        onThemeCustomizationChange, calendarFolder, cueHost, serviceAutoLoader
    ) {
        MainWindowScope(
            root = root,
            win = win,
            frame = frame,
            bannerModifier = bannerModifier,
            calendarSync = calendarSync,
            effectiveAppSettings = effectiveAppSettings,
            tunnelStatus = tunnelStatus,
            tunnelUrl = tunnelUrl,
            onThemeCustomizationChange = onThemeCustomizationChange,
            calendarFolder = calendarFolder,
            cueHost = cueHost,
            serviceAutoLoader = serviceAutoLoader,
        )
    }
    with(scope) {
        RemoteAddRequests()
        RemoteEnrollRequests()
        RemoteRemoveRequests()
        RemoteProjectRequests()
        RemoteDrawingRequests()
        CalendarAutomationWiring()
        ShowControlEffects()
        ControlInEffects()
        ServerCommandWiring()
        QaAndPresentationConnectRequests()
        AdminAndMusicianConnectRequests()
        ServerBroadcastWiring()

        AppMenuBar()
        frame.ThemedMenuBar()
        ThemedTitleBars()
        MainDesktopHost()
        // After MainDesktop, not before it: siblings in a Box draw in order and
        // MainDesktop's root is an opaque fillMaxSize surface, so a banner
        // emitted above this line is painted over and never seen.
        CrashGuardBanner(bannerModifier)
        SettingsDialogs()
        InfoDialogs()
        ToolWindows()
        CalendarManagerWindow()
        MoreToolWindows()

        RemoteApprovalDialog()
        ActivityToasts()
        root.HelperHost(helperModifier)
    }
}

/** The setup wizard on a first run, and the licence until it is accepted. */
@Composable
private fun AppRootState.FirstRunDialogs() {
    if (appReady && eulaAccepted && showSetupWizard) {
        LaunchedEffect(Unit) { UsageEvents.record(UsageEvent.SETUP_WIZARD_OPENED) }
        val closeSetupWizard = {
            val updated = appSettings.copy(setupWizardShown = true)
            settingsManager.saveSettings(updated)
            appSettings = updated
            showSetupWizard = false
        }
        SetupWizardDialog(
            theme = theme,
            selectedLanguage = currentLanguage,
            // Stands down for the windows its own steps open. The wizard floats so it is not lost
            // behind the main window, but that same flag puts it in front of anything it sends the
            // user to — Settings from any step, the Converter from the song books step — which
            // reads as the button having done nothing.
            alwaysOnTop = !showOptionsDialog && !showConverterWindow,
            bibleDirectory = appSettings.bibleSettings.storageDirectory,
            songsDirectory = appSettings.songSettings.storageDirectory,
            onLanguageSelected = { language ->
                currentLanguage = language
                appSettings = appSettings.copy(language = language.code)
                settingsManager.saveSettings(appSettings)
                Locale.setDefault(Locale.forLanguageTag(language.code))
            },
            onThemeSelected = { newTheme ->
                theme = newTheme
                appSettings = appSettings.copy(theme = newTheme.toString())
                settingsManager.saveSettings(appSettings)
            },
            onOpenSettings = {
                UsageEvents.record(UsageEvent.SETUP_WIZARD_OPENED_SETTINGS)
                openOptionsDialog(0)
            },
            onOpenConverter = {
                UsageEvents.record(UsageEvent.SETUP_WIZARD_OPENED_CONVERTER)
                converterInitialTab = ConverterTab.SONGS
                converterInitialSource = null
                showConverterWindow = true
            },
            onDismiss = {
                UsageEvents.record(UsageEvent.SETUP_WIZARD_SKIPPED)
                closeSetupWizard()
            },
            onFinish = {
                UsageEvents.record(UsageEvent.SETUP_WIZARD_FINISHED)
                closeSetupWizard()
            },
        )
    }

    LicenseDialog(
        isVisible = appReady && !eulaAccepted,
        onAccept = {
            val updated = appSettings.copy(eulaAcceptedVersion = CURRENT_EULA_VERSION)
            settingsManager.saveSettings(updated)
            appSettings = updated
            eulaAccepted = true
        },
        onDecline = { exitApplication() }
    )
}
