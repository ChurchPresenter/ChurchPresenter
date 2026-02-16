package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    // Business logic layer
    val settingsManager = SettingsManager()
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
    val presenterManager = remember { PresenterManager() }

    // Create BibleViewModel with settings (no fallback Bible needed)
    val bibleViewModel = remember(appSettings) {
        BibleViewModel(appSettings)
    }

    // Create SongsViewModel with settings
    val songsViewModel = remember(appSettings) {
        SongsViewModel(appSettings)
    }

    // UI state
    var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null
    val state = rememberWindowState(
        placement = WindowPlacement.Maximized
    )

    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
    val selectedVerses by presenterManager.selectedVerses
    val lyricSection by presenterManager.lyricSection

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = state
    ) {
        AppThemeWrapper(theme = theme) {

            NavigationTopBar(
                onAbout = { presenterManager.setShowPresenterWindow(true) },
                theme = {
                    appSettings = appSettings.copy(theme = it.toString())
                    theme = it
                    settingsManager.saveSettings(appSettings)
                },
                onSettings = {
                    showOptionsDialog = true
                },
                onExit = { exitApplication() },
            )
            MainDesktop(
                onVerseSelected = { verses ->
                    presenterManager.setSelectedVerses(verses)
                },
                onSongItemSelected = {
                    presenterManager.setLyricSection(it)
                },
                appSettings = appSettings,
                bibleViewModel = bibleViewModel,
                songsViewModel = songsViewModel,
                presenting = { presenterManager.setPresentingMode(it) }
            )

            // Options Dialog
            OptionsDialog(
                isVisible = showOptionsDialog,
                theme = theme,
                settingsManager = settingsManager,
                onDismiss = { showOptionsDialog = false },
                onSave = {
                    appSettings = it
                    // Reload ViewModels with new settings
                    bibleViewModel.loadBibles()
                    songsViewModel.loadSongs()
                }
            )
        }
    }

    if (showPresenterWindow) {
        val windowState = remember {
            if (secondScreenBounds != null) {
                WindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition(
                        secondScreenBounds.x.dp, secondScreenBounds.y.dp
                    )
                )
            } else {
                //WindowState(placement = WindowPlacement.Fullscreen)
                WindowState(placement = WindowPlacement.Floating)
            }
        }
        Window(
            title = "Presenter View",
            onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
            state = windowState,
        ) {
            PresenterScreen(modifier = Modifier.fillMaxSize()) {
                Column {
                    if (presentingMode == Presenting.BIBLE) {
                        BiblePresenter(selectedVerses = selectedVerses)
                    } else if (presentingMode == Presenting.LYRICS) {
                        SongPresenter(lyricSection = lyricSection, appSettings = appSettings)
                    }
                }
            }
        }
    }
}