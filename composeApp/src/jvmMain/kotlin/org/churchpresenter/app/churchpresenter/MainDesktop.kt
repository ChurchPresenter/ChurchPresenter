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
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.churchpresenter.app.churchpresenter.components.Toolbar
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.PresentationTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTabActions
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.StreamingTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

// Kept for NavigationTopBar / menu — wraps ScheduleTabActions
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
    onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    // ScheduleViewModel lives inside ScheduleTab — MainDesktop drives it via callbacks
    var scheduleActions by remember { mutableStateOf(ScheduleTabActions()) }

    // Selected schedule items — passed directly into each tab; tab reacts via LaunchedEffect
    var selectedBibleVerseItem by remember { mutableStateOf<ScheduleItem.BibleVerseItem?>(null) }
    var selectedSongItem       by remember { mutableStateOf<ScheduleItem.SongItem?>(null) }
    var selectedPictureItem    by remember { mutableStateOf<ScheduleItem.PictureItem?>(null) }
    var selectedPresentationItem by remember { mutableStateOf<ScheduleItem.PresentationItem?>(null) }
    var selectedMediaItem      by remember { mutableStateOf<ScheduleItem.MediaItem?>(null) }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem   by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }

    LaunchedEffect(selectedTabIndex) { onTabChange(selectedTabIndex) }

    // Forward ScheduleTabActions to parent (NavigationTopBar / menu) as ScheduleActions
    LaunchedEffect(scheduleActions) {
        onScheduleActionsReady(
            ScheduleActions(
                newSchedule    = scheduleActions.newSchedule,
                openSchedule   = scheduleActions.openSchedule,
                saveSchedule   = scheduleActions.saveSchedule,
                saveScheduleAs = scheduleActions.saveScheduleAs,
                removeSelected = scheduleActions.removeSelected,
                clearSchedule  = scheduleActions.clearSchedule
            )
        )
    }

    val mediaViewModel = LocalMediaViewModel.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Escape -> { mediaViewModel?.pause(); presenterManager.setPresentingMode(Presenting.NONE); true }
                        Key.F6  -> { selectedTabIndex = Tabs.BIBLE.ordinal;          true }
                        Key.F7  -> { selectedTabIndex = Tabs.SONGS.ordinal;          true }
                        Key.F8  -> { selectedTabIndex = Tabs.PICTURES.ordinal;       true }
                        Key.F9  -> { selectedTabIndex = Tabs.PRESENTATION.ordinal;   true }
                        Key.F10 -> { selectedTabIndex = Tabs.MEDIA.ordinal;          true }
                        Key.F11 -> { selectedTabIndex = Tabs.STREAMING.ordinal;      true }
                        Key.F12 -> { selectedTabIndex = Tabs.ANNOUNCEMENTS.ordinal;  true }
                        else -> false
                    }
                } else false
            }
            .focusTarget()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                currentTheme = theme,
                onThemeChange = onThemeChange,
                onNewSchedule    = { scheduleActions.newSchedule() },
                onOpenSchedule   = { scheduleActions.openSchedule() },
                onSaveSchedule   = { scheduleActions.saveSchedule() },
                onMoveToTop      = { scheduleActions.moveSelectedToTop() },
                onMoveUp         = { scheduleActions.moveSelectedUp() },
                onMoveDown       = { scheduleActions.moveSelectedDown() },
                onMoveToBottom   = { scheduleActions.moveSelectedToBottom() },
                onAddToSchedule  = { /* handled per-tab via onAddToSchedule callbacks */ },
                onRemoveFromSchedule = { scheduleActions.removeSelected() },
                onClearSchedule  = { scheduleActions.clearSchedule() },
                onAddLabel       = { showAddLabelDialog = true },
                onOpenSettings   = onShowSettings
            )

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth(0.30f).fillMaxHeight()) {
                    ScheduleTab(
                        onPresenting = presenting,
                        onPresentBible = { item ->
                            selectedBibleVerseItem = item
                            presenting(Presenting.BIBLE)
                        },
                        onPresentSong = { item ->
                            selectedSongItem = item
                            onSongItemSelected(
                                LyricSection(
                                    title = item.title,
                                    songNumber = item.songNumber,
                                    lines = emptyList(),
                                    type = Constants.SECTION_TYPE_SONG
                                )
                            )
                            presenting(Presenting.LYRICS)
                        },
                        onPresentPresentation = { item ->
                            selectedPresentationItem = item
                            presenting(Presenting.PRESENTATION)
                        },
                        onPresentPictures = { item -> selectedPictureItem = item },
                        onPresentMedia = { item ->
                            selectedMediaItem = item
                            presenting(Presenting.MEDIA)
                        },
                        onItemClick = { item ->
                            when (item) {
                                is ScheduleItem.SongItem -> {
                                    selectedTabIndex = Tabs.SONGS.ordinal
                                    selectedSongItem = item
                                }
                                is ScheduleItem.BibleVerseItem -> {
                                    selectedTabIndex = Tabs.BIBLE.ordinal
                                    selectedBibleVerseItem = item
                                }
                                is ScheduleItem.LabelItem -> {
                                    editingLabelItem = item
                                    showAddLabelDialog = true
                                }
                                is ScheduleItem.PictureItem -> {
                                    selectedTabIndex = Tabs.PICTURES.ordinal
                                    selectedPictureItem = item
                                }
                                is ScheduleItem.PresentationItem -> {
                                    selectedTabIndex = Tabs.PRESENTATION.ordinal
                                    selectedPresentationItem = item
                                }
                                is ScheduleItem.MediaItem -> {
                                    selectedTabIndex = Tabs.MEDIA.ordinal
                                    selectedMediaItem = item
                                }
                            }
                        },
                        onEditLabel = { labelItem ->
                            editingLabelItem = labelItem
                            showAddLabelDialog = true
                        },
                        onActionsReady = { actions ->
                            scheduleActions = actions
                        },
                        onSelectedItemChanged = { id ->
                            onScheduleItemSelected(id)
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    TabSection(
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it }
                    )

                    val currentTab = Tabs.entries[selectedTabIndex]

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            Tabs.BIBLE -> BibleTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { bookName, chapter, verseNumber, verseText ->
                                    scheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText)
                                },
                                selectedVerseItem = selectedBibleVerseItem,
                                onVerseSelected = onVerseSelected,
                                onPresenting = presenting
                            )
                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { songNumber, title, songbook ->
                                    scheduleActions.addSong(songNumber, title, songbook)
                                },
                                selectedSongItem = selectedSongItem,
                                onSongItemSelected = onSongItemSelected,
                                onPresenting = presenting,
                                theme = theme
                            )
                            Tabs.PICTURES -> PicturesTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { folderPath, folderName, imageCount ->
                                    scheduleActions.addPicture(folderPath, folderName, imageCount)
                                },
                                selectedPictureItem = selectedPictureItem,
                                presenterManager = presenterManager
                            )
                            Tabs.PRESENTATION -> PresentationTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { filePath, fileName, slideCount, fileType ->
                                    scheduleActions.addPresentation(filePath, fileName, slideCount, fileType)
                                },
                                selectedPresentationItem = selectedPresentationItem,
                                presenterManager = presenterManager
                            )
                            Tabs.MEDIA -> MediaTab(
                                modifier = Modifier.fillMaxSize(),
                                onAddToSchedule = { mediaUrl, mediaTitle, mediaType ->
                                    scheduleActions.addMedia(mediaUrl, mediaTitle, mediaType)
                                },
                                selectedMediaItem = selectedMediaItem,
                                presenterManager = presenterManager
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

    if (showAddLabelDialog) {
        AddLabelDialog(
            onDismiss = {
                showAddLabelDialog = false
                editingLabelItem = null
            },
            onConfirm = { text, textColor, backgroundColor ->
                if (editingLabelItem != null) {
                    scheduleActions.updateLabel(editingLabelItem!!.id, text, textColor, backgroundColor)
                } else {
                    scheduleActions.addLabel(text, textColor, backgroundColor)
                }
                showAddLabelDialog = false
                editingLabelItem = null
            },
            existingText = editingLabelItem?.text ?: "",
            existingTextColor = editingLabelItem?.textColor ?: "#FFFFFF",
            existingBackgroundColor = editingLabelItem?.backgroundColor ?: "#2196F3",
            isEdit = editingLabelItem != null
        )
    }
}