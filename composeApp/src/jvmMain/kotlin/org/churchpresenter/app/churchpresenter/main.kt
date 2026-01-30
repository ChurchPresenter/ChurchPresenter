package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ProvideThemeManager
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeManager
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import org.jetbrains.compose.resources.stringResource


fun main() = application {
    var openBlackWindow by remember { mutableStateOf(false) }
    var selectedVerse by remember { mutableStateOf(SelectedVerse()) }
    //var lyricSection by remember { mutableStateOf<LyricSection>("") }

    // Theme management
    val themeManager = remember { ThemeManager() }

    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null
    val state = rememberWindowState(
        placement = WindowPlacement.Maximized
    )
    var showPicker by remember { mutableStateOf(false) }

    ProvideThemeManager(themeManager = themeManager) {
        val currentTheme by themeManager.themeMode
        val isDarkTheme = when (currentTheme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

        ChurchPresenterTheme(darkTheme = isDarkTheme) {
            Window(
                onCloseRequest = ::exitApplication,
                title = stringResource(Res.string.app_name),
                state = state
            ) {
                NavigationTopBar(
                    onAbout = { openBlackWindow = true },
                    onExit = { exitApplication() },
                )
                MainDesktop(
                    onVerseSelected = {
                        selectedVerse = it
                    },
                    onSongItemSelected = { lyrics ->
                       // lyricSection = it
                    }
                )
            }
        }
    }

    if (showPicker) {
        showSwingFontChooser()
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
                WindowState(placement = WindowPlacement.Fullscreen)
            }
        }
        Window(
            onCloseRequest = { openBlackWindow = false },
            state = windowState,
        ) {
            PresenterScreen(modifier = Modifier.fillMaxSize()) {
                Column {
                    if (selectedVerse.bookName.isNotEmpty()) {
                        BiblePresenter(selectedVerse)
                    }
                }
            }
        }
    }
}