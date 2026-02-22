package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

fun main() {
    // Pre-warm JavaFX on a background thread before UI starts
    preWarmJavaFX()

    application {
    // Business logic layer
    val settingsManager = remember { SettingsManager() }
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
    val presenterManager = remember { PresenterManager() }

    // Load saved language and set locale
    var currentLanguage by remember {
        val savedLanguageCode = appSettings.language
        val language = Language.entries.find { it.code == savedLanguageCode } ?: Language.ENGLISH
        Locale.setDefault(Locale.forLanguageTag(language.code))
        mutableStateOf(language)
    }


    // Schedule actions are registered by MainDesktop — no ScheduleViewModel reference held here
    var scheduleActions by remember { mutableStateOf(ScheduleActions()) }

    // MediaViewModel is owned by MediaTab; we hold a reference here only for MediaPresenter
    var mediaViewModel by remember { mutableStateOf<MediaViewModel?>(null) }


    // UI state — restore saved theme so toolbar/icons have the correct colors on first frame
    var theme by remember {
        val savedTheme = when (appSettings.theme.uppercase()) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        mutableStateOf(savedTheme)
    }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val state = rememberWindowState(
        placement = WindowPlacement.Maximized
    )

    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
    val selectedVerses by presenterManager.selectedVerses
    val lyricSection by presenterManager.lyricSection
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration


    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = state
    ) {
        LanguageProvider(language = currentLanguage) {
            AppThemeWrapper(theme = theme) {


                NavigationTopBar(
                    onAbout = { presenterManager.setShowPresenterWindow(true) },
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
                    onSettings = {
                        showOptionsDialog = true
                    },
                    onExit = { exitApplication() },
                    onAddToSchedule = {
                        // Handled by MainDesktop via BibleTab/SongsTab callbacks
                    },
                    onNewSchedule = { scheduleActions.newSchedule() },
                    onOpenSchedule = { scheduleActions.openSchedule() },
                    onSaveSchedule = { scheduleActions.saveSchedule() },
                    onSaveScheduleAs = { scheduleActions.saveScheduleAs() },
                    onCloseSchedule = { scheduleActions.newSchedule() },
                    onRemoveFromSchedule = {
                        selectedScheduleItemId?.let { scheduleActions.removeSelected(); selectedScheduleItemId = null }
                    },
                    onClearSchedule = {
                        scheduleActions.clearSchedule()
                        selectedScheduleItemId = null
                    },
                )
                MainDesktop(
                    onVerseSelected = { verses -> presenterManager.setSelectedVerses(verses) },
                    onSongItemSelected = { presenterManager.setLyricSection(it) },
                    appSettings = appSettings,
                    presenterManager = presenterManager,
                    onMediaViewModelReady = { mediaViewModel = it },
                    onScheduleActionsReady = { scheduleActions = it },
                    presenting = { presenterManager.setPresentingMode(it) },
                    onScheduleItemSelected = { itemId ->
                        selectedScheduleItemId = itemId
                    },
                    onShowSettings = {
                        showOptionsDialog = true
                    },
                    onThemeChange = { newTheme ->
                        appSettings = appSettings.copy(theme = newTheme.toString())
                        theme = newTheme
                        settingsManager.saveSettings(appSettings)
                    },
                    onSettingsChange = { updateFn ->
                        appSettings = updateFn(appSettings)
                        settingsManager.saveSettings(appSettings)
                    },
                    theme = theme
                )

                // Options Dialog
                OptionsDialog(
                    isVisible = showOptionsDialog,
                    theme = theme,
                    settingsManager = settingsManager,
                    onDismiss = { showOptionsDialog = false },
                    onSave = { appSettings = it }
                )
            }
        }
    }
    val windowCount = appSettings.projectionSettings.numberOfWindows
    for (i in 0 until windowCount) {
        if (showPresenterWindow) {
            val windowState = remember(i) {
                val targetScreenIndex = i + 1

                if (targetScreenIndex < screens.size) {
                    val screenBounds = screens[targetScreenIndex].defaultConfiguration.bounds
                    WindowState(
                        placement = WindowPlacement.Floating,
                        position = WindowPosition(
                            (screenBounds.x + appSettings.projectionSettings.windowLeft).dp,
                            (screenBounds.y + appSettings.projectionSettings.windowTop).dp
                        ),
                        width = (screenBounds.width - appSettings.projectionSettings.windowLeft - appSettings.projectionSettings.windowRight).dp,
                        height = (screenBounds.height - appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom).dp
                    )
                } else {
                    WindowState(placement = WindowPlacement.Fullscreen)
                }
            }
            Window(
                title = "Presenter View ${i + 1}",
                onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                state = windowState,
            ) {
                PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings) {
                    // MediaPresenter must stay permanently in composition so its
                    // VideoPlayer (SwingPanel/JFXPanel) is never torn down.
                    // All other presenters sit on top in a Box.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    mediaViewModel?.pause()
                                    presenterManager.setPresentingMode(Presenting.NONE)
                                    true
                                } else false
                            }
                    ) {
                        // Always present, hidden when another mode is active
                        MediaPresenter(
                            viewModel = mediaViewModel ?: return@Box,
                            isVisible = presentingMode == Presenting.MEDIA,
                            modifier = Modifier.fillMaxSize().then(
                                if (presentingMode == Presenting.MEDIA) Modifier
                                else Modifier.requiredSize(0.dp)
                            )
                        )

                        when (presentingMode) {
                            Presenting.BIBLE -> BiblePresenter(
                                selectedVerses = selectedVerses,
                                appSettings = appSettings
                            )
                            Presenting.LYRICS -> SongPresenter(
                                lyricSection = lyricSection,
                                appSettings = appSettings
                            )
                            Presenting.PICTURES -> PicturePresenter(
                                imagePath = selectedImagePath,
                                animationType = animationType,
                                transitionDuration = transitionDuration
                            )
                            Presenting.PRESENTATION -> SlidePresenter(
                                slide = selectedSlide,
                                animationType = animationType,
                                transitionDuration = transitionDuration
                            )
                            Presenting.MEDIA -> { /* rendered above, always in composition */ }
                            Presenting.NONE -> { /* nothing */ }
                        }
                    }
                }
            }
        }
    }
    } // end application
}