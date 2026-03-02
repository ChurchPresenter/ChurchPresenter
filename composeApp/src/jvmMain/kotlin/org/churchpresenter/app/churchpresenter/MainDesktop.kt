package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_left
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.tooltip_collapse_schedule
import churchpresenter.composeapp.generated.resources.tooltip_expand_schedule
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.tooltip_settings
import churchpresenter.composeapp.generated.resources.ic_close
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    onSongsLoaded: ((List<org.churchpresenter.app.churchpresenter.data.SongItem>) -> Unit)? = null,
    onBibleLoaded: ((bible: org.churchpresenter.app.churchpresenter.data.Bible, translation: String) -> Unit)? = null,
    onScheduleChanged: ((List<org.churchpresenter.app.churchpresenter.models.ScheduleItem>) -> Unit)? = null
) {
    val isDarkTheme = when (theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

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
        // Re-request focus so F-key shortcuts keep working after the new tab's children steal focus
        mainFocusRequester.requestFocus()
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
                            mediaViewModel?.pause()
                            presenterManager.setPresentingMode(Presenting.NONE)
                            true
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
            var scheduleCollapsed by rememberSaveable { mutableStateOf(false) }

            val density = LocalDensity.current
            val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

            // Schedule panel width — loaded from settings, local state for smooth dragging
            var schedulePanelPx by remember(appSettings.schedulePanelWidthDp) {
                mutableStateOf(with(density) { appSettings.schedulePanelWidthDp.dp.toPx() })
            }
            var previewCollapsed by rememberSaveable { mutableStateOf(false) }
            var previewPanelPx by remember(appSettings.previewPanelWidthDp) {
                mutableStateOf(with(density) { appSettings.previewPanelWidthDp.dp.toPx() })
            }

            fun saveScheduleWidth() {
                onSettingsChangeState.value { s ->
                    s.copy(schedulePanelWidthDp = with(density) { schedulePanelPx.toDp().value.toInt() })
                }
            }

            fun savePreviewWidth() {
                onSettingsChangeState.value { s ->
                    s.copy(previewPanelWidthDp = with(density) { previewPanelPx.toDp().value.toInt() })
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Collapsible schedule panel
                AnimatedVisibility(
                    visible = !scheduleCollapsed,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    Column(modifier = Modifier.width(with(density) { schedulePanelPx.toDp() }).fillMaxHeight()) {
                    ScheduleTab(
                        onPresenting = presenting,
                        onAddLabel = { showAddLabelDialog = true },
                        onAddWebsite = { showAddWebsiteDialog = true },
                        onAddToSchedule = { /* handled per-tab */ },
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
                                                text              = item.text,
                                                textColor         = item.textColor,
                                                backgroundColor   = item.backgroundColor,
                                                fontSize          = item.fontSize,
                                                fontType          = item.fontType,
                                                bold              = item.bold,
                                                italic            = item.italic,
                                                underline         = item.underline,
                                                shadow            = item.shadow,
                                                position          = item.position,
                                                animationType     = item.animationType,
                                                animationDuration = item.animationDuration,
                                                timerMinutes      = item.timerMinutes,
                                                timerSeconds      = item.timerSeconds,
                                                timerTextColor    = item.timerTextColor,
                                                timerExpiredText  = item.timerExpiredText
                                            )
                                        )
                                    }
                                }

                                is ScheduleItem.WebsiteItem -> {
                                    presenterManager.setWebsiteUrl(item.url)
                                    presenterManager.setPresentingMode(Presenting.WEBSITE)
                                    presenterManager.setShowPresenterWindow(true)
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
                        onScheduleChanged = onScheduleChanged
                    )
                    } // end Column
                } // end AnimatedVisibility

                // Drag handle + collapse toggle between schedule and main content
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(scheduleCollapsed) {
                            if (!scheduleCollapsed) {
                                detectHorizontalDragGestures(
                                    onDragEnd = ::saveScheduleWidth
                                ) { _, amount ->
                                    schedulePanelPx = (schedulePanelPx + amount).coerceIn(
                                        with(density) { 150.dp.toPx() },
                                        with(density) { 600.dp.toPx() }
                                    )
                                }
                            }
                        }
                        .pointerHoverIcon(
                            if (scheduleCollapsed) PointerIcon.Default else PointerIcon.Hand
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { scheduleCollapsed = !scheduleCollapsed },
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        Icon(
                            painter = painterResource(
                                if (scheduleCollapsed) Res.drawable.ic_arrow_right
                                else Res.drawable.ic_arrow_left
                            ),
                            contentDescription = stringResource(
                                if (scheduleCollapsed) Res.string.tooltip_expand_schedule
                                else Res.string.tooltip_collapse_schedule
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabSection(
                            modifier = Modifier.weight(1f),
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it }
                        )
                        TooltipIconButton(
                            painter = painterResource(Res.drawable.ic_settings),
                            text = stringResource(Res.string.tooltip_settings),
                            onClick = onShowSettings,
                            buttonSize = 36.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val currentTab = Tabs.entries[selectedTabIndex]

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            Tabs.BIBLE -> BibleTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { bookName, chapter, verseNumber, verseText ->
                                    currentScheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText)
                                },
                                selectedVerseItem = selectedBibleVerseItem,
                                onVerseSelected = onVerseSelected,
                                onPresenting = presenting,
                                onBibleLoaded = onBibleLoaded
                            )

                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { songNumber, title, songbook ->
                                    currentScheduleActions.addSong(songNumber, title, songbook)
                                },
                                selectedSongItem = selectedSongItem,
                                onSongItemSelected = onSongItemSelected,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.LYRICS,
                                theme = theme,
                                onSongsLoaded = onSongsLoaded
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
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { presetId, presetLabel, pauseAtFrame, pauseDurationMs ->
                                    scheduleActions.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs)
                                },
                                onGoLive = { json, pauseAtFrame, pauseFrame, pauseDurationMs ->
                                    presenterManager.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs)
                                    presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                    presenterManager.setShowPresenterWindow(true)
                                },
                                isDarkTheme = isDarkTheme
                            )

                            Tabs.ANNOUNCEMENTS -> AnnouncementsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                presenterManager = presenterManager,
                                onAddToSchedule = { settings ->
                                    val isTimer = settings.timerMinutes > 0 || settings.timerSeconds > 0
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
                                        settings.animationDuration,
                                        isTimer,
                                        settings.timerMinutes,
                                        settings.timerSeconds,
                                        settings.timerTextColor,
                                        settings.timerExpiredText
                                    )
                                }
                            )
                        }
                    }
                }

                // Right drag handle + collapse toggle for preview panel
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(previewCollapsed) {
                            if (!previewCollapsed) {
                                detectHorizontalDragGestures(
                                    onDragEnd = ::savePreviewWidth
                                ) { _, amount ->
                                    // Invert drag direction: dragging left increases width
                                    previewPanelPx = (previewPanelPx - amount).coerceIn(
                                        with(density) { 150.dp.toPx() },
                                        with(density) { 600.dp.toPx() }
                                    )
                                }
                            }
                        }
                        .pointerHoverIcon(
                            if (previewCollapsed) PointerIcon.Default else PointerIcon.Hand
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { previewCollapsed = !previewCollapsed },
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        Icon(
                            painter = painterResource(
                                if (previewCollapsed) Res.drawable.ic_arrow_left
                                else Res.drawable.ic_arrow_right
                            ),
                            contentDescription = stringResource(
                                if (previewCollapsed) Res.string.tooltip_expand_schedule
                                else Res.string.tooltip_collapse_schedule
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Collapsible preview panel (right sidebar)
                AnimatedVisibility(
                    visible = !previewCollapsed,
                    enter = expandHorizontally(expandFrom = Alignment.End),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End)
                ) {
                    Column(
                        modifier = Modifier
                            .width(with(density) { previewPanelPx.toDp() })
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) {
                        TooltipIconButton(
                            painter = painterResource(Res.drawable.ic_close),
                            text = stringResource(Res.string.tooltip_clear_display),
                            onClick = {
                                mediaViewModel?.pause()
                                presenterManager.setPresentingMode(Presenting.NONE)
                            },
                            buttonSize = 36.dp,
                            iconTint = MaterialTheme.colorScheme.error
                        )
                        LivePreviewPanel(
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            modifier = Modifier.fillMaxWidth()
                        )
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