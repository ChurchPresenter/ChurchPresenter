package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusTarget
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.components.Toolbar
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.PresentationTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.StreamingTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    bibleViewModel: BibleViewModel,
    songsViewModel: SongsViewModel,
    scheduleViewModel: ScheduleViewModel,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel,
    presenting: (Presenting) -> Unit,
    onVerseSelected: (List<SelectedVerse>) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit,
    onTabChange: (Int) -> Unit = {},
    onScheduleItemSelected: (String?) -> Unit = {},
    onShowSettings: () -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    // Tab-specific ViewModels — owned here, not in main.kt
    val picturesViewModel = remember { PicturesViewModel(appSettings) }
    val presentationViewModel = remember { PresentationViewModel(appSettings) }

    val strSaveScheduleAs = stringResource(Res.string.file_chooser_save_schedule)
    val strOpenSchedule   = stringResource(Res.string.file_chooser_open_schedule)
    val strFileFilter     = stringResource(Res.string.file_filter_schedule)

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }

    // Notify parent when tab changes
    LaunchedEffect(selectedTabIndex) {
        onTabChange(selectedTabIndex)
    }

    // Notify parent when schedule item selection changes
    LaunchedEffect(selectedScheduleItemId) {
        onScheduleItemSelected(selectedScheduleItemId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Escape -> {
                            mediaViewModel.pause()
                            presenterManager.setPresentingMode(Presenting.NONE)
                            true
                        }
                        Key.F6 -> {
                            selectedTabIndex = Tabs.BIBLE.ordinal
                            true
                        }
                        Key.F7 -> {
                            selectedTabIndex = Tabs.SONGS.ordinal
                            true
                        }
                        Key.F8 -> {
                            selectedTabIndex = Tabs.PICTURES.ordinal
                            true
                        }
                        Key.F9 -> {
                            selectedTabIndex = Tabs.PRESENTATION.ordinal
                            true
                        }
                        Key.F10 -> {
                            selectedTabIndex = Tabs.MEDIA.ordinal
                            true
                        }
                        Key.F11 -> {
                            selectedTabIndex = Tabs.STREAMING.ordinal
                            true
                        }
                        Key.F12 -> {
                            selectedTabIndex = Tabs.ANNOUNCEMENTS.ordinal
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusTarget()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Toolbar(
                currentTheme = theme,
                onThemeChange = onThemeChange,
                onNewSchedule = {
                    scheduleViewModel.newSchedule()
                },
                onOpenSchedule = {
                    scheduleViewModel.loadSchedule(strOpenSchedule, strFileFilter)
                },
                onSaveSchedule = {
                    scheduleViewModel.saveSchedule(strSaveScheduleAs, strFileFilter)
                },
                onMoveToTop = {
                    selectedScheduleItemId?.let { scheduleViewModel.moveItemToTop(it) }
                },
                onMoveUp = {
                    selectedScheduleItemId?.let { scheduleViewModel.moveItemUp(it) }
                },
                onMoveDown = {
                    selectedScheduleItemId?.let { scheduleViewModel.moveItemDown(it) }
                },
                onMoveToBottom = {
                    selectedScheduleItemId?.let { scheduleViewModel.moveItemToBottom(it) }
                },
                onAddToSchedule = {
                    // Add current tab selection to schedule
                    when (Tabs.entries[selectedTabIndex]) {
                        Tabs.BIBLE -> {
                            // Add selected Bible verse to schedule
                            val selectedVerses = bibleViewModel.getSelectedVerses()
                            if (selectedVerses.isNotEmpty()) {
                                val verse = selectedVerses[0] // Get primary Bible verse
                                scheduleViewModel.addBibleVerse(
                                    bookName = verse.bookName,
                                    chapter = verse.chapter,
                                    verseNumber = verse.verseNumber,
                                    verseText = verse.verseText
                                )
                            }
                        }
                        Tabs.SONGS -> {
                            // Add selected song to schedule
                            val songs = songsViewModel.songsData.value.getSongs()
                            val selectedIndex = songsViewModel.selectedSongIndex.value

                            if (selectedIndex >= 0 && selectedIndex < songs.size) {
                                val song = songs[selectedIndex]
                                scheduleViewModel.addSong(
                                    songNumber = song.number.toIntOrNull() ?: 0,
                                    title = song.title,
                                    songbook = song.songbook
                                )
                            }
                        }
                        else -> {
                            // Other tabs not implemented yet
                        }
                    }
                },
                onRemoveFromSchedule = {
                    selectedScheduleItemId?.let { scheduleViewModel.removeItem(it) }
                },
                onClearSchedule = {
                    scheduleViewModel.clearSchedule()
                    selectedScheduleItemId = null
                },
                onAddLabel = {
                    showAddLabelDialog = true
                },
                onOpenSettings = onShowSettings
            )

            // Main content
            Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth(0.30f).fillMaxHeight()) {
                ScheduleTab(
                    scheduleViewModel = scheduleViewModel,
                    songsViewModel = songsViewModel,
                    bibleViewModel = bibleViewModel,
                    picturesViewModel = picturesViewModel,
                    presentationViewModel = presentationViewModel,
                    mediaViewModel = mediaViewModel,
                    presenterManager = presenterManager,
                    onSongItemSelected = onSongItemSelected,
                    onVerseSelected = onVerseSelected,
                    onPresenting = presenting,
                    onItemClick = { item ->
                        // For PictureItem, always select (don't toggle) to ensure it loads
                        if (item is ScheduleItem.PictureItem) {
                            selectedScheduleItemId = item.id
                        } else {
                            selectedScheduleItemId = if (selectedScheduleItemId == item.id) null else item.id
                        }

                        when (item) {
                            is ScheduleItem.SongItem -> {
                                selectedTabIndex = 1
                                songsViewModel.selectSongByDetails(
                                    songNumber = item.songNumber,
                                    title = item.title,
                                    songbook = item.songbook
                                )
                            }
                            is ScheduleItem.BibleVerseItem -> {
                                selectedTabIndex = 0
                                bibleViewModel.selectVerseByDetails(
                                    bookName = item.bookName,
                                    chapter = item.chapter,
                                    verseNumber = item.verseNumber
                                )
                            }
                            is ScheduleItem.LabelItem -> {
                                editingLabelItem = item
                                showAddLabelDialog = true
                            }
                            is ScheduleItem.PictureItem -> {
                                selectedTabIndex = 2
                            }
                            is ScheduleItem.PresentationItem -> {
                                selectedTabIndex = 3
                                presentationViewModel.loadPresentationByPath(item.filePath)
                            }
                            is ScheduleItem.MediaItem -> {
                                selectedTabIndex = Tabs.MEDIA.ordinal
                                mediaViewModel.loadMediaFromSchedule(
                                    url = item.mediaUrl,
                                    title = item.mediaTitle,
                                    type = item.mediaType
                                )
                            }
                        }
                    },
                    onEditLabel = { labelItem ->
                        editingLabelItem = labelItem
                        showAddLabelDialog = true
                    }
                )
            }

            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                TabSection(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { tabIndex ->
                        selectedTabIndex = tabIndex
                    }
                )

                // Keep MediaTab permanently in composition so VideoPlayer (SwingPanel)
                // is never destroyed on tab switch — just hidden when another tab is active.
                val currentTab = Tabs.entries[selectedTabIndex]

                Box(modifier = Modifier.fillMaxSize()) {
                    // Always-present MediaTab, hidden when not selected
                    MediaTab(
                        modifier = Modifier.fillMaxSize().then(
                            if (currentTab == Tabs.MEDIA) Modifier else Modifier.requiredSize(0.dp)
                        ),
                        viewModel = mediaViewModel,
                        scheduleViewModel = scheduleViewModel,
                        presenterManager = presenterManager
                    )

                    // All other tabs rendered on top when selected
                    when (currentTab) {
                        Tabs.BIBLE -> BibleTab(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = bibleViewModel,
                            scheduleViewModel = scheduleViewModel,
                            onVerseSelected = onVerseSelected,
                            onPresenting = presenting
                        )

                        Tabs.SONGS -> SongsTab(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = songsViewModel,
                            scheduleViewModel = scheduleViewModel,
                            onSongItemSelected = onSongItemSelected,
                            onPresenting = presenting,
                            theme = theme
                        )

                        Tabs.PICTURES -> PicturesTab(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = picturesViewModel,
                            scheduleViewModel = scheduleViewModel,
                            selectedPictureItem = selectedScheduleItemId?.let { id ->
                                scheduleViewModel.scheduleItems.find { it.id == id } as? ScheduleItem.PictureItem
                            },
                            presenterManager = presenterManager
                        )

                        Tabs.PRESENTATION -> PresentationTab(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = presentationViewModel,
                            scheduleViewModel = scheduleViewModel,
                            presenterManager = presenterManager
                        )

                        Tabs.MEDIA -> { /* rendered above, always in composition */ }

                        Tabs.STREAMING -> StreamingTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            onSettingsChange = onSettingsChange
                        )

                        Tabs.ANNOUNCEMENTS -> AnnouncementsTab()
                    }
                }
            }
        }
        }
    }

    // Add/Edit Label Dialog
    if (showAddLabelDialog) {
        AddLabelDialog(
            onDismiss = {
                showAddLabelDialog = false
                editingLabelItem = null
            },
            onConfirm = { text, textColor, backgroundColor ->
                if (editingLabelItem != null) {
                    // Update existing label
                    scheduleViewModel.updateLabel(
                        id = editingLabelItem!!.id,
                        text = text,
                        textColor = textColor,
                        backgroundColor = backgroundColor
                    )
                } else {
                    // Add new label
                    scheduleViewModel.addLabel(text, textColor, backgroundColor)
                }
            },
            existingText = editingLabelItem?.text ?: "",
            existingTextColor = editingLabelItem?.textColor ?: "#FFFFFF",
            existingBackgroundColor = editingLabelItem?.backgroundColor ?: "#2196F3",
            isEdit = editingLabelItem != null
        )
    }
}