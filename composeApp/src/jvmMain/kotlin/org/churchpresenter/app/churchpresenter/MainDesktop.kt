package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.connect
import churchpresenter.composeapp.generated.resources.instance_link_controlling_host
import churchpresenter.composeapp.generated.resources.instance_link_following_host
import churchpresenter.composeapp.generated.resources.instance_link_primary_badge
import churchpresenter.composeapp.generated.resources.menu_disconnect
import churchpresenter.composeapp.generated.resources.ic_arrow_left
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.tooltip_collapse_schedule
import churchpresenter.composeapp.generated.resources.tooltip_expand_schedule
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.tooltip_toggle_displays
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.tooltip_settings
import churchpresenter.composeapp.generated.resources.tab_visibility
import churchpresenter.composeapp.generated.resources.ic_close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toComposeImageBitmap

import org.churchpresenter.app.churchpresenter.composables.ConnectionStatusRow
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSyncMode
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.dialogs.AddWebsiteDialog
import org.churchpresenter.app.churchpresenter.dialogs.CrashFeedbackDialog
import org.churchpresenter.app.churchpresenter.dialogs.KonamiEasterEggDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDetailDto
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.PresentationTab
import org.churchpresenter.app.churchpresenter.tabs.CompanionSurfaceTab
import org.churchpresenter.app.churchpresenter.composables.CompanionConnectionChipRow
import org.churchpresenter.app.churchpresenter.composables.CompanionSurfacePanel
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTabActions
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.WebTab
import org.churchpresenter.app.churchpresenter.tabs.LowerThirdTab
import org.churchpresenter.app.churchpresenter.tabs.CanvasTab
import org.churchpresenter.app.churchpresenter.tabs.QATab
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.tabs.CrosswordTab
import org.churchpresenter.app.churchpresenter.tabs.DictionaryTab
import org.churchpresenter.app.churchpresenter.tabs.STTTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.tabs.getStringName
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel

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
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> },
    val addDictionary: (number: String, word: String, transliteration: String, definition: String) -> Unit = { _, _, _, _ -> }
)

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    // Same as appSettings except backgroundSettings may be swapped for a mirrored-from-primary copy
    // (Instance Link) — used ONLY at the live-preview render call site below, never for editing/
    // persistence, so the Options dialog still shows this instance's own local background settings.
    livePreviewAppSettings: AppSettings = appSettings,
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
    onShowBackgroundSettings: () -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    onSongsLoaded: ((List<SongItem>) -> Unit)? = null,
    onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null,
    onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    onPresentationSlidesLoaded: ((id: String, filePath: String, fileName: String, fileType: String, slideFiles: List<File>, slideNotes: List<String>) -> Unit)? = null,
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
    /** Persistent "Following <host>" badge shown above the Schedule panel while connected via Instance Link. */
    instanceLinkConnectionStatus: InstanceLinkStatus = InstanceLinkStatus.DISCONNECTED,
    instanceLinkFollowingHost: String = "",
    /** Persistent "Primary — N follower(s) connected" badge — the symmetric primary-side counterpart. */
    connectedInstanceLinkFollowerCount: Int = 0,
    /** Reconnects using the last-saved Instance Link settings — lets the Connect/Disconnect button
     *  next to the badge work without reopening the Connect dialog. */
    onInstanceLinkConnect: () -> Unit = {},
    onInstanceLinkDisconnect: () -> Unit = {},
    /** The primary's live schedule while connected via Instance Link — mirrored into [ScheduleViewModel]. */
    instanceLinkRemoteSchedule: List<ScheduleItemDto> = emptyList(),
    /** The primary's song catalog while connected via Instance Link — mirrored into [SongsViewModel]. */
    instanceLinkRemoteSongCatalog: SongCatalogResponse? = null,
    /** Fetches one song's full lyrics from the primary on demand — see SongsViewModel.setInstanceLinkSource. */
    instanceLinkFetchSongDetail: (suspend (number: String, songbook: String) -> SongDetailDto?)? = null,
    /** Downloads the primary's bible file while connected via Instance Link — see BibleViewModel.setInstanceLinkSource. */
    instanceLinkFetchBibleFile: (suspend () -> ByteArray?)? = null,
    /** How the Bible tab tracks the primary while connected — see BibleSyncMode. */
    instanceLinkBibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    instanceLinkFetchSecondaryBibleFile: (suspend () -> ByteArray?)? = null,
    /** Reports the secondary bible's local file path to CompanionServer (for GET /api/bible/file/secondary). */
    instanceLinkOnSecondaryBibleFilePathChanged: ((filePath: String) -> Unit)? = null,
    /** Non-null while connected via Instance Link — see MediaTab's instanceLinkMediaStreamUrl. */
    instanceLinkMediaStreamUrl: ((itemId: String) -> String)? = null,
    /** Non-null only when connected AND the operator has enabled pushing items to the primary's
     *  schedule — see ScheduleViewModel.onPushToRemoteSchedule. */
    instanceLinkSendAddToSchedule: ((ScheduleItem) -> Unit)? = null,
    /** See InstanceLinkRole — CONTROLLED (default, mirror the primary) or CONTROLLER (drive it). */
    instanceLinkRole: InstanceLinkRole = InstanceLinkRole.CONTROLLED,
    /** Controller mode "go live with a new item" — approval-gated the first time on the primary,
     *  instant afterwards. Non-null only when connected AND in Controller mode. */
    instanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Controller mode instant Bible verse display — non-null only when connected AND controlling. */
    instanceLinkSendVerse: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit)? = null,
    /** Controller mode instant picture display — non-null only when connected AND controlling. */
    instanceLinkSendPicture: ((folderId: String, index: Int, fileName: String?) -> Unit)? = null,
    /** Controller mode instant song-section navigation (within an already-live song) — non-null only
     *  when connected AND controlling. */
    instanceLinkSendSongSection: ((number: String, section: Int) -> Unit)? = null,
    /** Controller mode instant slide navigation (within an already-live presentation) — non-null only
     *  when connected AND controlling. */
    instanceLinkSendSlide: ((id: String, index: Int) -> Unit)? = null,
    /** Controller mode instant clear — non-null only when connected AND controlling. */
    instanceLinkSendClear: (() -> Unit)? = null,
    qaManager: QAManager? = null,
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    qaDisplayUrl: String = "",
    onQaDisplayUrlChanged: (String) -> Unit = {},
    presentationDisplayUrl: String = "",
    onPresentationDisplayUrlChanged: (String) -> Unit = {},
    presentationFrozen: Boolean = false,
    onFreezeToggle: () -> Unit = {},
    onClearPresentation: () -> Unit = {},
    onSlideChanged: ((id: String, slideIndex: Int, total: Int, isPlaying: Boolean) -> Unit)? = null,
    remotePresentationPlayPauseFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    remotePresentationLoopToggleFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    remotePresentationGotoFlow: kotlinx.coroutines.flow.Flow<Int>? = null,
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    sttManager: STTManager? = null,
    dialogDismissSignal: Int = 0,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
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
    // Incremented every time a song is selected — used as LaunchedEffect key so
    // clicking the same song twice (or API→click) always re-triggers navigation.
    var selectedSongItemVersion by remember { mutableStateOf(0) }
    var selectedPictureItem by remember { mutableStateOf<ScheduleItem.PictureItem?>(null) }
    var selectedPresentationItem by remember { mutableStateOf<ScheduleItem.PresentationItem?>(null) }
    var selectedMediaItem by remember { mutableStateOf<ScheduleItem.MediaItem?>(null) }
    var selectedLowerThirdItem by remember { mutableStateOf<ScheduleItem.LowerThirdItem?>(null) }
    var selectedWebsiteItem by remember { mutableStateOf<ScheduleItem.WebsiteItem?>(null) }
    var scheduleTimerVersion by remember { mutableStateOf(0) }

    var showCrosswordTab by remember { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val hasCompanionTabConnections = appSettings.companionSatelliteConnections.any { it.showInTab && it.host.isNotBlank() }
    val visibleTabs = remember(appSettings.hiddenTabs, showCrosswordTab, hasCompanionTabConnections) {
        (Tabs.entries.filter { tab ->
            tab != Tabs.CROSSWORD && tab.name !in appSettings.hiddenTabs &&
                (tab != Tabs.COMPANION_SURFACE || hasCompanionTabConnections)
        } + if (showCrosswordTab) listOf(Tabs.CROSSWORD) else emptyList())
            .ifEmpty { listOf(Tabs.BIBLE) }
    }
    // Clamp synchronously so no composition pass ever sees an out-of-bounds index.
    val effectiveTabIndex = selectedTabIndex.coerceIn(visibleTabs.indices)
    // Persist the clamped value back into state after composition.
    LaunchedEffect(effectiveTabIndex) {
        if (selectedTabIndex != effectiveTabIndex) selectedTabIndex = effectiveTabIndex
    }

    // Remember the URL of the first successful STT connection so the Bible-tab connect
    // button stays visible across restarts (and hides again if the URL later changes).
    val sttConnected = sttManager?.connected?.value == true
    LaunchedEffect(sttConnected) {
        if (sttConnected) {
            val url = appSettings.sttSettings.serverUrl
            if (appSettings.sttSettings.lastConnectedUrl != url) {
                onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(lastConnectedUrl = url)) }
            }
        }
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
            modifier = Modifier.size(0.dp),
            // This decoder only exists to keep rendering a frame while off the Media tab.
            // The only control that can set isPlaying = true lives on the Media tab itself,
            // so if this mounts paused it stays paused for its whole lifetime here — safe to
            // disable its audio track outright rather than rely on a volume of 0.
            audioEnabled = mediaViewModel.isPlaying
        )
    }

    val picturesViewModel = remember { PicturesViewModel(appSettings) }
    DisposableEffect(Unit) { onDispose { picturesViewModel.dispose() } }

    val presentationViewModel = remember { PresentationViewModel(appSettings) }
    DisposableEffect(Unit) { onDispose { presentationViewModel.dispose() } }

    LaunchedEffect(presentationViewModel.selectedSlideIndex, presentationViewModel.slideFiles.size, presentationViewModel.isPlaying) {
        val f = presentationViewModel.selectedPresentation ?: return@LaunchedEffect
        val id = f.absolutePath.hashCode().toUInt().toString(16)
        onSlideChanged?.invoke(id, presentationViewModel.selectedSlideIndex, presentationViewModel.slideFiles.size, presentationViewModel.isPlaying)
    }
    LaunchedEffect(appSettings.presentationRemoteSettings.remoteControlEnabled) {
        if (!appSettings.presentationRemoteSettings.remoteControlEnabled) return@LaunchedEffect
        val f = presentationViewModel.selectedPresentation ?: return@LaunchedEffect
        if (presentationViewModel.slideFiles.isEmpty()) return@LaunchedEffect
        val id = f.absolutePath.hashCode().toUInt().toString(16)
        onSlideChanged?.invoke(
            id,
            presentationViewModel.selectedSlideIndex,
            presentationViewModel.slideFiles.size,
            presentationViewModel.isPlaying
        )
        onPresentationSlidesLoaded?.invoke(
            id,
            f.absolutePath,
            f.nameWithoutExtension,
            f.extension.lowercase(),
            presentationViewModel.slideFiles.toList(),
            presentationViewModel.slideNotes.toList()
        )
    }
    LaunchedEffect(remotePresentationPlayPauseFlow) {
        remotePresentationPlayPauseFlow?.collect { presentationViewModel.togglePlayPause() }
    }
    LaunchedEffect(remotePresentationLoopToggleFlow) {
        remotePresentationLoopToggleFlow?.collect {
            presentationViewModel.isLooping = !presentationViewModel.isLooping
            onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(isLooping = presentationViewModel.isLooping)) }
        }
    }
    LaunchedEffect(remotePresentationGotoFlow) {
        remotePresentationGotoFlow?.collect { index ->
            if (index in presentationViewModel.slideFiles.indices) {
                presentationViewModel.selectSlide(index)
            }
        }
    }

    val sceneViewModel = remember { SceneViewModel() }

    val currentOnSongsLoaded by rememberUpdatedState(onSongsLoaded)
    val songsViewModel = remember { SongsViewModel(appSettings, onSongsLoaded = { songs -> currentOnSongsLoaded?.invoke(songs) }) }
    DisposableEffect(Unit) { onDispose { songsViewModel.dispose() } }

    // Mirrors the primary's song catalog while connected via Instance Link — see
    // SongsViewModel.setInstanceLinkSource for the lazy per-song lyric fetch. Only in Controlled
    // mode — a Controller keeps browsing its own local song library and drives the primary instead.
    LaunchedEffect(instanceLinkConnectionStatus, instanceLinkRemoteSongCatalog, instanceLinkRole) {
        songsViewModel.setInstanceLinkSource(
            active = instanceLinkConnectionStatus == InstanceLinkStatus.CONNECTED && instanceLinkRole == InstanceLinkRole.CONTROLLED,
            catalog = instanceLinkRemoteSongCatalog,
            fetchDetail = instanceLinkFetchSongDetail
        )
    }

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
    val currentOnSecondaryBibleFilePathChanged by rememberUpdatedState(instanceLinkOnSecondaryBibleFilePathChanged)
    val bibleViewModel = remember {
        BibleViewModel(
            appSettings,
            onBibleLoaded = { bible, translation -> currentOnBibleLoaded?.invoke(bible, translation) },
            onSecondaryBibleFilePathChanged = { path -> currentOnSecondaryBibleFilePathChanged?.invoke(path) }
        )
    }
    DisposableEffect(Unit) { onDispose { bibleViewModel.dispose() } }

    // Mirrors the primary's bible while connected via Instance Link — see
    // BibleViewModel.setInstanceLinkSource. Only in Controlled mode, same reasoning as Songs above.
    LaunchedEffect(instanceLinkConnectionStatus, instanceLinkBibleSyncMode, instanceLinkRole) {
        bibleViewModel.setInstanceLinkSource(
            active = instanceLinkConnectionStatus == InstanceLinkStatus.CONNECTED && instanceLinkRole == InstanceLinkRole.CONTROLLED,
            mode = instanceLinkBibleSyncMode,
            fetchBibleFile = instanceLinkFetchBibleFile,
            fetchSecondaryBibleFile = instanceLinkFetchSecondaryBibleFile
        )
    }

    // Bible Lookup Engine client — feeds detected scripture into the Bible tab and forwards the
    // reverse-lookup level to the engine.
    val bibleEngineClient = remember {
        BibleEngineClient(onScripture = bibleViewModel::onEngineScripture).also { client ->
            bibleViewModel.onTextMatchLevelChanged = { level -> client.setLevel(level.name.lowercase()) }
        }
    }
    DisposableEffect(Unit) { onDispose { bibleEngineClient.dispose() } }

    // Engine link lifecycle — owned here (not in BibleTab) so it survives tab switches: BibleTab is
    // composed inside AnimatedContent and would otherwise restart the engine every time the operator
    // navigates back to it. Started when STT connects, stopped on disconnect / when disabled.
    val bibleEngineSettings = appSettings.bibleEngineSettings
    // The SET of bibles to index (sorted, blanks removed). Keying the restart on this means swapping
    // primary↔secondary (same set) does NOT re-index, while changing to a different bible does.
    val engineBibles = remember(appSettings.bibleSettings.primaryBible, appSettings.bibleSettings.secondaryBible) {
        listOf(appSettings.bibleSettings.primaryBible, appSettings.bibleSettings.secondaryBible)
            .filter { it.isNotBlank() }
            .sorted()
    }
    LaunchedEffect(
        sttConnected, bibleEngineSettings.enabled, bibleEngineSettings.runLocal,
        bibleEngineSettings.host, bibleEngineSettings.port, engineBibles,
    ) {
        if (sttConnected && bibleEngineSettings.enabled && engineBibles.isNotEmpty()) {
            bibleEngineClient.start(
                sttUrl = appSettings.sttSettings.serverUrl,
                bibleRoot = appSettings.bibleSettings.storageDirectory,
                bibleFiles = engineBibles,
                runLocal = bibleEngineSettings.runLocal,
                host = bibleEngineSettings.host,
                port = bibleEngineSettings.port,
                level = bibleViewModel.textMatchLevel.value.name.lowercase(),
            )
        } else {
            bibleEngineClient.stop()
            bibleViewModel.clearDetectedReferences()
        }
    }

    val dictionaryViewModel = remember { DictionaryViewModel() }
    DisposableEffect(Unit) { onDispose { dictionaryViewModel.dispose() } }
    LaunchedEffect(appSettings.bibleSettings.storageDirectory) {
        dictionaryViewModel.loadAvailableBibles(appSettings.bibleSettings.storageDirectory)
    }

    // ScheduleViewModel is hoisted here (outside AnimatedVisibility) so that collapsing/
    // expanding the schedule panel does NOT destroy the schedule items.
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)
    val scheduleViewModel = remember { ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) }) }
    DisposableEffect(Unit) { onDispose { scheduleViewModel.dispose() } }

    // Mirrors the primary's schedule while connected via Instance Link, handing local editing
    // back to the operator on disconnect (see ScheduleViewModel.applyRemoteSchedule/stopFollowingRemote).
    // Only in Controlled mode — a Controller keeps its own local schedule, same reasoning as above.
    LaunchedEffect(instanceLinkConnectionStatus, instanceLinkRemoteSchedule, instanceLinkRole) {
        if (instanceLinkConnectionStatus == InstanceLinkStatus.CONNECTED && instanceLinkRole == InstanceLinkRole.CONTROLLED) {
            scheduleViewModel.applyRemoteSchedule(instanceLinkRemoteSchedule)
        } else {
            scheduleViewModel.stopFollowingRemote()
        }
    }
    LaunchedEffect(instanceLinkSendAddToSchedule) {
        scheduleViewModel.onPushToRemoteSchedule = instanceLinkSendAddToSchedule
    }

    val presentingMode by presenterManager.presentingMode

    // Keep the Stage Monitor's "Next" verse in sync with whatever is currently selected —
    // recomputes automatically whenever the underlying Bible selection changes, from any source
    // (manual click, auto-follow, remote API), since nextVerses is a derived state.
    val nextVerses by bibleViewModel.nextVerses
    LaunchedEffect(nextVerses) {
        presenterManager.setNextVerses(nextVerses)
    }

    // When Bible is live and the user is on a different tab, keep the presenter in sync with
    // new auto-follow detections. BibleTab is inside AnimatedContent and leaves the composition
    // on tab switch, so its own LaunchedEffect can't fire while the user is away.
    val autoFollowLiveToken by bibleViewModel.autoFollowLiveToken
    val mainAutoFollowTokenGate = rememberTokenGate(autoFollowLiveToken)
    LaunchedEffect(autoFollowLiveToken) {
        if (!mainAutoFollowTokenGate.consume()) return@LaunchedEffect
        // Defer to BibleTab's own handler (history, stats, training log) when it's active.
        if (effectiveTabIndex == visibleTabs.indexOf(Tabs.BIBLE)) return@LaunchedEffect
        // Don't steal the screen — only update verse content when Bible is already presenting.
        if (presentingMode != Presenting.BIBLE) return@LaunchedEffect
        val verses = bibleViewModel.getSelectedVerses()
        if (verses.isNotEmpty()) {
            onVerseSelected(verses)
            val primary = verses.first()
            val bookNum = bibleViewModel.canonicalBookIdForDisplayIndex(bibleViewModel.selectedBookIndex.value)
            TrainingDataLogger.logLiveReference(
                book       = bookNum,
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "auto",
                segmentId  = bibleViewModel.lastDetectionSegmentId,
                autoFollow = true,
                matchType  = bibleViewModel.autoFollowLiveMatchType.value,
            )
        }
    }

    val mainFocusRequester = remember { FocusRequester() }

    val konamiSequence = remember {
        listOf(
            Key.DirectionUp, Key.DirectionUp,
            Key.DirectionDown, Key.DirectionDown,
            Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionLeft, Key.DirectionRight,
            Key.B, Key.A
        )
    }
    var konamiProgress by remember { mutableStateOf(0) }
    var showKonamiEasterEgg by remember { mutableStateOf(false) }

    val crosswordSequence = remember {
        listOf(
            Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionLeft, Key.DirectionRight
        )
    }
    var crosswordProgress by remember { mutableStateOf(0) }

    // Notify server whenever the picture folder, image list, or image order changes
    val pictureImages = picturesViewModel.images
    val pictureFolder = picturesViewModel.selectedFolder
    val pictureOrderVersion = picturesViewModel.imageOrderVersion
    LaunchedEffect(pictureFolder, pictureImages.size, pictureOrderVersion) {
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
            if (index in presentationViewModel.slideFiles.indices) {
                presentationViewModel.selectSlide(index)
                val bitmap = presentationViewModel.slideFiles.getOrNull(index)?.let { f ->
                    withContext(Dispatchers.IO) {
                        org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                    }
                }
                val nextBitmap = presentationViewModel.slideFiles.getOrNull(index + 1)?.let { f ->
                    withContext(Dispatchers.IO) {
                        org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                    }
                }
                presenterManager.setSelectedSlide(bitmap)
                presenterManager.setNextSlide(nextBitmap)
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
            if (bookIndex >= 0) {
                // Capture the full span the client asked for: parse req.verseRange ("1-3", "2,4,5")
                // and take its max as the end, rather than hardcoding null (which dropped the range).
                val rangeNums = req.verseRange
                    .takeIf { it.isNotBlank() }
                    ?.split(",", "-")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.takeIf { it.isNotEmpty() }
                val verseEnd = rangeNums?.max()?.takeIf { it > req.verseNumber }
                TrainingDataLogger.logLiveReference(
                    book       = bibleViewModel.canonicalBookIdForDisplayIndex(bookIndex),
                    chapter    = req.chapter,
                    verseStart = req.verseNumber,
                    verseEnd   = verseEnd,
                    source     = "remote",
                    segmentId  = bibleViewModel.lastDetectionSegmentId,
                    autoFollow = bibleViewModel.autoFollowEnabled.value
                )
            }
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
            RecentPresentationFiles.add(file.absolutePath)
            // Switch to the Presentations tab so the user can see the newly loaded file
            selectTab(Tabs.PRESENTATION)
        }
    }

    LaunchedEffect(selectedTabIndex) {
        onTabChange(selectedTabIndex)
        visibleTabs.getOrNull(effectiveTabIndex)?.name?.let { tabName ->
            CrashReporter.setTag("active_tab", tabName)
            CrashReporter.breadcrumb("Tab: $tabName", category = "navigation")
        }
        // Re-request focus so F-key shortcuts keep working after the new tab's children steal focus
        mainFocusRequester.requestFocus()
    }
    // Restore focus whenever a dialog closes (DialogWindow steals OS focus; without this, arrow
    // keys and other shortcuts stop working until the user clicks back on the main window).
    LaunchedEffect(dialogDismissSignal) {
        if (dialogDismissSignal > 0) mainFocusRequester.requestFocus()
    }

    // One-time startup snapshot of the configuration as searchable Sentry tags, so errors can
    // be filtered by setup (screen/output count, integrations, VLC availability). Off the main
    // thread because the VLC probe can block.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val screenCount = try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
            } catch (_: Exception) { 0 }
            CrashReporter.setConfigTags(mapOf(
                "vlc.available" to isVlcAvailable.toString(),
                "screen.count" to screenCount.toString(),
                "output.count" to appSettings.projectionSettings.screenAssignments.size.toString(),
                "atem.enabled" to appSettings.atemSettings.host.isNotBlank().toString(),
                "obs.enabled" to appSettings.obsSettings.enabled.toString(),
                "server.enabled" to appSettings.serverSettings.enabled.toString()
            ))
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
                    when {
                        keyEvent.key == Key.Z && keyEvent.isCtrlPressed && keyEvent.isShiftPressed -> {
                            scheduleViewModel.redo(); true
                        }
                        keyEvent.key == Key.Z && keyEvent.isCtrlPressed -> {
                            scheduleViewModel.undo(); true
                        }
                        keyEvent.key == Key.Escape -> {
                            mediaViewModel?.pause()
                            presenterManager.requestClearDisplay()
                            // Also release any "Send to Stage Monitor" lock (e.g. from Announcements)
                            // so the stage monitor goes back to following the main presenting mode.
                            appSettings.projectionSettings.screenAssignments.indices
                                .filter { appSettings.projectionSettings.screenAssignments[it].displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR }
                                .forEach { presenterManager.setScreenLock(it, null) }
                            true
                        }
                        keyEvent.key == Key.F6 -> { selectTab(Tabs.BIBLE); true }
                        keyEvent.key == Key.F7 -> { selectTab(Tabs.SONGS); true }
                        keyEvent.key == Key.F8 -> { selectTab(Tabs.PICTURES); true }
                        keyEvent.key == Key.F9 -> { selectTab(Tabs.PRESENTATION); true }
                        keyEvent.key == Key.F10 -> { selectTab(Tabs.MEDIA); true }
                        keyEvent.key == Key.F11 -> { selectTab(Tabs.LOWER_THIRD); true }
                        keyEvent.key == Key.F12 -> { selectTab(Tabs.ANNOUNCEMENTS); true }
                        else -> {
                            if (presentingMode != Presenting.NONE) {
                                // Suppress both easter egg sequences while live
                                konamiProgress = 0
                                crosswordProgress = 0
                                false
                            } else {
                                // Konami code: ↑↑↓↓←→←→BA
                                val konamiExpected = konamiSequence.getOrNull(konamiProgress)
                                if (keyEvent.key == konamiExpected) {
                                    konamiProgress++
                                    if (konamiProgress == konamiSequence.size) {
                                        konamiProgress = 0
                                        showKonamiEasterEgg = true
                                    }
                                } else {
                                    konamiProgress = if (keyEvent.key == konamiSequence[0]) 1 else 0
                                }

                                // Crossword: ←→←→
                                val crosswordExpected = crosswordSequence.getOrNull(crosswordProgress)
                                if (keyEvent.key == crosswordExpected) {
                                    crosswordProgress++
                                    if (crosswordProgress == crosswordSequence.size) {
                                        crosswordProgress = 0
                                        showCrosswordTab = true
                                        selectTab(Tabs.CROSSWORD)
                                    }
                                } else {
                                    crosswordProgress = if (keyEvent.key == crosswordSequence[0]) 1 else 0
                                }

                                false
                            }
                        }
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

            val windowState = LocalMainWindowState.current
            val isMaximized = windowState?.placement != WindowPlacement.Floating
            val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

            var scheduleCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.schedulePanelCollapsed) }

            // Schedule panel width — loaded from settings, local state for smooth dragging
            var schedulePanelPx by remember(currentLayout.schedulePanelWidthDp, isMaximized) {
                mutableStateOf(with(density) { currentLayout.schedulePanelWidthDp.dp.toPx() })
            }
            var previewCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.previewPanelCollapsed) }
            var previewPanelPx by remember(currentLayout.previewPanelWidthDp, isMaximized) {
                mutableStateOf(with(density) { currentLayout.previewPanelWidthDp.dp.toPx() })
            }

            fun saveScheduleWidth() {
                val widthDp = with(density) { schedulePanelPx.toDp().value.toInt() }
                onSettingsChangeState.value { s ->
                    if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(schedulePanelWidthDp = widthDp))
                    else s.copy(windowedLayout = s.windowedLayout.copy(schedulePanelWidthDp = widthDp))
                }
            }

            fun savePreviewWidth() {
                val widthDp = with(density) { previewPanelPx.toDp().value.toInt() }
                onSettingsChangeState.value { s ->
                    if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(previewPanelWidthDp = widthDp))
                    else s.copy(windowedLayout = s.windowedLayout.copy(previewPanelWidthDp = widthDp))
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Keep the resize handles on screen at any window size (e.g. when the
                // window is snapped to a half/quadrant): cap each side panel's width so
                // both 16dp handles plus a minimum slice of main content always fit.
                // Render/drag clamp only — saved widths are untouched and restore when
                // the window grows again.
                val availablePx = constraints.maxWidth.toFloat()
                val reservePx = with(density) { (16 + 16 + 200).dp.toPx() } // 2 handles + min main
                val absMaxPx = with(density) { 600.dp.toPx() }
                fun panelCapPx(otherPanelPx: Float) =
                    (availablePx - otherPanelPx - reservePx).coerceIn(0f, absMaxPx)

                val maxSchedulePx = panelCapPx(if (previewCollapsed) 0f else previewPanelPx)
                val maxPreviewPx = panelCapPx(if (scheduleCollapsed) 0f else schedulePanelPx)

            Row(modifier = Modifier.fillMaxSize()) {
                // Collapsible schedule panel
                AnimatedVisibility(
                    visible = !scheduleCollapsed,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    Column(modifier = Modifier.width(with(density) { schedulePanelPx.coerceAtMost(maxSchedulePx).toDp() }).fillMaxHeight()) {
                    // Shown once a host has ever been configured — not just while actively connected —
                    // so the operator can always see the last-known status and reconnect/disconnect
                    // without reopening the Connect dialog.
                    if (instanceLinkFollowingHost.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ConnectionStatusRow(
                                status = instanceLinkConnectionStatus,
                                connectedLabel = if (instanceLinkRole == InstanceLinkRole.CONTROLLER)
                                    stringResource(Res.string.instance_link_controlling_host, instanceLinkFollowingHost)
                                else
                                    stringResource(Res.string.instance_link_following_host, instanceLinkFollowingHost)
                            )
                            if (instanceLinkConnectionStatus == InstanceLinkStatus.CONNECTED ||
                                instanceLinkConnectionStatus == InstanceLinkStatus.CONNECTING
                            ) {
                                TextButton(onClick = onInstanceLinkDisconnect) {
                                    Text(stringResource(Res.string.menu_disconnect), style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                TextButton(onClick = onInstanceLinkConnect) {
                                    Text(stringResource(Res.string.connect), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (connectedInstanceLinkFollowerCount > 0) {
                        ConnectionStatusRow(
                            status = InstanceLinkStatus.CONNECTED,
                            connectedLabel = stringResource(Res.string.instance_link_primary_badge, connectedInstanceLinkFollowerCount),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                    ScheduleTab(
                        scheduleViewModel = scheduleViewModel,
                        onPresenting = presenting,
                        onAddLabel = { showAddLabelDialog = true },
                        onAddWebsite = { showAddWebsiteDialog = true },

                        onPresentBible = { item ->
                            selectTab(Tabs.BIBLE)
                            selectedBibleVerseItem = item
                            presenting(Presenting.BIBLE)
                        },
                        onPresentSong = { item ->
                            selectTab(Tabs.SONGS)
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
                                songId = item.songId,
                                songNumber = item.songNumber,
                                title = item.title,
                                songbook = item.songbook
                            )
                            presenting(Presenting.LYRICS)
                        },
                        onPresentPresentation = { item ->
                            selectTab(Tabs.PRESENTATION)
                            selectedPresentationItem = item
                            presenting(Presenting.PRESENTATION)
                        },
                        onPresentPictures = { item ->
                            selectedPictureItem = item
                            selectTab(Tabs.PICTURES)
                            presenting(Presenting.PICTURES)
                        },
                        onPresentMedia = { item ->
                            selectTab(Tabs.MEDIA)
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
                                        timerExpiredText    = item.timerExpiredText,
                                        timerMode           = item.timerMode,
                                        targetHour          = item.targetHour,
                                        targetMinute        = item.targetMinute,
                                        targetSecond        = item.targetSecond
                                    )
                                )
                            }
                            if (item.isTimer) {
                                scheduleTimerVersion++
                            } else {
                                presenterManager.setAnnouncementText(item.text)
                            }
                            presenting(Presenting.ANNOUNCEMENTS)
                        },
                        onPresentLowerThird = { item ->
                            val lottieFolder = java.io.File(appSettings.streamingSettings.lowerThirdFolder)
                            val lottieFile = lottieFolder.listFiles()?.find {
                                it.nameWithoutExtension == item.presetLabel || it.nameWithoutExtension == item.presetId
                            }
                            if (lottieFile != null && lottieFile.exists()) {
                                val json = lottieFile.readText()
                                presenterManager.setLottieContent(json, item.pauseAtFrame, -1f, item.pauseDurationMs, lottieFile.nameWithoutExtension)
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
                        onPresentDictionary = { item ->
                            presenterManager.setAnnouncementText("${item.word} (${item.transliteration})\n\n${item.definition}")
                            presenterManager.setShowPresenterWindow(true)
                            presenting(Presenting.ANNOUNCEMENTS)
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
                                                timerExpiredText    = item.timerExpiredText,
                                                timerMode           = item.timerMode,
                                                targetHour          = item.targetHour,
                                                targetMinute        = item.targetMinute,
                                                targetSecond        = item.targetSecond
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

                                is ScheduleItem.DictionaryItem -> {
                                    selectTab(Tabs.DICTIONARY)
                                    dictionaryViewModel.selectByNumber(item.number)
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
                                    addScene = actions.addScene,
                                    addDictionary = actions.addDictionary
                                )
                            )
                        },
                        onSelectedItemChanged = { id ->
                            onScheduleItemSelected(id)
                        },
                        onScheduleChanged = onScheduleChanged
                    )
                    } // end Box (ScheduleTab weight)
                    val leftSidebarConnections = appSettings.companionSatelliteConnections.filter { it.showInLeftSidebar && it.host.isNotBlank() }
                    if (leftSidebarConnections.isNotEmpty()) {
                        HorizontalDivider()
                        var selectedLeftSidebarId by remember(leftSidebarConnections.map { it.id }) {
                            mutableStateOf(leftSidebarConnections.firstOrNull()?.id)
                        }
                        LaunchedEffect(leftSidebarConnections.map { it.id }) {
                            if (leftSidebarConnections.none { it.id == selectedLeftSidebarId }) {
                                selectedLeftSidebarId = leftSidebarConnections.firstOrNull()?.id
                            }
                        }
                        val selectedLeftSidebarConnection = leftSidebarConnections.find { it.id == selectedLeftSidebarId }
                        // No weight here — this panel sizes itself to exactly what its configured
                        // grid needs (sizeToContent), so the ScheduleTab above (weight(1f)) gets all
                        // the remaining space instead of being forced into a fixed 50/50 split.
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            if (leftSidebarConnections.size > 1) {
                                CompanionConnectionChipRow(
                                    connections = leftSidebarConnections,
                                    selectedId = selectedLeftSidebarId,
                                    onSelect = { selectedLeftSidebarId = it }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (selectedLeftSidebarConnection != null) {
                                CompanionSurfacePanel(
                                    connection = selectedLeftSidebarConnection,
                                    placement = CompanionSurfacePlacement.LEFT_SIDEBAR,
                                    viewModel = companionSatelliteViewModel,
                                    modifier = Modifier.fillMaxWidth(),
                                    sizeToContent = true
                                )
                            }
                        }
                    }
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
                                        minOf(with(density) { 160.dp.toPx() }, maxSchedulePx),
                                        maxSchedulePx
                                    )
                                }
                            }
                        }
                        .pointerHoverIcon(
                            if (scheduleCollapsed) PointerIcon.Default
                            else PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!scheduleCollapsed) {
                        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { repeat(3) { Box(Modifier.size(3.dp).background(dotColor, CircleShape)) } }
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { repeat(3) { Box(Modifier.size(3.dp).background(dotColor, CircleShape)) } }
                    }
                    IconButton(
                        onClick = {
                            scheduleCollapsed = !scheduleCollapsed
                            onSettingsChangeState.value { s ->
                                if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(schedulePanelCollapsed = scheduleCollapsed))
                                else s.copy(windowedLayout = s.windowedLayout.copy(schedulePanelCollapsed = scheduleCollapsed))
                            }
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
                                val visibleCount = Tabs.entries.count { it != Tabs.CROSSWORD && it.name !in appSettings.hiddenTabs }
                                Tabs.entries.filter { it != Tabs.CROSSWORD }.forEach { tab ->
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
                            painter = rememberVectorPainter(Icons.Default.Wallpaper),
                            text = stringResource(Res.string.background),
                            onClick = onShowBackgroundSettings,
                            buttonSize = 36.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface
                        )
                        TooltipIconButton(
                            painter = painterResource(Res.drawable.ic_settings),
                            text = stringResource(Res.string.tooltip_settings),
                            onClick = onShowSettings,
                            buttonSize = 36.dp,
                            iconTint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = "tab_content"
                    ) { tab ->
                        when (tab) {
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
                                onInstanceLinkSendVerse = instanceLinkSendVerse,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.BIBLE,
                                presenterManager = presenterManager,
                                statisticsManager = statisticsManager,
                                dialogDismissSignal = dialogDismissSignal,
                                sttManager = sttManager,
                                bibleEngineClient = bibleEngineClient
                            )

                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = songsViewModel,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { songNumber, title, songbook, songId ->
                                    currentScheduleActions.addSong(songNumber, title, songbook, songId)
                                },
                                onInstanceLinkSendProject = instanceLinkSendProject,
                                onInstanceLinkSendSongSection = instanceLinkSendSongSection,
                                selectedSongItem = selectedSongItem,
                                selectedSongItemVersion = selectedSongItemVersion,
                                onSongItemSelected = onSongItemSelected,
                                onAllSectionsChanged = onAllSectionsChanged,
                                onSectionIndexChanged = onSectionIndexChanged,
                                onLineIndexChanged = onLineIndexChanged,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.LYRICS,
                                theme = theme,
                                statisticsManager = statisticsManager,
                                dialogDismissSignal = dialogDismissSignal
                            )

                            Tabs.PICTURES -> PicturesTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { folderPath, folderName, imageCount ->
                                    currentScheduleActions.addPicture(folderPath, folderName, imageCount)
                                },
                                onInstanceLinkSendProject = instanceLinkSendProject,
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
                                onInstanceLinkSendProject = instanceLinkSendProject,
                                selectedPresentationItem = selectedPresentationItem,
                                presenterManager = presenterManager,
                                onSlidesLoaded = onPresentationSlidesLoaded,
                                onSettingsChange = onSettingsChange,
                                viewModel = presentationViewModel,
                                tunnelStatus = tunnelStatus,
                                tunnelUrl = tunnelUrl,
                                serverUrl = serverUrl,
                                presentationDisplayUrl = presentationDisplayUrl,
                                onPresentationDisplayUrlChanged = onPresentationDisplayUrlChanged,
                                onStartTunnel = onStartTunnel,
                                onStopTunnel = onStopTunnel,
                                presentationFrozen = presentationFrozen,
                                onFreezeToggle = onFreezeToggle,
                                onClearPresentation = onClearPresentation
                            )

                            Tabs.MEDIA -> MediaTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onAddToSchedule = { mediaUrl, mediaTitle, mediaType ->
                                    currentScheduleActions.addMedia(mediaUrl, mediaTitle, mediaType)
                                },
                                selectedMediaItem = selectedMediaItem,
                                presenterManager = presenterManager,
                                instanceLinkMediaStreamUrl = instanceLinkMediaStreamUrl,
                                onInstanceLinkSendProject = instanceLinkSendProject
                            )

                            Tabs.LOWER_THIRD -> LowerThirdTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                selectedLowerThirdItem = selectedLowerThirdItem,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { presetId, presetLabel, pauseAtFrame, pauseDurationMs ->
                                    scheduleActions.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs)
                                },
                                onGoLive = { json, pauseAtFrame, pauseFrame, pauseDurationMs, presetName ->
                                    presenterManager.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs, presetName)
                                    presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                    presenterManager.setShowPresenterWindow(true)
                                },
                                onOpenLottieGen = { outputDir, onSaved -> onOpenLottieGen(outputDir, onSaved) }
                            )

                            Tabs.ANNOUNCEMENTS -> AnnouncementsTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                presenterManager = presenterManager,
                                scheduleTimerVersion = scheduleTimerVersion,
                                onAddToSchedule = { settings ->
                                    val isTimer = settings.timerMode == "clock" ||
                                        settings.timerHours > 0 || settings.timerMinutes > 0 || settings.timerSeconds > 0
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
                                        settings.timerExpiredText,
                                        settings.timerMode,
                                        settings.targetHour,
                                        settings.targetMinute,
                                        settings.targetSecond
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
                                onSettingsChange = onSettingsChange,
                                presenterManager = presenterManager,
                                sceneViewModel = sceneViewModel,
                                onAddToSchedule = { sceneId, sceneName ->
                                    currentScheduleActions.addScene(sceneId, sceneName)
                                },
                                dialogDismissSignal = dialogDismissSignal
                            )

                            Tabs.QA -> if (qaManager != null) {
                                QATab(
                                    modifier = Modifier.fillMaxSize(),
                                    qaManager = qaManager,
                                    presenterManager = presenterManager,
                                    serverUrl = serverUrl,
                                    presenting = presenting,
                                    appSettings = appSettings,
                                    onSettingsChange = onSettingsChange,
                                    tunnelStatus = tunnelStatus,
                                    tunnelUrl = tunnelUrl,
                                    onStartTunnel = onStartTunnel,
                                    onStopTunnel = onStopTunnel,
                                    qaDisplayUrl = qaDisplayUrl,
                                    onQaDisplayUrlChanged = onQaDisplayUrlChanged,
                                )
                            }

                            Tabs.STT -> if (sttManager != null) {
                                STTTab(
                                    modifier = Modifier.fillMaxSize(),
                                    sttManager = sttManager,
                                    presenterManager = presenterManager,
                                    presenting = presenting,
                                    appSettings = appSettings,
                                    onSettingsChange = onSettingsChange
                                )
                            }

                            Tabs.CROSSWORD -> CrosswordTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange
                            )

                            Tabs.COMPANION_SURFACE -> CompanionSurfaceTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                viewModel = companionSatelliteViewModel
                            )

                            Tabs.DICTIONARY -> DictionaryTab(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = dictionaryViewModel,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { number, word, transliteration, definition ->
                                    scheduleActions.addDictionary(number, word, transliteration, definition)
                                },
                                onGoLive = { entry ->
                                    presenterManager.setDisplayedDictionaryEntry(entry)
                                    presenterManager.setShowPresenterWindow(true)
                                    presenting(Presenting.DICTIONARY)
                                },
                                getVerseText = { bookId, chapter, verse ->
                                    val bible = dictionaryViewModel.dictBible ?: bibleViewModel.primaryBible.value
                                    bible?.getVerseDetails(bookId, chapter, verse)?.second
                                },
                                getBookName = { bookId ->
                                    val bible = dictionaryViewModel.dictBible ?: bibleViewModel.primaryBible.value
                                    bible?.getBookName(bookId)
                                },
                                onWordClick = { strongsNumber ->
                                    dictionaryViewModel.selectByNumber(strongsNumber)
                                },
                                onVerseClick = { bookId, chapter, verseNumber ->
                                    selectTab(Tabs.BIBLE)
                                    bibleViewModel.selectVerseByBookId(bookId, chapter, verseNumber)
                                },
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
                        .pointerInput(previewCollapsed, appSettings.previewPanelWidthDp) {
                            if (!previewCollapsed) {
                                detectHorizontalDragGestures(
                                    onDragEnd = ::savePreviewWidth
                                ) { _, amount ->
                                    // Invert drag direction: dragging left increases width
                                    previewPanelPx = (previewPanelPx - amount).coerceIn(
                                        minOf(with(density) { 150.dp.toPx() }, maxPreviewPx),
                                        maxPreviewPx
                                    )
                                }
                            }
                        }
                        .pointerHoverIcon(
                            if (previewCollapsed) PointerIcon.Default
                            else PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!previewCollapsed) {
                        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { repeat(3) { Box(Modifier.size(3.dp).background(dotColor, CircleShape)) } }
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) { repeat(3) { Box(Modifier.size(3.dp).background(dotColor, CircleShape)) } }
                    }
                    IconButton(
                        onClick = {
                            previewCollapsed = !previewCollapsed
                            onSettingsChangeState.value { s ->
                                if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(previewPanelCollapsed = previewCollapsed))
                                else s.copy(windowedLayout = s.windowedLayout.copy(previewPanelCollapsed = previewCollapsed))
                            }
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
                            .width(with(density) { previewPanelPx.coerceAtMost(maxPreviewPx).toDp() })
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TooltipIconButton(
                                painter = rememberVectorPainter(Icons.Default.Monitor),
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
                            appSettings = livePreviewAppSettings,
                            modifier = Modifier.fillMaxWidth(),
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            sttManager = sttManager,
                        )
                        val rightSidebarConnections = appSettings.companionSatelliteConnections.filter { it.showInRightSidebar && it.host.isNotBlank() }
                        if (rightSidebarConnections.isNotEmpty()) {
                            // Pushes everything below (divider + panel) down to the bottom of this
                            // fillMaxHeight column instead of sitting right under the live preview
                            // with empty space left below it.
                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            var selectedRightSidebarId by remember(rightSidebarConnections.map { it.id }) {
                                mutableStateOf(rightSidebarConnections.firstOrNull()?.id)
                            }
                            LaunchedEffect(rightSidebarConnections.map { it.id }) {
                                if (rightSidebarConnections.none { it.id == selectedRightSidebarId }) {
                                    selectedRightSidebarId = rightSidebarConnections.firstOrNull()?.id
                                }
                            }
                            val selectedRightSidebarConnection = rightSidebarConnections.find { it.id == selectedRightSidebarId }
                            // No weight here — sizeToContent sizes this panel to exactly what its
                            // configured grid needs rather than stretching to fill all remaining
                            // space below the (fixed-size) live preview above it.
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                if (rightSidebarConnections.size > 1) {
                                    CompanionConnectionChipRow(
                                        connections = rightSidebarConnections,
                                        selectedId = selectedRightSidebarId,
                                        onSelect = { selectedRightSidebarId = it }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (selectedRightSidebarConnection != null) {
                                    CompanionSurfacePanel(
                                        connection = selectedRightSidebarConnection,
                                        placement = CompanionSurfacePlacement.RIGHT_SIDEBAR,
                                        viewModel = companionSatelliteViewModel,
                                        modifier = Modifier.fillMaxWidth(),
                                        sizeToContent = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // end BoxWithConstraints
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

    KonamiEasterEggDialog(
        isVisible = showKonamiEasterEgg,
        onDismiss = { showKonamiEasterEgg = false },
        theme = theme
    )

    // Invite feedback on the launch after an unexpected shutdown (opt-in analytics only).
    var showCrashFeedback by remember {
        mutableStateOf(CrashReporter.didCrashLastRun && appSettings.analyticsReportingEnabled)
    }
    if (showCrashFeedback) {
        CrashFeedbackDialog(
            onDismiss = { showCrashFeedback = false },
            onSend = { comment, email ->
                CrashReporter.sendUserFeedback(comment, email = email)
                showCrashFeedback = false
            }
        )
    }
}