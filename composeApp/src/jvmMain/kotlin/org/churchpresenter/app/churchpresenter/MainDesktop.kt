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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.churchpresenter.app.churchpresenter.components.Toolbar
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.dialogs.AddWebsiteDialog
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
import org.churchpresenter.app.churchpresenter.tabs.LowerThirdTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    companionServer: CompanionServer,
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
    // ScheduleViewModel lives inside ScheduleTab — MainDesktop drives it via callbacks.
    // rememberUpdatedState ensures toolbar lambdas always read the latest actions without
    // needing to be recreated on every scheduleActions update.
    var scheduleActions by remember { mutableStateOf(ScheduleTabActions()) }
    val currentScheduleActions by rememberUpdatedState(scheduleActions)

    // Keep a stable reference to onScheduleActionsReady so the onActionsReady lambda
    // below doesn't capture a stale instance across recompositions.
    val currentOnScheduleActionsReady by rememberUpdatedState(onScheduleActionsReady)
    var selectedBibleVerseItem by remember { mutableStateOf<ScheduleItem.BibleVerseItem?>(null) }
    var selectedSongItem by remember { mutableStateOf<ScheduleItem.SongItem?>(null) }
    var selectedPictureItem by remember { mutableStateOf<ScheduleItem.PictureItem?>(null) }
    var selectedPresentationItem by remember { mutableStateOf<ScheduleItem.PresentationItem?>(null) }
    var selectedMediaItem by remember { mutableStateOf<ScheduleItem.MediaItem?>(null) }
    var selectedLowerThirdItem by remember { mutableStateOf<ScheduleItem.LowerThirdItem?>(null) }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }

    val mediaViewModel = LocalMediaViewModel.current
    val presentingMode by presenterManager.presentingMode
    val mainFocusRequester = remember { FocusRequester() }


    LaunchedEffect(selectedTabIndex) {
        onTabChange(selectedTabIndex)
        mainFocusRequester.requestFocus()
    }

    // Load songs for the companion server in the background regardless of which tab is active.
    // This runs whenever the storage directory changes (e.g. after settings saved).
    LaunchedEffect(appSettings.songSettings.storageDirectory) {
        val songs = withContext(Dispatchers.IO) {
            val s = Songs()
            val dir = appSettings.songSettings.storageDirectory
            if (dir.isNotEmpty()) {
                val folder = File(dir)
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles { f -> f.extension.lowercase() == Constants.EXTENSION_SPS }
                        ?.sortedBy { it.name }
                        ?.forEach { file ->
                            try { s.loadFromSpsAppend(file.absolutePath) } catch (_: Exception) {}
                        }
                }
            }
            if (s.getSongCount() == 0) {
                try { s.loadFromSps(Constants.FALLBACK_SONG_RESOURCE) } catch (_: Exception) {}
            }
            s.getSongs()
        }
        companionServer.updateSongs(songs)
    }

    // Load primary Bible for the companion server in the background.
    // Re-runs whenever the storage directory or selected primary Bible changes.
    LaunchedEffect(appSettings.bibleSettings.storageDirectory, appSettings.bibleSettings.primaryBible) {
        val storageDir = appSettings.bibleSettings.storageDirectory
        val primaryFile = appSettings.bibleSettings.primaryBible
        if (storageDir.isNotEmpty() && primaryFile.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val f = File(storageDir, primaryFile)
                    if (f.exists()) {
                        val bible = Bible().apply { loadFromSpb(f.absolutePath) }
                        val translationName = primaryFile.substringBeforeLast('.')
                        companionServer.updateBible(bible, translationName)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(mainFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Escape -> {
                            mediaViewModel?.pause(); presenterManager.setPresentingMode(Presenting.NONE); true
                        }

                        Key.F6 -> {
                            selectedTabIndex = Tabs.BIBLE.ordinal; true
                        }

                        Key.F7 -> {
                            selectedTabIndex = Tabs.SONGS.ordinal; true
                        }

                        Key.F8 -> {
                            selectedTabIndex = Tabs.PICTURES.ordinal; true
                        }

                        Key.F9 -> {
                            selectedTabIndex = Tabs.PRESENTATION.ordinal; true
                        }

                        Key.F10 -> {
                            selectedTabIndex = Tabs.MEDIA.ordinal; true
                        }

                        Key.F11 -> {
                            selectedTabIndex = Tabs.LOWER_THIRD.ordinal; true
                        }

                        Key.F12 -> {
                            selectedTabIndex = Tabs.ANNOUNCEMENTS.ordinal; true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                currentTheme = theme,
                onThemeChange = onThemeChange,
                onNewSchedule = { currentScheduleActions.newSchedule() },
                onOpenSchedule = { currentScheduleActions.openSchedule() },
                onSaveSchedule = { currentScheduleActions.saveSchedule() },
                onMoveToTop = { currentScheduleActions.moveSelectedToTop() },
                onMoveUp = { currentScheduleActions.moveSelectedUp() },
                onMoveDown = { currentScheduleActions.moveSelectedDown() },
                onMoveToBottom = { currentScheduleActions.moveSelectedToBottom() },
                onAddToSchedule = { /* handled per-tab via onAddToSchedule callbacks */ },
                onRemoveFromSchedule = { currentScheduleActions.removeSelected() },
                onClearSchedule = { currentScheduleActions.clearSchedule() },
                onAddLabel = { showAddLabelDialog = true },
                onAddWebsite = { showAddWebsiteDialog = true },
                onOpenSettings = onShowSettings
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

                                is ScheduleItem.LowerThirdItem -> {
                                    selectedTabIndex = Tabs.LOWER_THIRD.ordinal
                                    selectedLowerThirdItem = item
                                }

                                is ScheduleItem.AnnouncementItem -> {
                                    selectedTabIndex = Tabs.ANNOUNCEMENTS.ordinal
                                    onSettingsChange { settings ->
                                        settings.copy(
                                            announcementsSettings = settings.announcementsSettings.copy(
                                                text             = item.text,
                                                textColor        = item.textColor,
                                                backgroundColor  = item.backgroundColor,
                                                fontSize         = item.fontSize,
                                                fontType         = item.fontType,
                                                bold             = item.bold,
                                                italic           = item.italic,
                                                underline        = item.underline,
                                                shadow           = item.shadow,
                                                position         = item.position,
                                                animationType    = item.animationType,
                                                animationDuration = item.animationDuration
                                            )
                                        )
                                    }
                                }

                                is ScheduleItem.WebsiteItem -> {
                                    presenterManager.setWebsiteUrl(item.url)
                                    presenterManager.setPresentingMode(Presenting.WEBSITE)
                                }
                            }
                        },
                        onEditLabel = { labelItem ->
                            editingLabelItem = labelItem
                            showAddLabelDialog = true
                        },
                        onActionsReady = { actions ->
                            scheduleActions = actions
                            currentOnScheduleActionsReady(
                                ScheduleActions(
                                    newSchedule = actions.newSchedule,
                                    openSchedule = actions.openSchedule,
                                    saveSchedule = actions.saveSchedule,
                                    saveScheduleAs = actions.saveScheduleAs,
                                    removeSelected = actions.removeSelected,
                                    clearSchedule = actions.clearSchedule
                                )
                            )
                        },
                        onSelectedItemChanged = { id ->
                            onScheduleItemSelected(id)
                        },
                        onScheduleChanged = { items ->
                            companionServer.updateSchedule(items)
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
                                    currentScheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText)
                                },
                                selectedVerseItem = selectedBibleVerseItem,
                                onVerseSelected = onVerseSelected,
                                onPresenting = presenting
                            )

                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { songNumber, title, songbook ->
                                    currentScheduleActions.addSong(songNumber, title, songbook)
                                },
                                selectedSongItem = selectedSongItem,
                                onSongItemSelected = onSongItemSelected,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.LYRICS,
                                theme = theme
                            )

                            Tabs.PICTURES -> PicturesTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { folderPath, folderName, imageCount ->
                                    currentScheduleActions.addPicture(folderPath, folderName, imageCount)
                                },
                                selectedPictureItem = selectedPictureItem,
                                presenterManager = presenterManager
                            )

                            Tabs.PRESENTATION -> PresentationTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { filePath, fileName, slideCount, fileType ->
                                    currentScheduleActions.addPresentation(filePath, fileName, slideCount, fileType)
                                },
                                selectedPresentationItem = selectedPresentationItem,
                                presenterManager = presenterManager
                            )

                            Tabs.MEDIA -> MediaTab(
                                modifier = Modifier.fillMaxSize(),
                                onAddToSchedule = { mediaUrl, mediaTitle, mediaType ->
                                    currentScheduleActions.addMedia(mediaUrl, mediaTitle, mediaType)
                                },
                                selectedMediaItem = selectedMediaItem,
                                presenterManager = presenterManager
                            )

                            Tabs.LOWER_THIRD -> LowerThirdTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                selectedLowerThirdItem = selectedLowerThirdItem,
                                onAddToSchedule = { presetId, presetLabel, pauseAtFrame, pauseDurationMs ->
                                    scheduleActions.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs)
                                },
                                onGoLive = { json, pauseAtFrame, pauseFrame, pauseDurationMs ->
                                    presenterManager.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs)
                                    presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                }
                            )

                            Tabs.ANNOUNCEMENTS -> AnnouncementsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                presenterManager = presenterManager,
                                onAddToSchedule = { settings ->
                                    currentScheduleActions.addAnnouncement(
                                        settings.text,
                                        settings.textColor,
                                        settings.backgroundColor,
                                        settings.fontSize,
                                        settings.fontType,
                                        settings.bold,
                                        settings.italic,
                                        settings.underline,
                                        settings.shadow,
                                        settings.position,
                                        settings.animationType,
                                        settings.animationDuration
                                    )
                                }
                            )
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
                    currentScheduleActions.updateLabel(editingLabelItem!!.id, text, textColor, backgroundColor)
                } else {
                    currentScheduleActions.addLabel(text, textColor, backgroundColor)
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

    if (showAddWebsiteDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddWebsiteDialog = false },
            onConfirm = { url, title ->
                currentScheduleActions.addWebsite(url, title)
                showAddWebsiteDialog = false
            }
        )
    }
}