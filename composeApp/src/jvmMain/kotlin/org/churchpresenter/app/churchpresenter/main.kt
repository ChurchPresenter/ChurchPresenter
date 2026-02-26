package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
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

        // Show GPL3 license on first run — block main window until accepted.
        // All remember blocks are always evaluated to avoid state loss on recomposition.
        var licenseAccepted by remember { mutableStateOf(appSettings.licenseAccepted) }

        // Load saved language and set locale — always initialised so state survives
        // the licenseAccepted transition without being reset.
        var currentLanguage by remember {
            val savedLanguageCode = appSettings.language
            val language = Language.entries.find { it.code == savedLanguageCode } ?: Language.ENGLISH
            Locale.setDefault(Locale.forLanguageTag(language.code))
            mutableStateOf(language)
        }


        // Schedule actions are registered by MainDesktop — no ScheduleViewModel reference held here.
        // rememberUpdatedState ensures NavigationTopBar lambdas always read the latest actions
        // without being recreated on every scheduleActions update.
        var scheduleActions by remember { mutableStateOf(ScheduleActions()) }
        val currentScheduleActions by rememberUpdatedState(scheduleActions)

        // MediaViewModel lives at application scope — shared between MediaTab (controls)
        // and the presenter Window via LocalMediaViewModel CompositionLocal.
        // Documented legitimate exception: cross-Window JFX/Swing bridge.
        val mediaViewModel = remember { MediaViewModel() }

        // false = not identifying; true = all screens showing their number for 5 seconds
        var identifyingScreen by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

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
        var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
        var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

        // Queried once — screen layout doesn't change at runtime.
        val screens = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices }
        val state = rememberWindowState(
            placement = WindowPlacement.Maximized
        )

        val showPresenterWindow by presenterManager.showPresenterWindow
        val presentingMode by presenterManager.presentingMode
        val selectedVerses by presenterManager.selectedVerses
        val lyricSection by presenterManager.lyricSection
        val lyricSectionVersion by presenterManager.lyricSectionVersion
        val selectedImagePath by presenterManager.selectedImagePath
        val selectedSlide by presenterManager.selectedSlide
        val animationType by presenterManager.animationType
        val transitionDuration by presenterManager.transitionDuration
        val lottieJsonContent by presenterManager.lottieJsonContent
        val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
        val lottiePauseFrame by presenterManager.lottiePauseFrame
        val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
        val lottieTrigger by presenterManager.lottieTrigger

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
                            onSettings = {
                                showOptionsDialog = true
                            },
                            onExit = { exitApplication() },
                            onAddToSchedule = {
                                // Handled by MainDesktop via BibleTab/SongsTab callbacks
                            },
                            onNewSchedule = { currentScheduleActions.newSchedule() },
                            onOpenSchedule = { currentScheduleActions.openSchedule() },
                            onSaveSchedule = { currentScheduleActions.saveSchedule() },
                            onSaveScheduleAs = { currentScheduleActions.saveScheduleAs() },
                            onCloseSchedule = { currentScheduleActions.newSchedule() },
                            onRemoveFromSchedule = {
                                selectedScheduleItemId?.let { currentScheduleActions.removeSelected(); selectedScheduleItemId = null }
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
                            presenting = { presenterManager.setPresentingMode(it) },
                            onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
                            onShowSettings = { showOptionsDialog = true },
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
                        OptionsDialog(
                            isVisible = showOptionsDialog,
                            theme = theme,
                            settingsManager = settingsManager,
                            onDismiss = { showOptionsDialog = false },
                            onSave = { updated ->
                                appSettings = updated
                                settingsManager.saveSettings(updated)
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
                    } // end CompositionLocalProvider
                }
            }
        }

        // Presenter windows — always composed (never destroyed) so the native HWND
        // is created once and stays alive. Visibility is controlled via the visible=
        // parameter — toggling it just hides/shows the existing window rather than
        // destroying and recreating the native window, which is expensive on Windows.
        // Always derive window count from physically detected screens (screens[0] is the app screen).
        // 2 screens → 1 presenter window, 3 screens → 2 presenter windows, etc.
        val windowCount = (screens.size - 1).coerceIn(1, 4)
        val proj = appSettings.projectionSettings
        for (i in 0 until windowCount) {
            val targetScreenIndex = i + 1
            val windowState = if (targetScreenIndex < screens.size) {
                val screenBounds = screens[targetScreenIndex].defaultConfiguration.bounds
                remember(i, proj.windowLeft, proj.windowRight, proj.windowTop, proj.windowBottom) {
                    WindowState(
                        placement = WindowPlacement.Fullscreen,
                        position = WindowPosition(
                            (screenBounds.x + proj.windowLeft).dp,
                            (screenBounds.y + proj.windowTop).dp
                        ),
                        width  = (screenBounds.width  - proj.windowLeft - proj.windowRight).dp,
                        height = (screenBounds.height - proj.windowTop  - proj.windowBottom).dp
                    )
                }
            } else {
                remember(i) { WindowState(placement = WindowPlacement.Fullscreen) }
            }

            // Resolve the screen assignment for this window index
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
                                        val bibleAnimType = when (appSettings.bibleSettings.animationType) {
                                            Constants.ANIMATION_FADE -> AnimationType.FADE
                                            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                                            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                                            Constants.ANIMATION_NONE -> AnimationType.NONE
                                            else -> AnimationType.CROSSFADE
                                        }
                                        val bibleDuration = appSettings.bibleSettings.transitionDuration.toInt()
                                        when (bibleAnimType) {
                                            AnimationType.CROSSFADE -> Crossfade(targetState = selectedVerses, animationSpec = tween(bibleDuration), label = "BibleCrossfade") { verses ->
                                                BiblePresenter(selectedVerses = verses, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.FADE -> AnimatedContent(targetState = selectedVerses, transitionSpec = { fadeIn(tween(bibleDuration)) togetherWith fadeOut(tween(bibleDuration)) }, label = "BibleFade") { verses ->
                                                BiblePresenter(selectedVerses = verses, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.SLIDE_LEFT -> AnimatedContent(targetState = selectedVerses, transitionSpec = { slideInHorizontally(tween(bibleDuration)) { it } togetherWith slideOutHorizontally(tween(bibleDuration)) { -it } }, label = "BibleSlideLeft") { verses ->
                                                BiblePresenter(selectedVerses = verses, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.SLIDE_RIGHT -> AnimatedContent(targetState = selectedVerses, transitionSpec = { slideInHorizontally(tween(bibleDuration)) { -it } togetherWith slideOutHorizontally(tween(bibleDuration)) { it } }, label = "BibleSlideRight") { verses ->
                                                BiblePresenter(selectedVerses = verses, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.NONE -> BiblePresenter(selectedVerses = selectedVerses, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                        }
                                    }
                                Presenting.LYRICS ->
                                    if (screenAssignment.showSongs) {
                                        val songAnimType = when (appSettings.songSettings.animationType) {
                                            Constants.ANIMATION_FADE -> AnimationType.FADE
                                            Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                                            Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                                            Constants.ANIMATION_NONE -> AnimationType.NONE
                                            else -> AnimationType.CROSSFADE
                                        }
                                        val songDuration = appSettings.songSettings.transitionDuration.toInt()
                                        val songKey = lyricSection to lyricSectionVersion
                                        when (songAnimType) {
                                            AnimationType.CROSSFADE -> Crossfade(targetState = songKey, animationSpec = tween(songDuration), label = "SongCrossfade") { (section, _) ->
                                                SongPresenter(lyricSection = section, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.FADE -> AnimatedContent(targetState = songKey, transitionSpec = { fadeIn(tween(songDuration)) togetherWith fadeOut(tween(songDuration)) }, label = "SongFade") { (section, _) ->
                                                SongPresenter(lyricSection = section, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.SLIDE_LEFT -> AnimatedContent(targetState = songKey, transitionSpec = { slideInHorizontally(tween(songDuration)) { it } togetherWith slideOutHorizontally(tween(songDuration)) { -it } }, label = "SongSlideLeft") { (section, _) ->
                                                SongPresenter(lyricSection = section, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.SLIDE_RIGHT -> AnimatedContent(targetState = songKey, transitionSpec = { slideInHorizontally(tween(songDuration)) { -it } togetherWith slideOutHorizontally(tween(songDuration)) { it } }, label = "SongSlideRight") { (section, _) ->
                                                SongPresenter(lyricSection = section, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                            }
                                            AnimationType.NONE -> SongPresenter(lyricSection = lyricSection, appSettings = appSettings, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD)
                                        }
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
                                Presenting.NONE -> { /* nothing */ }
                            }

                            // Identify overlay — shown for 5 seconds when Identify is pressed
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
        } else {
            // License not yet accepted — show the agreement window and block the rest of the app
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
    } // end application
}