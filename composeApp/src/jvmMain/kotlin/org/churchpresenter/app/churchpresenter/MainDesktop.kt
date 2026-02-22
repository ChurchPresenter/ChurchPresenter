package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import org.jetbrains.compose.resources.stringResource

data class ScheduleActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},
    val clearSchedule: () -> Unit = {}
)

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    presenterManager: PresenterManager,
    presenting: (Presenting) -> Unit,
    onVerseSelected: (List<SelectedVerse>) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit,
    onTabChange: (Int) -> Unit = {},
    onScheduleItemSelected: (String?) -> Unit = {},
    onShowSettings: () -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onMediaViewModelReady: (MediaViewModel) -> Unit = {},
    // Registers schedule actions with the parent (for NavigationTopBar)
    onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    val scheduleViewModel = remember { ScheduleViewModel() }

    var songsViewModel by remember { mutableStateOf<SongsViewModel?>(null) }
    var addBibleVerseToSchedule by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectBibleVerse by remember { mutableStateOf<((String, Int, Int) -> Unit)?>(null) }
    var loadPresentation by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var selectPictureFolder by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var presentPictures by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pauseMedia by remember { mutableStateOf<(() -> Unit)?>(null) }
    var loadMedia by remember { mutableStateOf<((String, String, String) -> Unit)?>(null) }

    val strSaveScheduleAs = stringResource(Res.string.file_chooser_save_schedule)
    val strOpenSchedule   = stringResource(Res.string.file_chooser_open_schedule)
    val strFileFilter     = stringResource(Res.string.file_filter_schedule)

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }

    // Register schedule actions with parent once string resources are ready
    LaunchedEffect(strSaveScheduleAs, strOpenSchedule, strFileFilter) {
        onScheduleActionsReady(ScheduleActions(
            newSchedule = { scheduleViewModel.newSchedule() },
            openSchedule = { scheduleViewModel.loadSchedule(strOpenSchedule, strFileFilter) },
            saveSchedule = { scheduleViewModel.saveSchedule(strSaveScheduleAs, strFileFilter) },
            saveScheduleAs = { scheduleViewModel.saveScheduleAs(strSaveScheduleAs, strFileFilter) },
            removeSelected = { selectedScheduleItemId?.let { scheduleViewModel.removeItem(it) } },
            clearSchedule = { scheduleViewModel.clearSchedule() }
        ))
    }

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
                            pauseMedia?.invoke()
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
                            addBibleVerseToSchedule?.invoke()
                        }
                        Tabs.SONGS -> {
                            songsViewModel?.addCurrentSongToSchedule(scheduleViewModel)
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
                    onPresenting = presenting,
                    onPresentBible = { item ->
                        selectBibleVerse?.invoke(item.bookName, item.chapter, item.verseNumber)
                        presenting(Presenting.BIBLE)
                    },
                    onPresentSong = { songItem ->
                        songsViewModel?.selectSongByDetails(songItem.songNumber, songItem.title, songItem.songbook)
                        songsViewModel?.getSelectedLyricSection()?.let { onSongItemSelected(it) }
                        presenting(Presenting.LYRICS)
                    },
                    onPresentPresentation = { item ->
                        loadPresentation?.invoke(item.filePath)
                        presenting(Presenting.PRESENTATION)
                    },
                    onPresentPictures = { item: ScheduleItem.PictureItem ->
                        presentPictures?.invoke(item.folderPath)
                    },
                    onPresentMedia = { item: ScheduleItem.MediaItem ->
                        loadMedia?.invoke(item.mediaUrl, item.mediaTitle, item.mediaType)
                        presenting(Presenting.MEDIA)
                    },
                    onItemClick = { item ->
                        selectedScheduleItemId = if (item is ScheduleItem.PictureItem) {
                            item.id
                        } else {
                            if (selectedScheduleItemId == item.id) null else item.id
                        }

                        when (item) {
                            is ScheduleItem.SongItem -> {
                                selectedTabIndex = Tabs.SONGS.ordinal
                                songsViewModel?.selectSongByDetails(
                                    songNumber = item.songNumber,
                                    title = item.title,
                                    songbook = item.songbook
                                )
                            }
                            is ScheduleItem.BibleVerseItem -> {
                                selectedTabIndex = Tabs.BIBLE.ordinal
                                selectBibleVerse?.invoke(item.bookName, item.chapter, item.verseNumber)
                            }
                            is ScheduleItem.LabelItem -> {
                                editingLabelItem = item
                                showAddLabelDialog = true
                            }
                            is ScheduleItem.PictureItem -> {
                                selectedTabIndex = Tabs.PICTURES.ordinal
                                selectPictureFolder?.invoke(item.folderPath)
                            }
                            is ScheduleItem.PresentationItem -> {
                                selectedTabIndex = Tabs.PRESENTATION.ordinal
                                loadPresentation?.invoke(item.filePath)
                            }
                            is ScheduleItem.MediaItem -> {
                                selectedTabIndex = Tabs.MEDIA.ordinal
                                loadMedia?.invoke(item.mediaUrl, item.mediaTitle, item.mediaType)
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
                    when (currentTab) {
                        Tabs.BIBLE -> BibleTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            onAddToSchedule = { bookName, chapter, verseNumber, verseText ->
                                scheduleViewModel.addBibleVerse(bookName, chapter, verseNumber, verseText)
                            },
                            onVerseSelected = onVerseSelected,
                            onPresenting = presenting,
                            onAddToScheduleRegistration = { action -> addBibleVerseToSchedule = action },
                            onSelectVerseRequest = { action -> selectBibleVerse = action }
                        )

                        Tabs.SONGS -> SongsTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            onAddToSchedule = {
                                songsViewModel?.addCurrentSongToSchedule(scheduleViewModel)
                            },
                            onSongItemSelected = onSongItemSelected,
                            onPresenting = presenting,
                            onViewModelReady = { songsViewModel = it },
                            theme = theme
                        )

                        Tabs.PICTURES -> PicturesTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            onAddToSchedule = { folderPath, folderName, imageCount ->
                                scheduleViewModel.addPicture(folderPath, folderName, imageCount)
                            },
                            selectedPictureItem = selectedScheduleItemId?.let { id ->
                                scheduleViewModel.scheduleItems.find { it.id == id } as? ScheduleItem.PictureItem
                            },
                            presenterManager = presenterManager,
                            onSelectFolderRequest = { action -> selectPictureFolder = action },
                            onPresentPicturesRequest = { action -> presentPictures = action }
                        )

                        Tabs.PRESENTATION -> PresentationTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            onAddToSchedule = { filePath, fileName, slideCount, fileType ->
                                scheduleViewModel.addPresentation(filePath, fileName, slideCount, fileType)
                            },
                            presenterManager = presenterManager,
                            onLoadPresentationRequest = { action -> loadPresentation = action }
                        )

                        Tabs.MEDIA -> MediaTab(
                            modifier = Modifier.fillMaxSize(),
                            onAddToSchedule = { mediaUrl, mediaTitle, mediaType ->
                                scheduleViewModel.addMedia(mediaUrl, mediaTitle, mediaType)
                            },
                            presenterManager = presenterManager,
                            onViewModelReady = { onMediaViewModelReady(it) },
                            onPauseRequest = { action -> pauseMedia = action },
                            onLoadMediaRequest = { action -> loadMedia = action }
                        )

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