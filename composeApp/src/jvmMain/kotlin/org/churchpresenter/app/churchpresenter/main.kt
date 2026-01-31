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
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.jetbrains.compose.resources.stringResource


fun main() = application {
    var openBlackWindow by remember { mutableStateOf(true) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedVerse by remember { mutableStateOf(SelectedVerse()) }
    var presenting by remember { mutableStateOf(Presenting.NONE) }
    var lyricSection by remember { mutableStateOf(LyricSection()) }


    // Get bounds of the second screen if present
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val secondScreenBounds = if (screens.size > 1) screens[1].defaultConfiguration.bounds else null
    val state = rememberWindowState(
        placement = WindowPlacement.Maximized
    )
    var showPicker by remember { mutableStateOf(false) }

    AppThemeWrapper {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = state
        ) {
            NavigationTopBar(
                onAbout = { openBlackWindow = true },
                onSettings = { showOptionsDialog = true },
                onExit = { exitApplication() },
            )
            MainDesktop(
                onVerseSelected = {
                    selectedVerse = it
                },
                onSongItemSelected = {
                    lyricSection = it
                },
                presenting = { presenting = it }
            )
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
                        SongPresenter(lyricSection = lyricSection)
                    }
                }
            }
        }
    }

    if (showOptionsDialog) {
        // Options Dialog
        OptionsDialog(
            isVisible = true,
            onDismiss = { showOptionsDialog = false },
            onSave = {
                // TODO: Save settings
                showOptionsDialog = false
            }
        )
    }
}