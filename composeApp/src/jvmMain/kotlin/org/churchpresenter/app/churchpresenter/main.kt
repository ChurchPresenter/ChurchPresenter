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
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.ui.theme.setTheme
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    var openBlackWindow by remember { mutableStateOf(true) }
    var selectedVerse by remember { mutableStateOf(SelectedVerse()) }
    var presenting by remember { mutableStateOf(Presenting.NONE) }
    var lyricSection by remember { mutableStateOf(LyricSection()) }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null
    val state = rememberWindowState(
        placement = WindowPlacement.Maximized
    )
    val settingsManager = SettingsManager()
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
    var theme by remember { mutableStateOf(appSettings.theme) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = state
    ) {
        AppThemeWrapper {
            setTheme(theme)

            NavigationTopBar(
                onAbout = { openBlackWindow = true },
                theme = {
                    appSettings = appSettings.copy(theme = it)
                    theme = it
                    settingsManager.saveSettings(appSettings)
                },
                onSettings = {
                    showOptionsDialog = true
                },
                onExit = { exitApplication() },
            )
            MainDesktop(
                onVerseSelected = {
                    selectedVerse = it
                },
                onSongItemSelected = {
                    lyricSection = it
                },
                appSettings = appSettings,
                presenting = { presenting = it }
            )

            // Options Dialog
            OptionsDialog(
                isVisible = showOptionsDialog,
                theme = theme,
                settingsManager = settingsManager,
                onDismiss = { showOptionsDialog = false },
                onSave = {
                    appSettings = it
                    theme = it.theme
                }
            )
        }
    }

    if (openBlackWindow) {
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
            onCloseRequest = { openBlackWindow = false },
            state = windowState,
        ) {
            PresenterScreen(modifier = Modifier.fillMaxSize()) {
                Column {
                    if (presenting == Presenting.BIBLE) {
                        BiblePresenter(selectedVerse = selectedVerse)
                    } else if (presenting == Presenting.LYRICS) {
                        SongPresenter(lyricSection = lyricSection, appSettings = appSettings)
                    }
                }
            }
        }
    }
}