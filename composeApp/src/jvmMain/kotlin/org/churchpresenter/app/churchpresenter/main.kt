package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
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

        // Load saved language and set locale
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

        // Queried once — screen layout doesn't change at runtime.
        val screens = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices }
        val state = rememberWindowState(
            placement = WindowPlacement.Maximized
        )

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
                            onSave = { appSettings = it }
                        )
                    } // end CompositionLocalProvider
                }
            }
        }

        PresenterWindows(
            appSettings = appSettings,
            presenterManager = presenterManager,
            mediaViewModel = mediaViewModel,
            screens = screens,
        )
    } // end application
}

/**
 * Renders the presenter output windows (up to 3).
 *
 * The Window composable is always kept in composition once showPresenterWindow
 * becomes true — it is never removed and re-added (which would recreate the native
 * window and block the EDT). Instead, the native window starts AWT-invisible and
 * is moved+shown via a single SwingUtilities.invokeLater call posted 500 ms later,
 * after the main window's layout work has fully drained from the EDT queue.
 */
@androidx.compose.runtime.Composable
private fun PresenterWindows(
    appSettings: org.churchpresenter.app.churchpresenter.data.AppSettings,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel,
    screens: Array<java.awt.GraphicsDevice>,
) {
    val proj          by derivedStateOf { appSettings.projectionSettings }
    val windowCount   by derivedStateOf { appSettings.projectionSettings.numberOfWindows }

    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode      by presenterManager.presentingMode
    val selectedVerses      by presenterManager.selectedVerses
    val lyricSection        by presenterManager.lyricSection
    val selectedImagePath   by presenterManager.selectedImagePath
    val selectedSlide       by presenterManager.selectedSlide
    val animationType       by presenterManager.animationType
    val transitionDuration  by presenterManager.transitionDuration

    val currentAppSettings by rememberUpdatedState(appSettings)

    for (i in 0 until 3) {
        if (i >= windowCount) continue

        // Stable WindowState — created once, never recreated.
        // We intentionally start with a tiny off-screen size; the real
        // bounds are applied by AWT after the delay below.
        val windowState = remember(i) {
            WindowState(
                placement = WindowPlacement.Floating,
                position  = WindowPosition((-9999).dp, (-9999).dp),
                width     = 1.dp,
                height    = 1.dp
            )
        }

        if (showPresenterWindow) {
            Window(
                title          = "Presenter View ${i + 1}",
                onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                state          = windowState,
                visible        = false,   // AWT-invisible; we show it manually below
            ) {
                // Grab the underlying ComposeWindow handle.
                val win: ComposeWindow = this.window
                val projSnapshot by rememberUpdatedState(proj)
                val screenIndex = i + 1

                // After 500ms, do a single EDT post that sets bounds and shows the window.
                // Using LaunchedEffect (coroutine) means the delay runs on the compose
                // coroutine dispatcher, not the EDT — so the EDT is never blocked.
                LaunchedEffect(i, proj.windowLeft, proj.windowRight, proj.windowTop, proj.windowBottom) {
                    delay(500)
                    SwingUtilities.invokeLater {
                        if (screenIndex < screens.size) {
                            val b = screens[screenIndex].defaultConfiguration.bounds
                            win.setLocation(b.x + projSnapshot.windowLeft, b.y + projSnapshot.windowTop)
                            win.setSize(
                                b.width  - projSnapshot.windowLeft - projSnapshot.windowRight,
                                b.height - projSnapshot.windowTop  - projSnapshot.windowBottom
                            )
                        } else {
                            val b = screens[0].defaultConfiguration.bounds
                            win.setLocation(b.x, b.y)
                            win.setSize(b.width, b.height)
                        }
                        win.isVisible = true
                    }
                }

                // Hide the native window when the composable leaves composition.
                DisposableEffect(Unit) {
                    onDispose { SwingUtilities.invokeLater { win.isVisible = false } }
                }

                CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                    PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = currentAppSettings) {
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
                                Presenting.BIBLE        -> BiblePresenter(selectedVerses = selectedVerses, appSettings = currentAppSettings)
                                Presenting.LYRICS       -> SongPresenter(lyricSection = lyricSection, appSettings = currentAppSettings)
                                Presenting.PICTURES     -> PicturePresenter(imagePath = selectedImagePath, animationType = animationType, transitionDuration = transitionDuration)
                                Presenting.PRESENTATION -> SlidePresenter(slide = selectedSlide, animationType = animationType, transitionDuration = transitionDuration)
                                Presenting.MEDIA        -> MediaPresenter(modifier = Modifier.fillMaxSize())
                                Presenting.NONE         -> { /* blank */ }
                            }
                        }
                    }
                }
            }
        }
    }
}