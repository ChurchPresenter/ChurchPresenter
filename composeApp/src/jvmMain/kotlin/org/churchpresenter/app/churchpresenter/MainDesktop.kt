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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_left
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.tooltip_collapse_schedule
import churchpresenter.composeapp.generated.resources.tooltip_expand_schedule
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.tooltip_toggle_displays
import churchpresenter.composeapp.generated.resources.tooltip_settings
import churchpresenter.composeapp.generated.resources.tab_visibility
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_close
import kotlinx.coroutines.flow.Flow
import org.churchpresenter.app.churchpresenter.utils.AnalyticsReporter
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.dialogs.AddWebsiteDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.PresentationTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTabActions
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.WebTab
import org.churchpresenter.app.churchpresenter.tabs.LowerThirdTab
import org.churchpresenter.app.churchpresenter.tabs.CanvasTab
import org.churchpresenter.app.churchpresenter.tabs.QATab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.tabs.getStringName
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import java.awt.image.BufferedImage
import java.io.File

// Kept for NavigationTopBar / menu — wraps ScheduleTabActions
data class ScheduleActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},
    val clearSchedule: () -> Unit = {},
    // Remote-API add helpers (populated from ScheduleTabActions)
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit = { _, _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> }
)

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager? = null,
    presenting: (Presenting) -> Unit,
    onVerseSelected: (List<SelectedVerse>) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit,
    onAllSectionsChanged: (List<LyricSection>) -> Unit = {},
    onSectionIndexChanged: (Int) -> Unit = {},
    onLineIndexChanged: (Int) -> Unit = {},
    onTabChange: (Int) -> Unit = {},
    onScheduleItemSelected: (String?) -> Unit = {},
    onShowSettings: () -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    onSongsLoaded: ((List<SongItem>) -> Unit)? = null,
    onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null,
    onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    onPresentationSlidesLoaded: ((id: String, filePath: String, fileName: String, fileType: String, slides: List<BufferedImage>) -> Unit)? = null,
    onPicturesLoaded: ((folderId: String, folderName: String, folderPath: String, imageFiles: List<File>) -> Unit)? = null,
    selectPictureImageFlow: Flow<Pair<String, Int>>? = null,
    /**
     * Resolves an image [File] by folder-id and index from the companion server's file map.
     * When non-null, remote picture selections are served from the correct folder even when
     * the requested folder differs from the one currently loaded in the Pictures tab UI
     * (e.g. session-only device_uploads photos).
     */
    resolveImageFile: ((folderId: String, index: Int) -> File?)? = null,
    /** Emits (presentationId, slideIndex) — instantly navigates to that slide without approval. */
    selectSlideFlow: Flow<Pair<String, Int>>? = null,
    /** Emits a verse to display instantly without approval. */
    selectBibleVerseFlow: Flow<SelectBibleVerseRequest>? = null,
    remoteSelectSongFlow: Flow<ScheduleItem.SongItem>? = null,
    /** Emits a presentation [File] uploaded by a mobile client — loaded into [PresentationViewModel] automatically. */
    uploadPresentationFlow: Flow<java.io.File>? = null,
    serverUrl: String = "",
    qaManager: QAManager? = null,
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
    // Incremented every time a song is selected — used as LaunchedEffect key so
    // clicking the same song twice (or API→click) always re-triggers navigation.
    var selectedSongItemVersion by remember { mutableStateOf(0) }
    var selectedPictureItem by remember { mutableStateOf<ScheduleItem.PictureItem?>(null) }
    var selectedPresentationItem by remember { mutableStateOf<ScheduleItem.PresentationItem?>(null) }
    var selectedMediaItem by remember { mutableStateOf<ScheduleItem.MediaItem?>(null) }
    var selectedLowerThirdItem by remember { mutableStateOf<ScheduleItem.LowerThirdItem?>(null) }
    var selectedWebsiteItem by remember { mutableStateOf<ScheduleItem.WebsiteItem?>(null) }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val visibleTabs = remember(appSettings.hiddenTabs) {
        Tabs.entries.filter { it.name !in appSettings.hiddenTabs }.ifEmpty { listOf(Tabs.BIBLE) }
    }
    // Clamp synchronously so no composition pass ever sees an out-of-bounds index.
    val effectiveTabIndex = selectedTabIndex.coerceIn(visibleTabs.indices)
    // Persist the clamped value back into state after composition.
    LaunchedEffect(effectiveTabIndex) {
        if (selectedTabIndex != effectiveTabIndex) selectedTabIndex = effectiveTabIndex
    }
    fun selectTab(tab: Tabs) {
        val idx = visibleTabs.indexOf(tab)
        if (idx >= 0) selectedTabIndex = idx
    }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }

    val mediaViewModel = LocalMediaViewModel.current

    // Hidden VLCJ player for audio: keeps audio playing when user switches away from Media tab.
    // Only composed when NOT on the Media tab (the tab has its own VideoPlayer).
    val currentTab = visibleTabs[effectiveTabIndex]
    if (mediaViewModel != null && mediaViewModel.isAudioFile && mediaViewModel.isPlaying
        && currentTab != Tabs.MEDIA
    ) {
        VideoPlayer(
            viewModel = mediaViewModel,
            modifier = Modifier.size(0.dp)
        )
    }
    // Master video decoder for video files when away from Media tab.
    // When on Media tab, MediaTab hosts its own SoftwareVideoPlayer (the master decoder).
    // Both are mutually exclusive so only one decoder runs at a time.
    if (mediaViewModel != null && !mediaViewModel.isAudioFile && mediaViewModel.isLoaded
        && currentTab != Tabs.MEDIA
    ) {
        SoftwareVideoPlayer(
            viewModel = mediaViewModel,
            modifier = Modifier.size(0.dp)
        )
    }

    val picturesViewModel = remember { PicturesViewModel(appSettings) }
    DisposableEffect(Unit) { onDispose { picturesViewModel.dispose() } }

    val presentationViewModel = remember { PresentationViewModel(appSettings) }
    DisposableEffect(Unit) { onDispose { presentationViewModel.dispose() } }

    val sceneViewModel = remember { SceneViewModel() }

    val currentOnSongsLoaded by rememberUpdatedState(onSongsLoaded)
    val songsViewModel = remember { SongsViewModel(appSettings, onSongsLoaded = { songs -> currentOnSongsLoaded?.invoke(songs) }) }
    DisposableEffect(Unit) { onDispose { songsViewModel.dispose() } }

    // Sync remote section changes (e.g. from mobile) back to the songs UI
    LaunchedEffect(Unit) {
        snapshotFlow { presenterManager.songDisplaySectionIndex.value }
            .collect { index ->
                if (presenterManager.presentingMode.value == Presenting.LYRICS &&
                    songsViewModel.selectedSectionIndex.value != index) {
                    songsViewModel.selectSection(index)
                }
            }
    }

    val currentOnPicturesLoaded by rememberUpdatedState(onPicturesLoaded)
    val currentOnBibleLoaded by rememberUpdatedState(onBibleLoaded)
    val bibleViewModel = remember { BibleViewModel(appSettings, onBibleLoaded = { bible, translation -> currentOnBibleLoaded?.invoke(bible, translation) }) }
    DisposableEffect(Unit) { onDispose { bibleViewModel.dispose() } }

    // ScheduleViewModel is hoisted here (outside AnimatedVisibility) so that collapsing/
    // expanding the schedule panel does NOT destroy the schedule items.
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)
    val scheduleViewModel = remember { ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) }) }

    val presentingMode by presenterManager.presentingMode
    val mainFocusRequester = remember { FocusRequester() }

    // Notify server whenever the picture folder or image list changes
    val pictureImages = picturesViewModel.images
    val pictureFolder = picturesViewModel.selectedFolder
    LaunchedEffect(pictureFolder, pictureImages.size) {
        val folder = pictureFolder ?: return@LaunchedEffect
        if (pictureImages.isEmpty()) return@LaunchedEffect
        val folderId = folder.absolutePath.hashCode().toUInt().toString(16)
        currentOnPicturesLoaded?.invoke(folderId, folder.name, folder.absolutePath, pictureImages.toList())
    }

    // Load picture folder when a picture schedule item is selected (works even before Pictures tab is composed)
    LaunchedEffect(selectedPictureItem) {
        selectedPictureItem?.let { pictureItem ->
            val folder = java.io.File(pictureItem.folderPath)
            if (folder.exists() && folder.isDirectory) {
                picturesViewModel.selectFolder(folder)
            }
        }
    }

    // Handle remote picture selection (from REST POST /api/pictures/select or WS select_picture)
    LaunchedEffect(selectPictureImageFlow) {
        selectPictureImageFlow?.collect { (folderId, index) ->
            // Derive the folderId of the currently loaded Pictures-tab folder (same hash as
            // CompanionServer.updatePictures and the LaunchedEffect(pictureFolder, …) above).
            val activeFolderId = picturesViewModel.selectedFolder
                ?.absolutePath?.hashCode()?.toUInt()?.toString(16)

            // Resolve the file from the server's file map so selections from any folder
            // (including session-only device_uploads) go to the correct image.
            val imageFile = resolveImageFile?.invoke(folderId, index)
            if (imageFile != null && imageFile.exists()) {
                // When the selection is from a DIFFERENT folder (e.g. device_uploads), load
                // that folder into picturesViewModel NOW, before changing the presenting mode.
                // This prevents PicturesTab's syncWithPresenter LaunchedEffect from firing with
                // stale files and overwriting the correct image path in the presenter.
                if (folderId != activeFolderId) {
                    picturesViewModel.selectFolder(imageFile.parentFile)
                }
                // Set the selected index (images are synchronously populated by selectFolder).
                if (index in picturesViewModel.images.indices) {
                    picturesViewModel.selectedImageIndex = index
                }
                // Now syncWithPresenter will read the correct file via getCurrentImageFile().
                presenterManager.setSelectedImagePath(imageFile.absolutePath)
                val nextIdx = if (index in picturesViewModel.images.indices) index + 1 else -1
                presenterManager.setNextImagePath(picturesViewModel.images.getOrNull(nextIdx)?.absolutePath)
                presenterManager.setPresentingMode(Presenting.PICTURES)
                presenterManager.setShowPresenterWindow(true)
            } else {
                // Fallback: resolveImageFile not wired or file not found — use VM directly.
                val images = picturesViewModel.images
                if (index in images.indices) {
                    picturesViewModel.selectedImageIndex = index
                    val currentImage = picturesViewModel.getCurrentImageFile()
                    if (currentImage != null) {
                        presenterManager.setSelectedImagePath(currentImage.absolutePath)
                        presenterManager.setNextImagePath(picturesViewModel.images.getOrNull(index + 1)?.absolutePath)
                        presenterManager.setPresentingMode(Presenting.PICTURES)
                        presenterManager.setShowPresenterWindow(true)
                    }
                }
            }
        }
    }

    // Handle remote slide selection (POST /api/presentations/{id}/select or WS select_slide)
    // No approval required — navigates the live presentation instantly.
    LaunchedEffect(selectSlideFlow) {
        selectSlideFlow?.collect { (_, index) ->
            if (index in presentationViewModel.slides.indices) {
                presentationViewModel.selectSlide(index)
                val slide = presentationViewModel.slides.getOrNull(index)
                presenterManager.setSelectedSlide(slide)
                presenterManager.setNextSlide(presentationViewModel.slides.getOrNull(index + 1))
                presenterManager.setPresenterNotes(presentationViewModel.slideNotes.getOrElse(index) { "" })
                if (presenterManager.presentingMode.value != Presenting.PRESENTATION) {
                    presenterManager.setPresentingMode(Presenting.PRESENTATION)
                    presenterManager.setShowPresenterWindow(true)
                }
            }
        }
    }

    // Handle remote Bible verse instant display (POST /api/bible/select or WS select_bible_verse)
    // No approval required — displays the verse immediately like select_picture.
    // Enriches the bare request with Bible abbreviation/title from loaded Bibles
    // and includes the secondary Bible verse if one is loaded.
    LaunchedEffect(selectBibleVerseFlow) {
        selectBibleVerseFlow?.collect { req ->
            val verses = mutableListOf<SelectedVerse>()
            val primaryBible = bibleViewModel.primaryBible.value
            val secondaryBible = bibleViewModel.secondaryBible.value

            // Resolve bookId from book name using the primary Bible's book list
            val bookIndex = primaryBible?.getBooks()
                ?.indexOfFirst { it.equals(req.bookName, ignoreCase = true) }
                ?: -1
            val bookId = if (bookIndex >= 0) primaryBible?.getBookId(bookIndex) ?: 0 else 0

            // Primary verse enriched with abbreviation and title
            verses.add(
                SelectedVerse(
                    bibleAbbreviation = primaryBible?.getBibleAbbreviation() ?: "",
                    bibleName = primaryBible?.getBibleTitle() ?: "",
                    bookName    = req.bookName,
                    chapter     = req.chapter,
                    verseNumber = req.verseNumber,
                    verseText   = req.verseText,
                    verseRange  = req.verseRange
                )
            )

            // Secondary verse (if a secondary Bible is loaded)
            if (secondaryBible != null && bookId > 0) {
                val codeRef = primaryBible?.getCodeReference(bookId, req.chapter, req.verseNumber)
                val secBook = codeRef?.first ?: bookId
                val secChapter = codeRef?.second ?: req.chapter
                val secVerse = codeRef?.third ?: req.verseNumber
                secondaryBible.getVerseDetailsByCode(secBook, secChapter, secVerse)?.let { result ->
                    verses.add(
                        SelectedVerse(
                            bibleAbbreviation = secondaryBible.getBibleAbbreviation(),
                            bibleName = secondaryBible.getBibleTitle(),
                            bookName = result.bookName,
                            chapter = result.displayChapter,
                            verseNumber = result.displayVerse,
                            verseText = result.verseText,
                            verseRange = req.verseRange
                        )
                    )
                }
            }

            presenterManager.setSelectedVerses(verses)
            presenterManager.setPresentingMode(Presenting.BIBLE)
            presenterManager.setShowPresenterWindow(true)
        }
    }

    // Handle remote song selection — set selectedSongItem so the Songs tab navigates to it
    LaunchedEffect(remoteSelectSongFlow) {
        remoteSelectSongFlow?.collect { songItem ->
            selectedSongItem = songItem
            selectedSongItemVersion++
            selectTab(Tabs.SONGS)
        }
    }

    // Load a presentation file uploaded by a mobile client (POST /api/presentations/upload).
    // addPresentation renders the slides and triggers onSlidesLoaded → companionServer.updatePresentation,
    // which broadcasts WS_EVENT_PRESENTATION_UPDATED so the mobile's GET /api/presentations finds it.
    LaunchedEffect(uploadPresentationFlow) {
        uploadPresentationFlow?.collect { file ->
            presentationViewModel.addPresentation(file)
            // Switch to the Presentations tab so the user can see the newly loaded file
            selectTab(Tabs.PRESENTATION)
        }
    }

    LaunchedEffect(selectedTabIndex) {
        onTabChange(selectedTabIndex)
        AnalyticsReporter.logPageView(visibleTabs.getOrNull(selectedTabIndex)?.name ?: "unknown")
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
                            presenterManager.requestClearDisplay()
                            true
                        }

                        Key.F6 -> {
                            selectTab(Tabs.BIBLE); true
                        }

                        Key.F7 -> {
                            selectTab(Tabs.SONGS); true
                        }

                        Key.F8 -> {
                            selectTab(Tabs.PICTURES); true
                        }

                        Key.F9 -> {
                            selectTab(Tabs.PRESENTATION); true
                        }

                        Key.F10 -> {
                            selectTab(Tabs.MEDIA); true
                        }

                        Key.F11 -> {
                            selectTab(Tabs.LOWER_THIRD); true
                        }

                        Key.F12 -> {
                            selectTab(Tabs.ANNOUNCEMENTS); true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var scheduleCollapsed by remember { mutableStateOf(appSettings.schedulePanelCollapsed) }

            val density = LocalDensity.current
            val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

            // Schedule panel width — loaded from settings, local state for smooth dragging
            var schedulePanelPx by remember(appSettings.schedulePanelWidthDp) {
                mutableStateOf(with(density) { appSettings.schedulePanelWidthDp.dp.toPx() })
            }
            var previewCollapsed by remember { mutableStateOf(appSettings.previewPanelCollapsed) }
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
                        scheduleViewModel = scheduleViewModel,
                        onPresenting = presenting,
                        onAddLabel = { showAddLabelDialog = true },
                        onAddWebsite = { showAddWebsiteDialog = true },

                        onPresentBible = { item ->
                            selectedBibleVerseItem = item
                            presenting(Presenting.BIBLE)
                        },
                        onPresentSong = { item ->
                            selectedSongItem = item
                            selectedSongItemVersion++
                            onSongItemSelected(
                                LyricSection(
                                    title = item.title,
                                    songNumber = item.songNumber,
                                    lines = emptyList(),
                                    type = Constants.SECTION_TYPE_SONG
                                )
                            )
                            statisticsManager?.recordSongDisplay(
                                songNumber = item.songNumber,
                                title = item.title,
                                songbook = item.songbook
                            )
                            presenting(Presenting.LYRICS)
                        },
                        onPresentPresentation = { item ->
                            selectedPresentationItem = item
                            presenting(Presenting.PRESENTATION)
                        },
                        onPresentPictures = { item ->
                            selectedPictureItem = item
                            selectTab(Tabs.PICTURES)
                            presenting(Presenting.PICTURES)
                        },
                        onPresentMedia = { item ->
                            selectedMediaItem = item
                            presenting(Presenting.MEDIA)
                        },
                        onPresentAnnouncement = { item ->
                            onSettingsChange { settings ->
                                settings.copy(
                                    announcementsSettings = settings.announcementsSettings.copy(
                                        text                = item.text,
                                        textColor           = item.textColor,
                                        backgroundColor     = item.backgroundColor,
                                        fontSize            = item.fontSize,
                                        fontType            = item.fontType,
                                        bold                = item.bold,
                                        italic              = item.italic,
                                        underline           = item.underline,
                                        shadow              = item.shadow,
                                        horizontalAlignment = item.horizontalAlignment,
                                        position            = item.position,
                                        animationType       = item.animationType,
                                        animationDuration   = item.animationDuration,
                                        timerHours          = item.timerHours,
                                        timerMinutes        = item.timerMinutes,
                                        timerSeconds        = item.timerSeconds,
                                        timerTextColor      = item.timerTextColor,
                                        timerExpiredText    = item.timerExpiredText
                                    )
                                )
                            }
                            presenterManager.setAnnouncementText(item.text)
                            presenting(Presenting.ANNOUNCEMENTS)
                        },
                        onPresentLowerThird = { item ->
                            val lottieFolder = java.io.File(appSettings.streamingSettings.lowerThirdFolder)
                            val lottieFile = lottieFolder.listFiles()?.find {
                                it.nameWithoutExtension == item.presetLabel || it.nameWithoutExtension == item.presetId
                            }
                            if (lottieFile != null && lottieFile.exists()) {
                                val json = lottieFile.readText()
                                presenterManager.setLottieContent(json, item.pauseAtFrame, -1f, item.pauseDurationMs)
                                presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                presenterManager.setShowPresenterWindow(true)
                            }
                        },
                        onPresentWebsite = { item ->
                            selectedWebsiteItem = item
                            selectTab(Tabs.WEB)
                            presenterManager.setWebsiteUrl(item.url)
                            presenting(Presenting.WEBSITE)
                        },
                        onItemClick = { item ->
                            when (item) {
                                is ScheduleItem.SongItem -> {
                                    selectTab(Tabs.SONGS)
                                    selectedSongItem = item
                                    selectedSongItemVersion++
                                }

                                is ScheduleItem.BibleVerseItem -> {
                                    selectTab(Tabs.BIBLE)
                                    selectedBibleVerseItem = item
                                }

                                is ScheduleItem.LabelItem -> {
                                    editingLabelItem = item
                                    showAddLabelDialog = true
                                }

                                is ScheduleItem.PictureItem -> {
                                    selectTab(Tabs.PICTURES)
                                    selectedPictureItem = item
                                }

                                is ScheduleItem.PresentationItem -> {
                                    selectTab(Tabs.PRESENTATION)
                                    selectedPresentationItem = item
                                }

                                is ScheduleItem.MediaItem -> {
                                    selectTab(Tabs.MEDIA)
                                    selectedMediaItem = item
                                }

                                is ScheduleItem.LowerThirdItem -> {
                                    selectTab(Tabs.LOWER_THIRD)
                                    selectedLowerThirdItem = item
                                }

                                is ScheduleItem.AnnouncementItem -> {
                                    selectTab(Tabs.ANNOUNCEMENTS)
                                    onSettingsChange { settings ->
                                        settings.copy(
                                            announcementsSettings = settings.announcementsSettings.copy(
                                                text                = item.text,
                                                textColor           = item.textColor,
                                                backgroundColor     = item.backgroundColor,
                                                fontSize            = item.fontSize,
                                                fontType            = item.fontType,
                                                bold                = item.bold,
                                                italic              = item.italic,
                                                underline           = item.underline,
                                                shadow              = item.shadow,
                                                horizontalAlignment = item.horizontalAlignment,
                                                position            = item.position,
                                                animationType       = item.animationType,
                                                animationDuration   = item.animationDuration,
                                                timerHours          = item.timerHours,
                                                timerMinutes        = item.timerMinutes,
                                                timerSeconds        = item.timerSeconds,
                                                timerTextColor      = item.timerTextColor,
                                                timerExpiredText    = item.timerExpiredText
                                            )
                                        )
                                    }
                                }

                                is ScheduleItem.WebsiteItem -> {
                                    selectedWebsiteItem = item
                                    selectTab(Tabs.WEB)
                                }

                                is ScheduleItem.SceneItem -> {
                                    sceneViewModel.selectScene(item.sceneId)
                                    selectTab(Tabs.CANVAS)
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
                                    clearSchedule = actions.clearSchedule,
                                    addSong = actions.addSong,
                                    addBibleVerse = actions.addBibleVerse,
                                    addPicture = actions.addPicture,
                                    addPresentation = actions.addPresentation,
                                    addMedia = actions.addMedia,
                                    addScene = actions.addScene
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
                        .pointerInput(scheduleCollapsed, appSettings.schedulePanelWidthDp) {
                            if (!scheduleCollapsed) {
                                detectHorizontalDragGestures(
                                    onDragEnd = ::saveScheduleWidth
                                ) { _, amount ->
                                    schedulePanelPx = (schedulePanelPx + amount).coerceIn(
                                        with(density) { 160.dp.toPx() },
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
                        onClick = {
                            scheduleCollapsed = !scheduleCollapsed
                            onSettingsChangeState.value { s -> s.copy(schedulePanelCollapsed = scheduleCollapsed) }
                        },
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
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabSection(
                            modifier = Modifier.weight(1f),
                            visibleTabs = visibleTabs,
                            selectedTabIndex = effectiveTabIndex,
                            onTabSelected = { selectedTabIndex = it }
                        )
                        // Tab visibility dropdown button
                        var showTabVisibilityMenu by remember { mutableStateOf(false) }
                        Box {
                            TooltipIconButton(
                                painter = rememberVectorPainter(Icons.Default.Tune),
                                text = stringResource(Res.string.tab_visibility),
                                onClick = { showTabVisibilityMenu = true },
                                buttonSize = 36.dp,
                                iconTint = MaterialTheme.colorScheme.onSurface
                            )
                            DropdownMenu(
                                expanded = showTabVisibilityMenu,
                                onDismissRequest = { showTabVisibilityMenu = false }
                            ) {
                                val visibleCount = Tabs.entries.count { it.name !in appSettings.hiddenTabs }
                                Tabs.entries.forEach { tab ->
                                    val isVisible = tab.name !in appSettings.hiddenTabs
                                    val isOnlyVisible = isVisible && visibleCount == 1
                                    DropdownMenuItem(
                                        text = { Text(getStringName(tab)) },
                                        onClick = {
                                            if (!isOnlyVisible) {
                                                onSettingsChange { s ->
                                                    val newHidden = if (isVisible) {
                                                        s.hiddenTabs + tab.name
                                                    } else {
                                                        s.hiddenTabs - tab.name
                                                    }
                                                    s.copy(hiddenTabs = newHidden)
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = isVisible,
                                                onCheckedChange = null,
                                                enabled = !isOnlyVisible
                                            )
                                        },
                                        enabled = !isOnlyVisible
                                    )
                                }
                            }
                        }
                        TooltipIconButton(
                            painter = painterResource(Res.drawable.ic_settings),
                            text = stringResource(Res.string.tooltip_settings),
                            onClick = onShowSettings,
                            buttonSize = 36.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            Tabs.BIBLE -> BibleTab(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = bibleViewModel,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { bookName, chapter, verseNumber, verseText, verseRange ->
                                    currentScheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange)
                                },
                                selectedVerseItem = selectedBibleVerseItem,
                                onVerseSelected = onVerseSelected,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.BIBLE,
                                presenterManager = presenterManager,
                                statisticsManager = statisticsManager
                            )

                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = songsViewModel,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { songNumber, title, songbook, songId ->
                                    currentScheduleActions.addSong(songNumber, title, songbook, songId)
                                },
                                selectedSongItem = selectedSongItem,
                                selectedSongItemVersion = selectedSongItemVersion,
                                onSongItemSelected = onSongItemSelected,
                                onAllSectionsChanged = onAllSectionsChanged,
                                onSectionIndexChanged = onSectionIndexChanged,
                                onLineIndexChanged = onLineIndexChanged,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.LYRICS,
                                theme = theme,
                                statisticsManager = statisticsManager
                            )

                            Tabs.PICTURES -> PicturesTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { folderPath, folderName, imageCount ->
                                    currentScheduleActions.addPicture(folderPath, folderName, imageCount)
                                },
                                selectedPictureItem = selectedPictureItem,
                                presenterManager = presenterManager,
                                onSettingsChange = onSettingsChange,
                                viewModel = picturesViewModel
                            )

                            Tabs.PRESENTATION -> PresentationTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { filePath, fileName, slideCount, fileType ->
                                    currentScheduleActions.addPresentation(filePath, fileName, slideCount, fileType)
                                },
                                selectedPresentationItem = selectedPresentationItem,
                                presenterManager = presenterManager,
                                onSlidesLoaded = onPresentationSlidesLoaded,
                                viewModel = presentationViewModel
                            )

                            Tabs.MEDIA -> MediaTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
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
                                isDarkTheme = isDarkTheme,
                                serverUrl = serverUrl
                            )

                            Tabs.ANNOUNCEMENTS -> AnnouncementsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                presenterManager = presenterManager,
                                onAddToSchedule = { settings ->
                                    val isTimer = settings.timerHours > 0 || settings.timerMinutes > 0 || settings.timerSeconds > 0
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
                                        settings.horizontalAlignment,
                                        settings.position,
                                        settings.animationType,
                                        settings.animationDuration,
                                        isTimer,
                                        settings.timerHours,
                                        settings.timerMinutes,
                                        settings.timerSeconds,
                                        settings.timerTextColor,
                                        settings.timerExpiredText
                                    )
                                }
                            )

                            Tabs.WEB -> WebTab(
                                modifier = Modifier.fillMaxSize(),
                                presenterManager = presenterManager,
                                selectedWebsiteItem = selectedWebsiteItem,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { url, title ->
                                    currentScheduleActions.addWebsite(url, title)
                                },
                                onUpdateScheduleTitle = { url, title ->
                                    currentScheduleActions.updateWebsiteTitle(url, title)
                                }
                            )

                            Tabs.CANVAS -> CanvasTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                presenterManager = presenterManager,
                                sceneViewModel = sceneViewModel,
                                onAddToSchedule = { sceneId, sceneName ->
                                    currentScheduleActions.addScene(sceneId, sceneName)
                                }
                            )

                            Tabs.QA -> if (qaManager != null) {
                                QATab(
                                    modifier = Modifier.fillMaxSize(),
                                    qaManager = qaManager,
                                    presenterManager = presenterManager,
                                    serverUrl = serverUrl,
                                    presenting = presenting,
                                    appSettings = appSettings,
                                    onSettingsChange = onSettingsChange
                                )
                            }
                        }
                    }
                }

                // Right drag handle + collapse toggle for preview panel
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(previewCollapsed, appSettings.previewPanelWidthDp) {
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
                        onClick = {
                            previewCollapsed = !previewCollapsed
                            onSettingsChangeState.value { s -> s.copy(previewPanelCollapsed = previewCollapsed) }
                        },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TooltipIconButton(
                                painter = painterResource(Res.drawable.ic_cast),
                                text = stringResource(Res.string.tooltip_toggle_displays),
                                onClick = { presenterManager.togglePresenterWindow() },
                                buttonSize = 36.dp,
                                iconTint = if (presenterManager.showPresenterWindow.value)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            TooltipIconButton(
                                painter = painterResource(Res.drawable.ic_close),
                                text = stringResource(Res.string.tooltip_clear_display),
                                onClick = {
                                    mediaViewModel?.pause()
                                    presenterManager.requestClearDisplay()
                                },
                                buttonSize = 36.dp,
                                iconTint = MaterialTheme.colorScheme.error
                            )
                        }
                        LivePreviewPanel(
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            modifier = Modifier.fillMaxWidth(),
                            serverUrl = serverUrl,
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
                    currentScheduleActions.updateLabel(editingLabelItem?.id ?: return@AddLabelDialog, text, textColor, backgroundColor)
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