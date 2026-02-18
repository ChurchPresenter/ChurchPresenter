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
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
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

    // Create ScheduleViewModel
    val scheduleViewModel = remember { ScheduleViewModel() }

    // UI state
    var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // Track which tab is selected
    var selectedScheduleItemId by remember { mutableStateOf<String?>(null) } // Track selected schedule item

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
                onAddToSchedule = {
                    // Add currently selected song or verse to schedule
                    when (currentTab) {
                        0 -> { // Bible tab
                            // Use getSelectedVerses which already has the right logic
                            val verses = bibleViewModel.getSelectedVerses()
                            verses.firstOrNull()?.let { verse ->
                                scheduleViewModel.addBibleVerse(
                                    bookName = verse.bookName,
                                    chapter = verse.chapter,
                                    verseNumber = verse.verseNumber,
                                    verseText = verse.verseText
                                )
                            }
                        }
                        1 -> { // Songs tab
                            // Access the filtered songs and get the selected song
                            val selectedIndex = songsViewModel.selectedSongIndex.value
                            val filteredSongs = songsViewModel.filteredSongs.value
                            val allSongs = songsViewModel.songsData.value.getSongs()

                            if (selectedIndex >= 0 && selectedIndex < filteredSongs.size) {
                                // Map back to Song objects
                                val songText = filteredSongs[selectedIndex]
                                val song = allSongs.find { "${it.number}. ${it.title}" == songText }

                                song?.let {
                                    scheduleViewModel.addSong(
                                        songNumber = it.number.toIntOrNull() ?: 0,
                                        title = it.title,
                                        songbook = it.songbook
                                    )
                                }
                            }
                        }
                    }
                },
                onRemoveFromSchedule = {
                    // Remove selected item from schedule
                    selectedScheduleItemId?.let { id ->
                        scheduleViewModel.removeItem(id)
                        selectedScheduleItemId = null
                    }
                },
                onClearSchedule = {
                    // Clear all schedule items
                    scheduleViewModel.clearSchedule()
                    selectedScheduleItemId = null
                },
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
                scheduleViewModel = scheduleViewModel,
                presenting = { presenterManager.setPresentingMode(it) },
                onTabChange = { tabIndex ->
                    currentTab = tabIndex
                },
                onScheduleItemSelected = { itemId ->
                    selectedScheduleItemId = itemId
                },
                theme = theme
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
                        BiblePresenter(selectedVerses = selectedVerses, appSettings = appSettings)
                    } else if (presentingMode == Presenting.LYRICS) {
                        SongPresenter(lyricSection = lyricSection, appSettings = appSettings)
                    }
                }
            }
        }
    }
}