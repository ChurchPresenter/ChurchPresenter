package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.presenter_view_title
import churchpresenter.composeapp.generated.resources.key_output_title
import churchpresenter.composeapp.generated.resources.screen_number
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsDevice
import java.util.Locale


fun main() {
    // Pre-warm JavaFX on a background thread before UI starts
    preWarmJavaFX()

    // Initialize JCEF (Chromium) for embedded web browsing
    CefManager.init()

    application {
        // Business logic layer
        val settingsManager = remember { SettingsManager() }
        var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
        // Set custom VLC path from saved settings before first VLC access
        remember { vlcCustomPath = appSettings.projectionSettings.vlcPath }
        val presenterManager = remember { PresenterManager() }


        var licenseAccepted by remember { mutableStateOf(appSettings.licenseAccepted) }

        var currentLanguage by remember {
            val savedLanguageCode = appSettings.language
            val language = Language.entries.find { it.code == savedLanguageCode } ?: Language.ENGLISH
            Locale.setDefault(Locale.forLanguageTag(language.code))
            mutableStateOf(language)
        }

        var scheduleActions by remember { mutableStateOf(ScheduleActions()) }
        val currentScheduleActions by rememberUpdatedState(scheduleActions)

        val mediaViewModel = remember { MediaViewModel() }

        var identifyingScreen by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        var theme by remember {
            val savedTheme = when (appSettings.theme.uppercase()) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            mutableStateOf(savedTheme)
        }
        val companionServer = remember { CompanionServer() }
        var showOptionsDialog by remember { mutableStateOf(false) }
        var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
        var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

        // Preload songs and bible at startup. Uses CompanionServer's own IO scope —
        // no Compose composable context needed.
        remember(Unit) {
            companionServer.preloadData(
                songStorageDir = appSettings.songSettings.storageDirectory,
                bibleStorageDir = appSettings.bibleSettings.storageDirectory,
                primaryBibleFileName = appSettings.bibleSettings.primaryBible
            )
            companionServer.updateLowerThirdFolder(appSettings.streamingSettings.lowerThirdFolder)
            // Auto-start server if user previously enabled it
            if (appSettings.serverSettings.enabled) {
                companionServer.start(appSettings.serverSettings.port)
            }
        }

        val screens = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices }
        val state = rememberWindowState(placement = WindowPlacement.Maximized)


        if (licenseAccepted) {
            Window(
                onCloseRequest = { exitApplication() },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.ic_app_icon),
                state = state
            ) {
                LanguageProvider(language = currentLanguage) {
                    AppThemeWrapper(theme = theme) {
                        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                            NavigationTopBar(
                                onAbout = { presenterManager.setShowPresenterWindow(true) },
                                onKeyboardShortcuts = { showKeyboardShortcutsDialog = true },
                                theme = {
                                    appSettings = appSettings.copy(theme = it.toString())
                                    theme = it
                                    settingsManager.saveSettings(appSettings)
                                },
                                onLanguageChange = { language ->
                                    currentLanguage = language
                                    appSettings = appSettings.copy(language = language.code)
                                    settingsManager.saveSettings(appSettings)
                                    Locale.setDefault(Locale.forLanguageTag(language.code))
                                },
                                onSettings = { showOptionsDialog = true },
                                onExit = { exitApplication() },
                                onAddToSchedule = {},
                                onNewSchedule = { currentScheduleActions.newSchedule() },
                                onOpenSchedule = { currentScheduleActions.openSchedule() },
                                onSaveSchedule = { currentScheduleActions.saveSchedule() },
                                onSaveScheduleAs = { currentScheduleActions.saveScheduleAs() },
                                onCloseSchedule = { currentScheduleActions.newSchedule() },
                                onRemoveFromSchedule = {
                                    selectedScheduleItemId?.let {
                                        currentScheduleActions.removeSelected()
                                        selectedScheduleItemId = null
                                    }
                                },
                                onClearSchedule = {
                                    currentScheduleActions.clearSchedule()
                                    selectedScheduleItemId = null
                                },
                            )
                            MainDesktop(
                                onVerseSelected = { verses -> presenterManager.setSelectedVerses(verses) },
                                onSongItemSelected = { presenterManager.setLyricSection(it) },
                                appSettings = appSettings,
                                presenterManager = presenterManager,
                                onScheduleActionsReady = { scheduleActions = it },
                                presenting = { mode ->
                                    presenterManager.setPresentingMode(mode)
                                    if (mode != Presenting.NONE) presenterManager.setShowPresenterWindow(true)
                                },
                                onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
                                onShowSettings = { showOptionsDialog = true },
                                onSettingsChange = { updateFn ->
                                    appSettings = updateFn(appSettings)
                                    settingsManager.saveSettings(appSettings)
                                },
                                theme = theme,
                                onSongsLoaded = { songs -> companionServer.updateSongs(songs) },
                                onBibleLoaded = { bible, translation -> companionServer.updateBible(bible, translation) },
                                onScheduleChanged = { items -> companionServer.updateSchedule(items) },
                                serverUrl = companionServer.serverUrl.collectAsState().value
                            )
                            OptionsDialog(
                                isVisible = showOptionsDialog,
                                theme = theme,
                                settingsManager = settingsManager,
                                companionServer = companionServer,
                                presenterManager = presenterManager,
                                onDismiss = { showOptionsDialog = false },
                                onSave = { updated ->
                                    appSettings = updated
                                    settingsManager.saveSettings(updated)
                                    // Re-preload in case bible/song directories changed
                                    companionServer.preloadData(
                                        songStorageDir = updated.songSettings.storageDirectory,
                                        bibleStorageDir = updated.bibleSettings.storageDirectory,
                                        primaryBibleFileName = updated.bibleSettings.primaryBible
                                    )
                                    companionServer.updateLowerThirdFolder(updated.streamingSettings.lowerThirdFolder)
                                },
                                onThemeChange = { newTheme ->
                                    appSettings = appSettings.copy(theme = newTheme.toString())
                                    theme = newTheme
                                    settingsManager.saveSettings(appSettings)
                                },
                                onIdentifyScreen = {
                                    identifyingScreen = true
                                    coroutineScope.launch {
                                        delay(5_000L)
                                        identifyingScreen = false
                                    }
                                }
                            )
                            KeyboardShortcutsDialog(
                                isVisible = showKeyboardShortcutsDialog,
                                onDismiss = { showKeyboardShortcutsDialog = false }
                            )
                        }
                    }
                }
            }

            PresenterWindows(
                screens = screens,
                presenterManager = presenterManager,
                mediaViewModel = mediaViewModel,
                appSettings = appSettings,
                identifyingScreen = identifyingScreen,
            )
        } else {
            LicenseDialog(
                onAccept = {
                    val updated = appSettings.copy(licenseAccepted = true)
                    settingsManager.saveSettings(updated)
                    appSettings = updated
                    licenseAccepted = true
                },
                onDecline = { exitApplication() }
            )
        }
    }
}

@Composable
private fun PresenterWindows(
    screens: Array<GraphicsDevice>,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel,
    appSettings: AppSettings,
    identifyingScreen: Boolean,
) {
    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
    val selectedVerses by presenterManager.selectedVerses
    val lyricSection by presenterManager.lyricSection
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
    val lottiePauseFrame by presenterManager.lottiePauseFrame
    val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
    val lottieTrigger by presenterManager.lottieTrigger
    val websiteUrl by presenterManager.websiteUrl

    val proj = appSettings.projectionSettings
    // Only create windows for screens that actually exist beyond screen 0 (the main app screen).
    // If there is only one screen, there is nowhere to project to — skip entirely.
    val windowCount = (screens.size - 1).coerceIn(0, 4)

    for (i in 0 until windowCount) {
        val screenAssignment = when (i) {
            0 -> proj.screen1Assignment
            1 -> proj.screen2Assignment
            2 -> proj.screen3Assignment
            else -> proj.screen4Assignment
        }

        // DeckLink outputs are handled separately — no Compose window needed
        if (screenAssignment.targetType == "decklink") {
            // DeckLink rendering is managed by DeckLinkPresenter (launched externally)
            continue
        }

        // Resolve target display: -1 = auto (screen index + 1), otherwise use the configured index
        val targetScreenIndex = if (screenAssignment.targetDisplay == -1) {
            i + 1  // auto: same as original behavior
        } else {
            screenAssignment.targetDisplay
        }

        // Skip if the target screen doesn't exist
        if (targetScreenIndex < 0 || targetScreenIndex >= screens.size) continue

        // Derive output role from key target configuration
        val primaryRole = screenAssignment.primaryOutputRole

        val windowState = remember(i, targetScreenIndex) {
            val b = screens[targetScreenIndex].defaultConfiguration.bounds
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(b.x.dp, b.y.dp),
                width = b.width.dp,
                height = b.height.dp
            )
        }

        // Primary window (fill or normal)
        val presenterTitle = stringResource(Res.string.presenter_view_title, i + 1)
        Window(
            visible = showPresenterWindow,
            title = presenterTitle,
            onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
            state = windowState,
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings, outputRole = primaryRole) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    mediaViewModel.pause()
                                    presenterManager.setPresentingMode(Presenting.NONE)
                                    true
                                } else false
                            }
                    ) {
                        when (presentingMode) {
                            Presenting.BIBLE ->
                                if (screenAssignment.showBible) {
                                    BiblePresenter(
                                        selectedVerses = selectedVerses,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole
                                    )
                                }
                            Presenting.LYRICS ->
                                if (screenAssignment.showSongs) {
                                    SongPresenter(
                                        lyricSection = lyricSection,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole
                                    )
                                }
                            Presenting.PICTURES ->
                                if (screenAssignment.showPictures)
                                    PicturePresenter(imagePath = selectedImagePath, animationType = animationType, transitionDuration = transitionDuration)
                            Presenting.PRESENTATION ->
                                if (screenAssignment.showPictures)
                                    SlidePresenter(slide = selectedSlide, animationType = animationType, transitionDuration = transitionDuration)
                            Presenting.MEDIA ->
                                if (screenAssignment.showMedia) {
                                    if (mediaViewModel.isAudioFile) {
                                        // Audio: playback handled by hidden VideoPlayer in MainDesktop
                                        // Projection shows background only
                                    } else {
                                        MediaPresenter(modifier = Modifier.fillMaxSize(), audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId)
                                    }
                                }
                            Presenting.LOWER_THIRD ->
                                if (screenAssignment.showStreaming)
                                    LowerThirdPresenter(
                                        jsonContent = lottieJsonContent,
                                        pauseAtFrame = lottiePauseAtFrame,
                                        pauseFrame = lottiePauseFrame,
                                        pauseDurationMs = lottiePauseDurationMs,
                                        trigger = lottieTrigger,
                                        appSettings = appSettings
                                    )
                            Presenting.ANNOUNCEMENTS ->
                                if (screenAssignment.showAnnouncements)
                                    AnnouncementsPresenter(
                                        text = presenterManager.announcementText.value,
                                        appSettings = appSettings,
                                        outputRole = primaryRole
                                    )
                            Presenting.WEBSITE ->
                                WebsitePresenter(
                                    url = websiteUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                    onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
                                    onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                    onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                    audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
                                )
                            Presenting.NONE -> { /* nothing */ }
                        }

                        // Clear live browser ref when leaving WEBSITE mode
                        androidx.compose.runtime.LaunchedEffect(presentingMode) {
                            if (presentingMode != Presenting.WEBSITE) {
                                presenterManager.setLiveBrowser(null)
                            }
                        }

                        if (identifyingScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.screen_number, i + 1),
                                    color = Color.White,
                                    fontSize = 96.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Key output window — spawned when a key target is configured
        if (screenAssignment.hasKeyOutput && screenAssignment.keyTargetType != "decklink") {
            val keyScreenIndex = screenAssignment.keyTargetDisplay
            if (keyScreenIndex in screens.indices) {
                val keyWindowState = remember(i, keyScreenIndex) {
                    val b = screens[keyScreenIndex].defaultConfiguration.bounds
                    WindowState(
                        placement = WindowPlacement.Floating,
                        position = WindowPosition(b.x.dp, b.y.dp),
                        width = b.width.dp,
                        height = b.height.dp
                    )
                }

                val keyOutputTitle = stringResource(Res.string.key_output_title, i + 1)
                Window(
                    visible = showPresenterWindow,
                    title = keyOutputTitle,
                    onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                    state = keyWindowState,
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                ) {
                    CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                        PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings, outputRole = Constants.OUTPUT_ROLE_KEY) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                            mediaViewModel.pause()
                                            presenterManager.setPresentingMode(Presenting.NONE)
                                            true
                                        } else false
                                    }
                            ) {
                                when (presentingMode) {
                                    Presenting.BIBLE ->
                                        if (screenAssignment.showBible) {
                                            BiblePresenter(
                                                selectedVerses = selectedVerses,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )
                                        }
                                    Presenting.LYRICS ->
                                        if (screenAssignment.showSongs) {
                                            SongPresenter(
                                                lyricSection = lyricSection,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )
                                        }
                                    Presenting.PICTURES ->
                                        if (screenAssignment.showPictures)
                                            PicturePresenter(imagePath = selectedImagePath, animationType = animationType, transitionDuration = transitionDuration)
                                    Presenting.PRESENTATION ->
                                        if (screenAssignment.showPictures)
                                            SlidePresenter(slide = selectedSlide, animationType = animationType, transitionDuration = transitionDuration)
                                    Presenting.MEDIA ->
                                        if (screenAssignment.showMedia) {
                                            if (mediaViewModel.isAudioFile) {
                                                // Audio: background only
                                            } else {
                                                MediaPresenter(modifier = Modifier.fillMaxSize(), audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId)
                                            }
                                        }
                                    Presenting.LOWER_THIRD ->
                                        if (screenAssignment.showStreaming)
                                            LowerThirdPresenter(
                                                jsonContent = lottieJsonContent,
                                                pauseAtFrame = lottiePauseAtFrame,
                                                pauseFrame = lottiePauseFrame,
                                                pauseDurationMs = lottiePauseDurationMs,
                                                trigger = lottieTrigger,
                                                appSettings = appSettings
                                            )
                                    Presenting.ANNOUNCEMENTS ->
                                        if (screenAssignment.showAnnouncements)
                                            AnnouncementsPresenter(
                                                text = presenterManager.announcementText.value,
                                                appSettings = appSettings,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )
                                    Presenting.WEBSITE ->
                                        WebsitePresenter(
                                    url = websiteUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                    onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                    onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                    audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
                                )
                                    Presenting.NONE -> { /* nothing */ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
