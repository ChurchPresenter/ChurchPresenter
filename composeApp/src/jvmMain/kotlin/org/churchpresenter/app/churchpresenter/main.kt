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

    application {
        // Business logic layer
        val settingsManager = remember { SettingsManager() }
        var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
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
            // Auto-start server if user previously enabled it
            if (appSettings.serverSettings.enabled) {
                companionServer.start(appSettings.serverSettings.port)
            }
        }

        val screens = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices }
        val state = rememberWindowState(placement = WindowPlacement.Maximized)


        if (licenseAccepted) {
            Window(
                onCloseRequest = ::exitApplication,
                title = stringResource(Res.string.app_name),
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
        val targetScreenIndex = i + 1

        val windowState = remember(i) {
            val b = screens[targetScreenIndex].defaultConfiguration.bounds
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(b.x.dp, b.y.dp),
                width = b.width.dp,
                height = b.height.dp
            )
        }

        val screenAssignment = when (i) {
            0 -> proj.screen1Assignment
            1 -> proj.screen2Assignment
            2 -> proj.screen3Assignment
            else -> proj.screen4Assignment
        }

        Window(
            visible = showPresenterWindow,
            title = "Presenter View ${i + 1}",
            onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
            state = windowState,
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings) {
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
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD
                                    )
                                }
                            Presenting.LYRICS ->
                                if (screenAssignment.showSongs) {
                                    SongPresenter(
                                        lyricSection = lyricSection,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD
                                    )
                                }
                            Presenting.PICTURES ->
                                if (screenAssignment.showPictures)
                                    PicturePresenter(imagePath = selectedImagePath, animationType = animationType, transitionDuration = transitionDuration)
                            Presenting.PRESENTATION ->
                                if (screenAssignment.showPictures)
                                    SlidePresenter(slide = selectedSlide, animationType = animationType, transitionDuration = transitionDuration)
                            Presenting.MEDIA ->
                                if (screenAssignment.showMedia)
                                    MediaPresenter(modifier = Modifier.fillMaxSize())
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
                                        appSettings = appSettings
                                    )
                            Presenting.WEBSITE ->
                                WebsitePresenter(url = websiteUrl, modifier = Modifier.fillMaxSize())
                            Presenting.NONE -> { /* nothing */ }
                        }

                        if (identifyingScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Screen ${i + 1}",
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
    }
}
