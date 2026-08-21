package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import java.awt.GraphicsEnvironment
import java.awt.Window as AwtWindow
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
import churchpresenter.composeapp.generated.resources.instance_link_status_reconnecting_in
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
import churchpresenter.composeapp.generated.resources.timer_expired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.churchpresenter.app.churchpresenter.composables.ConnectionStatusRow
import org.churchpresenter.app.churchpresenter.composables.PanelResizeHandle
import org.churchpresenter.app.churchpresenter.composables.resizedPanelWidth
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
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.app.churchpresenter.dialogs.AddWebsiteDialog
import org.churchpresenter.app.churchpresenter.dialogs.CrashFeedbackDialog
import org.churchpresenter.app.churchpresenter.dialogs.KonamiEasterEggDialog
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.Scene
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
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel

import java.io.File
import org.churchpresenter.app.churchpresenter.viewmodel.clearDetectedReferences
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.invalidateInstanceLinkBibleCache
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference
import org.churchpresenter.app.churchpresenter.viewmodel.onEngineScripture
import org.churchpresenter.app.churchpresenter.viewmodel.onEngineVersion
import org.churchpresenter.app.churchpresenter.viewmodel.setInstanceLinkSource

private const val PANEL_COLLAPSE_ANIM_MS = 220
private const val CLOCK_TICK_MS = 1000L
private const val CONTENT_CROSSFADE_MS = 120

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    // The hosting AWT window — lets tabs force window focus back when AWT's focus
    // tracking wedges (see PresentationTab's focus-lost rescue banner).
    hostWindow: AwtWindow? = null,
    appSettings: AppSettings,
    // Same as appSettings except backgroundSettings may be swapped for a mirrored-from-primary copy
    // (Instance Link) — used ONLY at the live-preview render call site below, never for editing/
    // persistence, so the Options dialog still shows this instance's own local background settings.
    livePreviewAppSettings: AppSettings = appSettings,
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager? = null,
    verseSequenceLog: VerseSequenceLog? = null,
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
    /** This instance's saved Canvas scenes, re-published whenever they change — the InstanceLink
     *  follower path resolves a mirrored CANVAS live state by scene id against this list. */
    onScenesChanged: ((List<Scene>) -> Unit)? = null,
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
    /** Same backfill mechanism as [remoteSelectSongFlow] — a remote PROJECT go-live for a picture
     *  folder/presentation only adds it to the schedule and flips presentingMode; these drive this
     *  composable to actually load the real content into the corresponding ViewModel. */
    remoteSelectPictureFlow: Flow<ScheduleItem.PictureItem>? = null,
    remoteSelectPresentationFlow: Flow<ScheduleItem.PresentationItem>? = null,
    /** Instance Link Controller-mode navigation — advance/retreat whatever the primary currently has
     *  live (no id needed, see Constants.WS_CMD_NEXT_PICTURE and siblings). Received on the primary
     *  side; sent from the Controller side via [instanceLinkSendNextPicture] and siblings below. */
    nextPictureFlow: Flow<Unit>? = null,
    previousPictureFlow: Flow<Unit>? = null,
    nextSlideFlow: Flow<Unit>? = null,
    previousSlideFlow: Flow<Unit>? = null,
    /** Emits a presentation [File] uploaded by a mobile client — loaded into [PresentationViewModel] automatically. */
    uploadPresentationFlow: Flow<File>? = null,
    serverUrl: String = "",
    /** Persistent "Following <host>" badge shown above the Schedule panel while connected via Instance Link. */
    instanceLinkConnectionStatus: InstanceLinkStatus = InstanceLinkStatus.DISCONNECTED,
    instanceLinkFollowingHost: String = "",
    /** Absolute wall-clock ms of the next reconnect attempt while status is ERROR, else null. */
    instanceLinkNextRetryAtMs: Long? = null,
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
    /** Bumped when the primary announces its bible/secondary-bible changed — triggers cache
     *  invalidation + re-download in the bible mirror effect below. */
    instanceLinkBibleUpdatedSignal: Int = 0,
    instanceLinkSecondaryBibleUpdatedSignal: Int = 0,
    /** How the Bible tab tracks the primary while connected — see BibleSyncMode. */
    instanceLinkBibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    instanceLinkFetchSecondaryBibleFile: (suspend () -> ByteArray?)? = null,
    instanceLinkFetchBibleTranslations: (suspend () -> List<Pair<String, ByteArray>>)? = null,
    /** Reports the secondary bible's local file path to CompanionServer (for GET /api/bible/file/secondary). */
    instanceLinkOnSecondaryBibleFilePathChanged: ((filePath: String) -> Unit)? = null,
    instanceLinkOnBibleFilePathsChanged: ((filePaths: List<String>) -> Unit)? = null,
    /** Non-null while connected via Instance Link — see MediaTab's instanceLinkMediaStreamUrl. */
    instanceLinkMediaStreamUrl: ((itemId: String) -> String)? = null,
    /** Non-null only when connected AND the operator has enabled pushing items to the primary's
     *  schedule — see ScheduleViewModel.onPushToRemoteSchedule. */
    instanceLinkSendAddToSchedule: ((ScheduleItem) -> Unit)? = null,
    /** Non-null only when connected AND the operator has enabled pushing items to the primary's
     *  schedule (same gate as [instanceLinkSendAddToSchedule]) — see
     *  ScheduleViewModel.onRemoveFromRemoteSchedule. */
    instanceLinkSendRemoveFromSchedule: ((id: String) -> Unit)? = null,
    /** See InstanceLinkRole — CONTROLLED (default, mirror the primary) or CONTROLLER (drive it). */
    instanceLinkRole: InstanceLinkRole = InstanceLinkRole.CONTROLLED,
    /** Controller mode "go live with a new item" — approval-gated the first time on the primary,
     *  instant afterwards. Non-null only when connected AND in Controller mode. */
    instanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Controller mode instant Bible verse display — non-null only when connected AND controlling. */
    instanceLinkSendVerse: ((bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit)? = null,
    /** Controller mode instant song-section navigation (within an already-live song) — non-null only
     *  when connected AND controlling. */
    instanceLinkSendSongSection: ((number: String, section: Int, lineIndex: Int) -> Unit)? = null,
    /** Controller mode instant clear — non-null only when connected AND controlling. */
    instanceLinkSendClear: (() -> Unit)? = null,
    /** Controller mode instant Bible Hold toggle — non-null only when connected AND controlling. */
    instanceLinkSendBibleHold: ((Boolean) -> Unit)? = null,
    /** Controller mode next/previous navigation for whatever the primary currently has live —
     *  non-null only when connected AND controlling. See [nextPictureFlow] and siblings for the
     *  primary-side receive. */
    instanceLinkSendNextPicture: (() -> Unit)? = null,
    instanceLinkSendPreviousPicture: (() -> Unit)? = null,
    instanceLinkSendNextSlide: (() -> Unit)? = null,
    instanceLinkSendPreviousSlide: (() -> Unit)? = null,
    /** Fetches remote bytes for a mirrored Picture/Presentation schedule item whose local path
     *  doesn't resolve on this machine (network/shared-drive mismatch) — non-null only while
     *  connected via Instance Link. See PicturesTab/PresentationTab's fallback in their
     *  selectedPictureItem/selectedPresentationItem effects. */
    instanceLinkFetchPictureImageBytes: (suspend (folderId: String, index: Int) -> ByteArray?)? = null,
    instanceLinkFetchPresentationSlideBytes: (suspend (id: String, index: Int) -> ByteArray?)? = null,
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
    // Secret keypress unlock — invoked after D is pressed seven times in a row, revealing
    // the Developer menu in packaged builds for this session (see onPreviewKeyEvent below).
    onRequestDeveloperMenuUnlock: () -> Unit = {},
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
    // Bumped on every click, like selectedSongItemVersion below: keyed on the item alone, clicking
    // the same verse twice never re-runs the effect that resolves it.
    var selectedBibleVerseItemVersion by remember { mutableStateOf(0) }
    var selectedSongItem by remember { mutableStateOf<ScheduleItem.SongItem?>(null) }
    // Incremented every time a song is selected — used as LaunchedEffect key so
    // clicking the same song twice (or API→click) always re-triggers navigation.
    var selectedSongItemVersion by remember { mutableStateOf(0) }
    var selectedPictureItem by remember { mutableStateOf<ScheduleItem.PictureItem?>(null) }
    var selectedPictureItemVersion by remember { mutableStateOf(0) }
    var selectedPresentationItem by remember { mutableStateOf<ScheduleItem.PresentationItem?>(null) }
    var selectedPresentationItemVersion by remember { mutableStateOf(0) }
    var selectedMediaItem by remember { mutableStateOf<ScheduleItem.MediaItem?>(null) }
    var selectedMediaItemVersion by remember { mutableStateOf(0) }
    var selectedLowerThirdItem by remember { mutableStateOf<ScheduleItem.LowerThirdItem?>(null) }
    var selectedLowerThirdItemVersion by remember { mutableStateOf(0) }
    var selectedWebsiteItem by remember { mutableStateOf<ScheduleItem.WebsiteItem?>(null) }
    var selectedWebsiteItemVersion by remember { mutableStateOf(0) }
    val timerExpiredDefaultLabel = stringResource(Res.string.timer_expired)

    var showCrosswordTab by remember { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val hasCompanionTabConnections = appSettings.companionSatelliteConnections.any { it.showInTab && it.host.isNotBlank() }
    val visibleTabs = remember(appSettings.hiddenTabs, showCrosswordTab, hasCompanionTabConnections) {
        computeVisibleTabs(appSettings.hiddenTabs, showCrosswordTab, hasCompanionTabConnections)
    }
    // Clamp synchronously so no composition pass ever sees an out-of-bounds index.
    val effectiveTabIndex = clampedTabIndex(selectedTabIndex, visibleTabs)
    // Persist the clamped value back into state after composition.
    LaunchedEffect(effectiveTabIndex) {
        if (selectedTabIndex != effectiveTabIndex) selectedTabIndex = effectiveTabIndex
    }

    // Remember the URL of the first successful STT connection so the Bible-tab connect
    // button stays visible across restarts (and hides again if the URL later changes).
    val sttConnected = sttManager?.connected?.value == true
    LaunchedEffect(sttConnected) {
        val urlToPersist = sttUrlToPersist(appSettings, sttConnected)
        if (urlToPersist != null) {
            onSettingsChange { s -> withSttLastConnectedUrl(s, urlToPersist) }
        }
    }
    fun selectTab(tab: Tabs) {
        selectedTabIndex = resolveTabSelection(tab, visibleTabs, selectedTabIndex)
    }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var editingLabelItem by remember { mutableStateOf<ScheduleItem.LabelItem?>(null) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }

    val mediaViewModel = LocalMediaViewModel.current

    // Hidden VLCJ player for audio: keeps audio playing when user switches away from Media tab.
    // Only composed when NOT on the Media tab (the tab has its own VideoPlayer).
    val currentTab = visibleTabs[effectiveTabIndex]
    if (mediaViewModel != null &&
        shouldHostBackgroundAudio(mediaViewModel.isAudioFile, mediaViewModel.isPlaying, currentTab)
    ) {
        VideoPlayer(
            viewModel = mediaViewModel,
            modifier = Modifier.size(0.dp)
        )
    }
    // Master video decoder for video files when away from Media tab.
    // When on Media tab, MediaTab hosts its own SoftwareVideoPlayer (the master decoder).
    // Both are mutually exclusive so only one decoder runs at a time.
    if (mediaViewModel != null &&
        shouldHostBackgroundVideo(mediaViewModel.isAudioFile, mediaViewModel.isLoaded, currentTab)
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
        val id = stableFileId(f)
        onSlideChanged?.invoke(id, presentationViewModel.selectedSlideIndex, presentationViewModel.slideFiles.size, presentationViewModel.isPlaying)
    }
    LaunchedEffect(appSettings.presentationRemoteSettings.remoteControlEnabled) {
        val f = presentationViewModel.selectedPresentation
        if (!shouldPublishPresentation(
                appSettings.presentationRemoteSettings.remoteControlEnabled,
                f != null,
                presentationViewModel.slideFiles.size,
            ) || f == null
        ) return@LaunchedEffect
        val id = stableFileId(f)
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

    val sceneViewModel = remember { SceneViewModel() }
    // Publish the scene list out for InstanceLink CANVAS mirroring — the callback pattern keeps
    // SceneViewModel owned here (only the plain Scene list crosses the boundary).
    val currentOnScenesChanged by rememberUpdatedState(onScenesChanged)
    LaunchedEffect(sceneViewModel.scenes.toList()) {
        currentOnScenesChanged?.invoke(sceneViewModel.scenes.toList())
    }

    val currentOnSongsLoaded by rememberUpdatedState(onSongsLoaded)
    val songsViewModel = remember { SongsViewModel(appSettings, onSongsLoaded = { songs -> currentOnSongsLoaded?.invoke(songs) }) }
    DisposableEffect(Unit) { onDispose { songsViewModel.dispose() } }

    // Mirrors the primary's song catalog while connected via Instance Link — see
    // SongsViewModel.setInstanceLinkSource for the lazy per-song lyric fetch. Only in Controlled
    // mode — a Controller keeps browsing its own local song library and drives the primary instead.
    LaunchedEffect(instanceLinkConnectionStatus, instanceLinkRemoteSongCatalog, instanceLinkRole) {
        songsViewModel.setInstanceLinkSource(
            active = shouldMirrorFromPrimary(instanceLinkConnectionStatus, instanceLinkRole),
            catalog = instanceLinkRemoteSongCatalog,
            fetchDetail = instanceLinkFetchSongDetail
        )
    }

    // Sync remote section changes (e.g. from mobile) back to the songs UI
    LaunchedEffect(Unit) {
        snapshotFlow { presenterManager.songDisplaySectionIndex.value }
            .collect { index ->
                if (shouldFollowRemoteSection(
                        presenterManager.presentingMode.value,
                        songsViewModel.selectedSectionIndex.value,
                        index,
                    )
                ) {
                    songsViewModel.selectSection(index)
                }
            }
    }

    val currentOnPicturesLoaded by rememberUpdatedState(onPicturesLoaded)
    val currentOnBibleLoaded by rememberUpdatedState(onBibleLoaded)
    val currentOnSecondaryBibleFilePathChanged by rememberUpdatedState(instanceLinkOnSecondaryBibleFilePathChanged)
    val currentOnBibleFilePathsChanged by rememberUpdatedState(instanceLinkOnBibleFilePathsChanged)
    val bibleViewModel = remember {
        BibleViewModel(
            appSettings,
            onBibleLoaded = { bible, translation -> currentOnBibleLoaded?.invoke(bible, translation) },
            onSecondaryBibleFilePathChanged = { path -> currentOnSecondaryBibleFilePathChanged?.invoke(path) },
            onBibleFilePathsChanged = { paths -> currentOnBibleFilePathsChanged?.invoke(paths) },
        )
    }
    DisposableEffect(Unit) { onDispose { bibleViewModel.dispose() } }

    // Mirrors the primary's bible while connected via Instance Link — see
    // BibleViewModel.setInstanceLinkSource. Only in Controlled mode, same reasoning as Songs above.
    // The two *UpdatedSignal keys re-run this when the primary announces a bible change: the
    // cache is invalidated first so FULL_REPLICA re-downloads the fresh file.
    LaunchedEffect(
        instanceLinkConnectionStatus, instanceLinkBibleSyncMode, instanceLinkRole,
        instanceLinkBibleUpdatedSignal, instanceLinkSecondaryBibleUpdatedSignal
    ) {
        if (shouldInvalidateBibleCache(instanceLinkBibleUpdatedSignal, instanceLinkSecondaryBibleUpdatedSignal)) {
            bibleViewModel.invalidateInstanceLinkBibleCache()
        }
        bibleViewModel.setInstanceLinkSource(
            active = shouldMirrorFromPrimary(instanceLinkConnectionStatus, instanceLinkRole),
            mode = instanceLinkBibleSyncMode,
            fetchBibleFile = instanceLinkFetchBibleFile,
            fetchSecondaryBibleFile = instanceLinkFetchSecondaryBibleFile,
            fetchBibleTranslations = instanceLinkFetchBibleTranslations,
        )
    }

    // Bible Lookup Engine client — feeds detected scripture into the Bible tab and forwards the
    // reverse-lookup level to the engine.
    val bibleEngineClient = remember {
        BibleEngineClient(onScripture = { e ->
            bibleViewModel.onEngineScripture(
                bookId = e.bookId,
                chapter = e.chapter,
                verseStart = e.verseStart,
                verseEnd = e.verseEnd,
                verseText = e.verseText,
                matchType = e.matchType,
                canonicalCodeStart = e.canonicalCodeStart,
                canonicalCodeEnd = e.canonicalCodeEnd,
                segmentId = e.segmentId,
                sessionId = e.sessionId,
                tracks = e.tracks,
                detectedVersion = e.detectedVersion,
            )
        }, onVersion = { version ->
            bibleViewModel.onEngineVersion(version)
        }).also { client ->
            bibleViewModel.onTextMatchLevelChanged = { level -> client.setLevel(level.name.lowercase()) }
            bibleViewModel.onContinuationSpeedChanged = { speed -> client.setContinuationSpeed(speed.name.lowercase()) }
        }
    }
    DisposableEffect(Unit) { onDispose { bibleEngineClient.dispose() } }

    // Engine link lifecycle — owned here (not in BibleTab) so it survives tab switches: BibleTab is
    // composed inside AnimatedContent and would otherwise restart the engine every time the operator
    // navigates back to it. Started when STT connects, stopped on disconnect / when disabled.
    val bibleEngineSettings = appSettings.bibleEngineSettings
    // The SET of bibles to index (sorted, blanks removed). Keying the restart on this means swapping
    // primary↔secondary (same set) does NOT re-index, while changing to a different bible does.
    val engineBibles = remember(appSettings.bibleSettings.translationList()) {
        engineBibleFiles(appSettings.bibleSettings)
    }
    // storageDirectory is a key because it is READ below as bibleRoot: without it, changing the
    // Bible folder mid-service leaves the engine on the old root — old verse index, and a version
    // corpus that is built once at start and never rebuilt. engineBibles does not cover it, being
    // file NAMES: move to another folder holding the same names and the set never changes.
    LaunchedEffect(
        sttConnected, bibleEngineSettings.enabled, bibleEngineSettings.runLocal,
        bibleEngineSettings.host, bibleEngineSettings.port, engineBibles,
        appSettings.bibleSettings.storageDirectory,
    ) {
        if (shouldRunBibleEngine(sttConnected, bibleEngineSettings.enabled, engineBibles)) {
            bibleEngineClient.start(
                sttUrl = appSettings.sttSettings.serverUrl,
                bibleRoot = appSettings.bibleSettings.storageDirectory,
                bibleFiles = engineBibles,
                runLocal = bibleEngineSettings.runLocal,
                host = bibleEngineSettings.host,
                port = bibleEngineSettings.port,
                level = bibleViewModel.textMatchLevel.value.name.lowercase(),
                continuationSpeed = bibleViewModel.continuationSpeed.value.name.lowercase(),
            )
        } else {
            bibleEngineClient.stop()
            bibleViewModel.clearDetectedReferences(reason = "expired")
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
        if (shouldMirrorFromPrimary(instanceLinkConnectionStatus, instanceLinkRole)) {
            scheduleViewModel.applyRemoteSchedule(instanceLinkRemoteSchedule)
        } else {
            scheduleViewModel.stopFollowingRemote()
        }
    }
    LaunchedEffect(instanceLinkSendAddToSchedule) {
        scheduleViewModel.onPushToRemoteSchedule = instanceLinkSendAddToSchedule
    }
    LaunchedEffect(instanceLinkSendRemoveFromSchedule) {
        scheduleViewModel.onRemoveFromRemoteSchedule = instanceLinkSendRemoveFromSchedule
    }

    val presentingMode by presenterManager.presentingMode

    // Runs the clicker-key (Page Down/Up) slide advances from the root key handler.
    val clickerScope = rememberCoroutineScope()

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
        if (!shouldMainHandleAutoFollow(
                activeTabIndex = effectiveTabIndex,
                bibleTabIndex = visibleTabs.indexOf(Tabs.BIBLE),
                presentingMode = presentingMode,
            )
        ) return@LaunchedEffect
        val verses = bibleViewModel.getSelectedVerses()
        if (verses.isNotEmpty()) {
            onVerseSelected(verses)
            val primary = verses.first()
            bibleViewModel.logLiveReference(
                displayBookIndex = bibleViewModel.selectedBookIndex.value,
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "auto",
                autoFollow = true,
                matchType  = bibleViewModel.autoFollowLiveMatchType.value,
            )
        }
    }

    // The same stand-in as above, for a schedule verse clicked while BibleTab is not composed —
    // the Bible tab hidden in settings (selectTab then declines to switch, so the tab never
    // appears), or disposed by the visibleTabs clamp. Without this the item is silently dropped
    // while the presenter has already been switched to BIBLE, which is the blank output that was
    // reported.
    val scheduleVerseGate = rememberTokenGate(selectedBibleVerseItemVersion)
    LaunchedEffect(selectedBibleVerseItemVersion) {
        if (!scheduleVerseGate.consume()) return@LaunchedEffect
        val item = selectedBibleVerseItem ?: return@LaunchedEffect
        if (!shouldMainResolveScheduleVerse(
                activeTabIndex = effectiveTabIndex,
                bibleTabIndex = visibleTabs.indexOf(Tabs.BIBLE),
            )
        ) return@LaunchedEffect
        val verses = bibleViewModel.resolveVerseSelection(
            bookName = item.bookName,
            chapter = item.chapter,
            verseNumber = item.verseNumber,
            verseRange = item.verseRange,
            bookId = item.bookId,
        )
        if (verses.isEmpty()) {
            CrashReporter.breadcrumb(
                "Bible schedule item did not resolve to a verse",
                category = "schedule",
            )
            return@LaunchedEffect
        }
        onVerseSelected(verses)
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

    // Secret Developer-menu unlock: the letter D pressed seven times in a row
    val developerUnlockSequence = remember { List(7) { Key.D } }
    var developerUnlockProgress by remember { mutableStateOf(0) }

    // Notify server whenever the picture folder, image list, or image order changes
    val pictureImages = picturesViewModel.images
    val pictureFolder = picturesViewModel.selectedFolder
    val pictureOrderVersion = picturesViewModel.imageOrderVersion
    LaunchedEffect(pictureFolder, pictureImages.size, pictureOrderVersion) {
        val folder = pictureFolder ?: return@LaunchedEffect
        if (pictureImages.isEmpty()) return@LaunchedEffect
        val folderId = stableFileId(folder)
        currentOnPicturesLoaded?.invoke(folderId, folder.name, folder.absolutePath, pictureImages.toList())
    }

    // Load picture folder when a picture schedule item is selected (works even before Pictures tab is composed)
    LaunchedEffect(selectedPictureItem) {
        selectedPictureItem?.let { pictureItem ->
            val folder = File(pictureItem.folderPath)
            if (isLoadablePictureFolder(folder)) {
                picturesViewModel.selectFolder(folder)
            }
        }
    }

    // Handle remote picture selection (from REST POST /api/pictures/select or WS select_picture)

    // Instance Link Controller-mode navigation — advance/retreat whatever is currently live, no id
    // needed. syncWithPresenter() only pushes when Pictures is actually the live content, same gate
    // next/prev navigation should have.

    // Pushes the presentation's current slide to the presenter — shared by the next/previous slide
    // Instance Link commands below. Only pushes when Presentation is actually the live content,
    // same gate PresentationTab's own slide-push effect uses.
    suspend fun pushCurrentSlideIfLive() {
        val index = presentationViewModel.selectedSlideIndex
        if (!shouldPushSlide(presenterManager.presentingMode.value, index, presentationViewModel.slideFiles.size)) return
        val (bitmap, nextBitmap) = decodeSlideBitmaps(presentationViewModel.slideFiles, index)
        presenterManager.setSelectedSlide(bitmap)
        presenterManager.setNextSlide(nextBitmap)
        presenterManager.setPresenterNotes(presenterNotesAt(presentationViewModel.slideNotes, index))
        // Keep animated playback in sync (or cleared) so a stale animated frame from a
        // previous slide can never override the freshly pushed static slide.
        presentationViewModel.deck?.let { presenterManager.presentationShowSlide(it, index) }
            ?: presenterManager.clearPresentationPlayback()
    }

    // Handle remote slide selection (POST /api/presentations/{id}/select or WS select_slide)
    // No approval required — navigates the live presentation instantly.

    // Handle remote Bible verse instant display (POST /api/bible/select or WS select_bible_verse)
    // No approval required — displays the verse immediately like select_picture.
    // Resolves the request through BibleViewModel so every configured translation follows the same
    // canonical-code mapping as a local click.

    // Handle remote song selection — set selectedSongItem so the Songs tab navigates to it

    // Handle remote picture-folder selection — same backfill shape as remoteSelectSongFlow above.
    // Setting selectedPictureItem drives the existing LaunchedEffect(selectedPictureItem) (below)
    // to load the folder into PicturesViewModel, whose own reactive effect (PicturesTab.kt) pushes
    // the current image to the presenter once loaded, since presentingMode is already PICTURES.

    // Handle remote presentation selection — same shape. Setting selectedPresentationItem drives
    // PresentationTab's own LaunchedEffect(selectedPresentationItem) to load the file, and its
    // LaunchedEffect(selectedSlideIndex, slideFiles.size) pushes the first slide once loaded.

    // Load a presentation file uploaded by a mobile client (POST /api/presentations/upload).
    // addPresentation renders the slides and triggers onSlidesLoaded → companionServer.updatePresentation,
    // which broadcasts WS_EVENT_PRESENTATION_UPDATED so the mobile's GET /api/presentations finds it.
    RemoteCommandEffects(
        appSettings = appSettings,
        picturesViewModel = picturesViewModel,
        presentationViewModel = presentationViewModel,
        bibleViewModel = bibleViewModel,
        presenterManager = presenterManager,
        onSongItemVersionBump = { selectedSongItemVersion++ },
        resolveImageFile = resolveImageFile,
        onSettingsChange = onSettingsChange,
        onSongItemSelected = { selectedSongItem = it },
        onPictureItemSelected = { selectedPictureItem = it; selectedPictureItemVersion++ },
        onPresentationItemSelected = { selectedPresentationItem = it; selectedPresentationItemVersion++ },
        onSelectTab = ::selectTab,
        pushCurrentSlideIfLive = ::pushCurrentSlideIfLive,
        remotePresentationPlayPauseFlow = remotePresentationPlayPauseFlow,
        remotePresentationLoopToggleFlow = remotePresentationLoopToggleFlow,
        remotePresentationGotoFlow = remotePresentationGotoFlow,
        selectPictureImageFlow = selectPictureImageFlow,
        nextPictureFlow = nextPictureFlow,
        previousPictureFlow = previousPictureFlow,
        nextSlideFlow = nextSlideFlow,
        previousSlideFlow = previousSlideFlow,
        selectSlideFlow = selectSlideFlow,
        selectBibleVerseFlow = selectBibleVerseFlow,
        remoteSelectSongFlow = remoteSelectSongFlow,
        remoteSelectPictureFlow = remoteSelectPictureFlow,
        remoteSelectPresentationFlow = remoteSelectPresentationFlow,
        uploadPresentationFlow = uploadPresentationFlow,
    )

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

    val shortcuts = LocalShortcuts.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(mainFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val shortcutTab = shortcuts.actionFor(keyEvent, ShortcutScope.GLOBAL)?.targetTab
                    when {
                        shortcuts.matches(ShortcutAction.REDO, keyEvent) -> {
                            scheduleViewModel.redo(); true
                        }
                        shortcuts.matches(ShortcutAction.UNDO, keyEvent) -> {
                            scheduleViewModel.undo(); true
                        }
                        shortcuts.matches(ShortcutAction.CLEAR_OUTPUT, keyEvent) -> {
                            mediaViewModel?.pause()
                            presenterManager.requestClearDisplay()
                            instanceLinkSendClear?.invoke()
                            // Also release any "Send to Stage Monitor" lock (e.g. from Announcements)
                            // so the stage monitor goes back to following the main presenting mode.
                            stageMonitorScreenIndices(appSettings.projectionSettings.screenAssignments)
                                .forEach { presenterManager.setScreenLock(it, null) }
                            true
                        }
                        // Presentation clickers (Logitech/Kensington etc.) are HID keyboards
                        // sending Page Down/Up. Handled here in the preview pass so a live
                        // presentation responds no matter which tab or control has focus —
                        // the presenter clicks from the platform while the operator works
                        // elsewhere. Only claimed while a presentation is actually live.
                        shortcuts.matches(ShortcutAction.CLICKER_NEXT, keyEvent) && presentingMode == Presenting.PRESENTATION -> {
                            clickerScope.launch {
                                val deck = presentationViewModel.deck
                                val stepped = deck != null && presenterManager
                                    .advancePresentationStep(deck, presentationViewModel.selectedSlideIndex)
                                if (!stepped) {
                                    presentationViewModel.nextSlide(instanceLinkSendNextSlide)
                                    pushCurrentSlideIfLive()
                                }
                            }
                            true
                        }
                        shortcuts.matches(ShortcutAction.CLICKER_PREVIOUS, keyEvent) && presentingMode == Presenting.PRESENTATION -> {
                            clickerScope.launch {
                                val deck = presentationViewModel.deck
                                val stepped = deck != null && presenterManager
                                    .rewindPresentationStep(deck, presentationViewModel.selectedSlideIndex)
                                if (!stepped) {
                                    presentationViewModel.previousSlide(instanceLinkSendPreviousSlide)
                                    pushCurrentSlideIfLive()
                                }
                            }
                            true
                        }
                        shortcutTab != null -> { selectTab(shortcutTab); true }
                        else -> {
                            if (presentingMode != Presenting.NONE) {
                                // Suppress both easter egg sequences while live
                                konamiProgress = 0
                                crosswordProgress = 0
                                false
                            } else {
                                // Konami code: ↑↑↓↓←→←→BA
                                val konamiStep = advanceKeySequence(keyEvent.key, konamiSequence, konamiProgress)
                                konamiProgress = konamiStep.progress
                                if (konamiStep.completed) showKonamiEasterEgg = true

                                // Crossword: ←→←→
                                val crosswordStep = advanceKeySequence(keyEvent.key, crosswordSequence, crosswordProgress)
                                crosswordProgress = crosswordStep.progress
                                if (crosswordStep.completed) {
                                    showCrosswordTab = true
                                    selectTab(Tabs.CROSSWORD)
                                }

                                // Secret unlock: press D seven times in a row to reveal the
                                // Developer menu (upper- or lower-case; Key.D is Shift-agnostic).
                                val developerStep = advanceKeySequence(keyEvent.key, developerUnlockSequence, developerUnlockProgress)
                                developerUnlockProgress = developerStep.progress
                                if (developerStep.completed) onRequestDeveloperMenuUnlock()

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
            val isMaximized = isMaximizedPlacement(windowState?.placement)
            val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

            var scheduleCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.schedulePanelCollapsed) }

            // Schedule panel width — loaded from settings, local state for smooth dragging.
            // Keyed ONLY on isMaximized (not the persisted width, which saveScheduleWidth()
            // rewrites after every single drag gesture) — recreating this MutableState after
            // every gesture was found to correlate with every subsequent gesture's remeasure
            // freezing until an unrelated recompose forced its way through; see AGENT.md's
            // sidebar-resize debugging notes. The object should persist for the whole
            // windowed/maximized session, only reloading when that mode's saved width should
            // legitimately take over (switching between windowed and maximized).
            var schedulePanelPx by remember(isMaximized) {
                mutableStateOf(with(density) { currentLayout.schedulePanelWidthDp.dp.toPx() })
            }
            var previewCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.previewPanelCollapsed) }
            var previewPanelPx by remember(isMaximized) {
                mutableStateOf(with(density) { currentLayout.previewPanelWidthDp.dp.toPx() })
            }

            // Drives the collapse/expand slide manually (replaces AnimatedVisibility, which
            // was found to stop remeasuring its content after the first drag gesture settles —
            // see AGENT.md's sidebar-resize debugging notes). While settled at 0f/1f the
            // rendered width below tracks schedulePanelPx/previewPanelPx with no extra lag.
            val scheduleVisibleFraction = remember { Animatable(if (scheduleCollapsed) 0f else 1f) }
            LaunchedEffect(scheduleCollapsed) {
                scheduleVisibleFraction.animateTo(if (scheduleCollapsed) 0f else 1f, animationSpec = tween(PANEL_COLLAPSE_ANIM_MS))
            }
            val previewVisibleFraction = remember { Animatable(if (previewCollapsed) 0f else 1f) }
            LaunchedEffect(previewCollapsed) {
                previewVisibleFraction.animateTo(if (previewCollapsed) 0f else 1f, animationSpec = tween(PANEL_COLLAPSE_ANIM_MS))
            }

            fun saveScheduleWidth() {
                val widthDp = with(density) { schedulePanelPx.toDp().value.toInt() }
                onSettingsChangeState.value { s -> withScheduleWidth(s, isMaximized, widthDp) }
            }

            fun savePreviewWidth() {
                val widthDp = with(density) { previewPanelPx.toDp().value.toInt() }
                onSettingsChangeState.value { s -> withPreviewWidth(s, isMaximized, widthDp) }
            }

            // Plain Box + onSizeChanged (not BoxWithConstraints/SubcomposeLayout): subcomposed
            // content here was found to stop remeasuring after the first drag gesture on a
            // given handle settles, only catching up when an unrelated, larger recomposition
            // forced its way through — see AGENT.md's sidebar-resize debugging notes. Ordinary
            // composition/layout (no subcomposition boundary) doesn't exhibit that freeze.
            var availablePx by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { availablePx = it.width.toFloat() }
            ) {
                // Keep the resize handles on screen at any window size (e.g. when the
                // window is snapped to a half/quadrant): cap each side panel's width so
                // both 16dp handles plus a minimum slice of main content always fit.
                // Render/drag clamp only — saved widths are untouched and restore when
                // the window grows again.
                val reservePx = with(density) { (16 + 16 + 200).dp.toPx() } // 2 handles + min main
                val absMaxPx = with(density) { 600.dp.toPx() }
                // availablePx is 0f until onSizeChanged fires on the first layout pass — treat
                // that as "unknown" (uncapped) rather than clamping panels to 0 in the interim,
                // since nothing here ever raises schedulePanelPx/previewPanelPx back up once the
                // SideEffect below has clamped them down.
                fun panelCapPx(otherPanelPx: Float) = computePanelCapPx(availablePx, otherPanelPx, reservePx, absMaxPx)

                val maxSchedulePx = panelCapPx(if (previewCollapsed) 0f else previewPanelPx)
                val maxPreviewPx = panelCapPx(if (scheduleCollapsed) 0f else schedulePanelPx)

                // Drag handlers below live across many separate gestures (their
                // pointerInput key no longer churns per-drag — see comment there), so
                // they must read these caps live rather than from a captured local val.
                val maxScheduleState = rememberUpdatedState(maxSchedulePx)
                val maxPreviewState = rememberUpdatedState(maxPreviewPx)

                // Keep the drag-base state in sync with the live cap — otherwise the
                // first drag after a shrink jumps/snaps instead of tracking the cursor,
                // since the drag delta would be applied to a stale, out-of-range base.
                // Uses SideEffect (not LaunchedEffect): these caps are plain vals that
                // change on nearly every recomposition during an active drag, and a
                // LaunchedEffect keyed on a value that churns that fast cancels/relaunches
                // its coroutine constantly, starving live recomposition until the drag ends.
                SideEffect {
                    schedulePanelPx = clampPanelWidth(schedulePanelPx, maxSchedulePx)
                    previewPanelPx = clampPanelWidth(previewPanelPx, maxPreviewPx)
                }

            Row(modifier = Modifier.fillMaxSize()) {
                // Collapsible schedule panel
                if (isPanelRendered(scheduleCollapsed, scheduleVisibleFraction.value)) {
                    Column(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val widthPx = panelRenderWidthPx(schedulePanelPx, maxScheduleState.value, scheduleVisibleFraction.value)
                                val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
                                layout(widthPx, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                            .fillMaxHeight()
                    ) {
                    // Shown once a host has ever been configured — not just while actively connected —
                    // so the operator can always see the last-known status and reconnect/disconnect
                    // without reopening the Connect dialog.
                    if (instanceLinkFollowingHost.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 1s ticker so the "reconnecting in Xs" countdown stays current while
                            // the link is down; idle (single recomposition) otherwise.
                            var reconnectNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(instanceLinkConnectionStatus == InstanceLinkStatus.ERROR) {
                                while (instanceLinkConnectionStatus == InstanceLinkStatus.ERROR) {
                                    reconnectNowMs = System.currentTimeMillis()
                                    delay(CLOCK_TICK_MS)
                                }
                            }
                            val retrySecondsLeft = retrySecondsLeft(instanceLinkNextRetryAtMs, reconnectNowMs)
                            ConnectionStatusRow(
                                status = instanceLinkConnectionStatus,
                                connectedLabel = if (instanceLinkRole == InstanceLinkRole.CONTROLLER)
                                    stringResource(Res.string.instance_link_controlling_host, instanceLinkFollowingHost)
                                else
                                    stringResource(Res.string.instance_link_following_host, instanceLinkFollowingHost),
                                errorLabel = retrySecondsLeft?.let {
                                    stringResource(Res.string.instance_link_status_reconnecting_in, it.toInt())
                                }
                            )
                            // != DISCONNECTED (not just CONNECTED/CONNECTING) so the operator can
                            // stop an ERROR-state retry loop without reopening the dialog.
                            if (canDisconnectInstanceLink(instanceLinkConnectionStatus)) {
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

                        onPresentBible = { item ->
                            selectTab(Tabs.BIBLE)
                            selectedBibleVerseItem = item
                            selectedBibleVerseItemVersion++
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
                            selectedPresentationItemVersion++
                            presenting(Presenting.PRESENTATION)
                        },
                        onPresentPictures = { item ->
                            selectedPictureItem = item
                            selectedPictureItemVersion++
                            selectTab(Tabs.PICTURES)
                            presenting(Presenting.PICTURES)
                        },
                        onPresentMedia = { item ->
                            selectTab(Tabs.MEDIA)
                            selectedMediaItem = item
                            selectedMediaItemVersion++
                            presenting(Presenting.MEDIA)
                        },
                        onPresentAnnouncement = { item ->
                            onSettingsChange { settings ->
                                withAnnouncementFrom(settings, item)
                            }
                            if (item.isTimer) {
                                presenterManager.goLiveAnnouncementTimer(
                                    timerMode = item.timerMode,
                                    timerHours = item.timerHours,
                                    timerMinutes = item.timerMinutes,
                                    timerSeconds = item.timerSeconds,
                                    targetHour = item.targetHour,
                                    targetMinute = item.targetMinute,
                                    targetSecond = item.targetSecond,
                                    liveClockFormat = item.liveClockFormat,
                                    timerExpiredText = item.timerExpiredText.ifBlank { timerExpiredDefaultLabel }
                                )
                            } else {
                                presenterManager.setAnnouncementText(item.text)
                            }
                            presenting(Presenting.ANNOUNCEMENTS)
                        },
                        onPresentLowerThird = { item ->
                            val lottieFolder = File(appSettings.streamingSettings.lowerThirdFolder)
                            val lottieFile = findLottiePresetFile(lottieFolder.listFiles()?.toList(), item.presetLabel, item.presetId)
                            if (lottieFile != null && lottieFile.exists()) {
                                val json = lottieFile.readText()
                                presenterManager.setLottieContent(json, item.pauseAtFrame, -1f, item.pauseDurationMs, lottieFile.nameWithoutExtension)
                                presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                presenterManager.setShowPresenterWindow(true)
                            }
                        },
                        onPresentWebsite = { item ->
                            selectedWebsiteItem = item
                            selectedWebsiteItemVersion++
                            selectTab(Tabs.WEB)
                            presenterManager.setWebsiteUrl(item.url)
                            presenting(Presenting.WEBSITE)
                        },
                        onPresentDictionary = { item ->
                            presenterManager.setAnnouncementText("${item.word} (${item.transliteration})\n\n${item.definition}")
                            presenterManager.setShowPresenterWindow(true)
                            presenting(Presenting.ANNOUNCEMENTS)
                        },
                        onPresentScene = { item ->
                            sceneViewModel.selectScene(item.sceneId)
                            val scene = sceneViewModel.scenes.find { it.id == item.sceneId }
                            presenterManager.setActiveScene(scene)
                            selectTab(Tabs.CANVAS)
                            presenting(Presenting.CANVAS)
                        },
                        onItemClick = { item ->
                            tabForScheduleItem(item)?.let { selectTab(it) }
                            when (item) {
                                is ScheduleItem.SongItem -> {
                                    selectedSongItem = item
                                    selectedSongItemVersion++
                                }

                                is ScheduleItem.BibleVerseItem -> {
                                    selectedBibleVerseItem = item
                                    selectedBibleVerseItemVersion++
                                }

                                is ScheduleItem.LabelItem -> {
                                    editingLabelItem = item
                                    showAddLabelDialog = true
                                }

                                is ScheduleItem.PictureItem -> {
                                    selectedPictureItem = item
                                    selectedPictureItemVersion++
                                }

                                is ScheduleItem.PresentationItem -> {
                                    selectedPresentationItem = item
                                    selectedPresentationItemVersion++
                                }

                                is ScheduleItem.MediaItem -> {
                                    selectedMediaItem = item
                                    selectedMediaItemVersion++
                                }

                                is ScheduleItem.LowerThirdItem -> {
                                    selectedLowerThirdItem = item
                                    selectedLowerThirdItemVersion++
                                }

                                is ScheduleItem.AnnouncementItem -> {
                                    onSettingsChange { settings -> withAnnouncementFrom(settings, item) }
                                }

                                is ScheduleItem.WebsiteItem -> {
                                    selectedWebsiteItem = item
                                    selectedWebsiteItemVersion++
                                }

                                is ScheduleItem.SceneItem -> {
                                    sceneViewModel.selectScene(item.sceneId)
                                }

                                is ScheduleItem.DictionaryItem -> {
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
                                    removeById = actions.removeById,
                                    clearSchedule = actions.clearSchedule,
                                    addSong = actions.addSong,
                                    addBibleVerse = actions.addBibleVerse,
                                    addPicture = actions.addPicture,
                                    addPresentation = actions.addPresentation,
                                    addMedia = actions.addMedia,
                                    addScene = actions.addScene,
                                    addDictionary = actions.addDictionary,
                                    addAnnouncement = { item ->
                                        actions.addAnnouncement(
                                            item.text, item.textColor, item.backgroundColor,
                                            item.fontSize, item.fontType, item.bold, item.italic,
                                            item.underline, item.shadow, item.shadowColor,
                                            item.shadowSize, item.shadowOpacity, item.horizontalAlignment,
                                            item.position, item.animationType, item.animationDuration,
                                            item.loopCount, item.isTimer, item.timerHours,
                                            item.timerMinutes, item.timerSeconds, item.timerTextColor,
                                            item.timerExpiredText, item.timerMode, item.targetHour,
                                            item.targetMinute, item.targetSecond, item.liveClockFormat
                                        )
                                    },
                                    addWebsite = actions.addWebsite
                                )
                            )
                        },
                        onSelectedItemChanged = { id ->
                            onScheduleItemSelected(id)
                        },
                        onScheduleChanged = onScheduleChanged,
                        theme = theme,
                        itemZoomPercent = appSettings.scheduleItemZoomPercent,
                        onItemZoomChange = { percent ->
                            onSettingsChange { settings -> settings.copy(scheduleItemZoomPercent = percent) }
                        },
                        legacyRowActions = appSettings.scheduleLegacyRowActions,
                        onLegacyRowActionsChange = { legacy ->
                            onSettingsChange { settings -> settings.copy(scheduleLegacyRowActions = legacy) }
                        },
                        hiddenToolbarButtons = appSettings.hiddenScheduleButtons,
                        onToggleToolbarButton = { button ->
                            onSettingsChange { settings ->
                                settings.copy(
                                    hiddenScheduleButtons =
                                        toggleHiddenScheduleButton(settings.hiddenScheduleButtons, button)
                                )
                            }
                        },
                        planningCenterSettings = appSettings.planningCenterSettings,
                        onPlanningCenterTokensRefreshed = { accessToken, refreshToken, expiresAtEpochMs ->
                            onSettingsChange { settings ->
                                settings.copy(
                                    planningCenterSettings = settings.planningCenterSettings.copy(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        tokenExpiresAtEpochMs = expiresAtEpochMs
                                    )
                                )
                            }
                        },
                        onPlanningCenterConnected = { accessToken, refreshToken, expiresAtEpochMs, personName ->
                            onSettingsChange { settings ->
                                settings.copy(
                                    planningCenterSettings = settings.planningCenterSettings.copy(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        tokenExpiresAtEpochMs = expiresAtEpochMs,
                                        connectedPersonName = personName
                                    )
                                )
                            }
                        },
                        onPlanningCenterDisconnect = {
                            onSettingsChange { settings ->
                                settings.copy(
                                    planningCenterSettings = settings.planningCenterSettings.copy(
                                        accessToken = "",
                                        refreshToken = "",
                                        tokenExpiresAtEpochMs = 0L,
                                        connectedPersonName = ""
                                    )
                                )
                            }
                        }
                    )
                    } // end Box (ScheduleTab weight)
                    ScheduleSidebarCompanionPanel(
                        connections = appSettings.companionSatelliteConnections,
                        companionSatelliteViewModel = companionSatelliteViewModel,
                    )
                    } // end Column
                } // end if (schedule panel visible)

                // Drag handle + collapse toggle between schedule and main content
                PanelResizeHandle(
                    collapsed = scheduleCollapsed,
                    onResize = { amount ->
                        val cap = maxScheduleState.value
                        schedulePanelPx = resizedPanelWidth(
                            currentPx = schedulePanelPx,
                            dragAmount = amount,
                            invert = false,
                            minPx = with(density) { 160.dp.toPx() },
                            maxPx = cap,
                        )
                    },
                    onResizeEnd = ::saveScheduleWidth,
                    onToggleCollapsed = {
                        scheduleCollapsed = !scheduleCollapsed
                        onSettingsChangeState.value { s ->
                            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(schedulePanelCollapsed = scheduleCollapsed))
                            else s.copy(windowedLayout = s.windowedLayout.copy(schedulePanelCollapsed = scheduleCollapsed))
                        }
                    },
                    icon = painterResource(
                        if (scheduleCollapsed) Res.drawable.ic_arrow_right else Res.drawable.ic_arrow_left
                    ),
                    contentDescription = stringResource(
                        if (scheduleCollapsed) Res.string.tooltip_expand_schedule
                        else Res.string.tooltip_collapse_schedule
                    ),
                )

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
                                val visibleCount = visibleTabCount(appSettings.hiddenTabs)
                                Tabs.entries.filter { it != Tabs.CROSSWORD }.forEach { tab ->
                                    val isVisible = tab.name !in appSettings.hiddenTabs
                                    val isOnlyVisible = isOnlyVisibleTab(tab, appSettings.hiddenTabs, visibleCount)
                                    DropdownMenuItem(
                                        text = { Text(getStringName(tab)) },
                                        onClick = {
                                            if (!isOnlyVisible) {
                                                onSettingsChange { s -> s.copy(hiddenTabs = toggleHiddenTabs(s.hiddenTabs, tab)) }
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
                        transitionSpec = { fadeIn(tween(CONTENT_CROSSFADE_MS)) togetherWith fadeOut(tween(CONTENT_CROSSFADE_MS)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = "tab_content"
                    ) { tab ->
                        when (tab) {
                            Tabs.BIBLE -> BibleTab(
                                modifier = Modifier.fillMaxSize(),
                                hostWindow = hostWindow,
                                viewModel = bibleViewModel,
                                appSettings = appSettings,
                                onSettingsChange = onSettingsChange,
                                onAddToSchedule = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
                                    currentScheduleActions.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
                                },
                                selectedVerseItem = selectedBibleVerseItem,
                                selectedVerseItemVersion = selectedBibleVerseItemVersion,
                                onVerseSelected = onVerseSelected,
                                onInstanceLinkSendVerse = instanceLinkSendVerse,
                                onInstanceLinkSendBibleHold = instanceLinkSendBibleHold,
                                onPresenting = presenting,
                                isPresenting = presentingMode == Presenting.BIBLE,
                                presenterManager = presenterManager,
                                statisticsManager = statisticsManager,
                                verseSequenceLog = verseSequenceLog,
                                dialogDismissSignal = dialogDismissSignal,
                                sttManager = sttManager,
                                bibleEngineClient = bibleEngineClient
                            )

                            Tabs.SONGS -> SongsTab(
                                modifier = Modifier.fillMaxSize(),
                                hostWindow = hostWindow,
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
                                hostWindow = hostWindow,
                                appSettings = appSettings,
                                onAddToSchedule = { folderPath, folderName, imageCount ->
                                    currentScheduleActions.addPicture(folderPath, folderName, imageCount)
                                },
                                onInstanceLinkSendProject = instanceLinkSendProject,
                                onInstanceLinkSendNextPicture = instanceLinkSendNextPicture,
                                onInstanceLinkSendPreviousPicture = instanceLinkSendPreviousPicture,
                                instanceLinkFetchPictureImageBytes = instanceLinkFetchPictureImageBytes,
                                selectedPictureItem = selectedPictureItem,
                                selectedPictureItemVersion = selectedPictureItemVersion,
                                presenterManager = presenterManager,
                                onSettingsChange = onSettingsChange,
                                viewModel = picturesViewModel
                            )

                            Tabs.PRESENTATION -> PresentationTab(
                                modifier = Modifier.fillMaxSize(),
                                hostWindow = hostWindow,
                                appSettings = appSettings,
                                onAddToSchedule = { filePath, fileName, slideCount, fileType ->
                                    currentScheduleActions.addPresentation(filePath, fileName, slideCount, fileType)
                                },
                                onInstanceLinkSendProject = instanceLinkSendProject,
                                onInstanceLinkSendNextSlide = instanceLinkSendNextSlide,
                                onInstanceLinkSendPreviousSlide = instanceLinkSendPreviousSlide,
                                instanceLinkFetchPresentationSlideBytes = instanceLinkFetchPresentationSlideBytes,
                                selectedPresentationItem = selectedPresentationItem,
                                selectedPresentationItemVersion = selectedPresentationItemVersion,
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
                                selectedMediaItemVersion = selectedMediaItemVersion,
                                presenterManager = presenterManager,
                                instanceLinkMediaStreamUrl = instanceLinkMediaStreamUrl,
                                onInstanceLinkSendProject = instanceLinkSendProject
                            )

                            Tabs.LOWER_THIRD -> LowerThirdTab(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                selectedLowerThirdItem = selectedLowerThirdItem,
                                selectedLowerThirdItemVersion = selectedLowerThirdItemVersion,
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
                                onAddToSchedule = { settings ->
                                    val isTimer = settings.timerMode != Constants.TIMER_MODE_DURATION ||
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
                                        settings.shadowColor,
                                        settings.shadowSize,
                                        settings.shadowOpacity,
                                        settings.horizontalAlignment,
                                        settings.position,
                                        settings.animationType,
                                        settings.animationDuration,
                                        settings.loopCount,
                                        isTimer,
                                        settings.timerHours,
                                        settings.timerMinutes,
                                        settings.timerSeconds,
                                        settings.timerTextColor,
                                        settings.timerExpiredText,
                                        settings.timerMode,
                                        settings.targetHour,
                                        settings.targetMinute,
                                        settings.targetSecond,
                                        settings.liveClockFormat
                                    )
                                }
                            )

                            Tabs.WEB -> WebTab(
                                modifier = Modifier.fillMaxSize(),
                                presenterManager = presenterManager,
                                selectedWebsiteItem = selectedWebsiteItem,
                                selectedWebsiteItemVersion = selectedWebsiteItemVersion,
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
                PanelResizeHandle(
                    collapsed = previewCollapsed,
                    // Inverted: this panel is on the right, so dragging left widens it.
                    onResize = { amount ->
                        val cap = maxPreviewState.value
                        previewPanelPx = resizedPanelWidth(
                            currentPx = previewPanelPx,
                            dragAmount = amount,
                            invert = true,
                            minPx = with(density) { 150.dp.toPx() },
                            maxPx = cap,
                        )
                    },
                    onResizeEnd = ::savePreviewWidth,
                    onToggleCollapsed = {
                        previewCollapsed = !previewCollapsed
                        onSettingsChangeState.value { s ->
                            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(previewPanelCollapsed = previewCollapsed))
                            else s.copy(windowedLayout = s.windowedLayout.copy(previewPanelCollapsed = previewCollapsed))
                        }
                    },
                    icon = painterResource(
                        if (previewCollapsed) Res.drawable.ic_arrow_left else Res.drawable.ic_arrow_right
                    ),
                    contentDescription = stringResource(
                        if (previewCollapsed) Res.string.tooltip_expand_schedule
                        else Res.string.tooltip_collapse_schedule
                    ),
                )

                // Collapsible preview panel (right sidebar)
                PreviewSidebar(
                    collapsed = previewCollapsed,
                    visibleFraction = previewVisibleFraction.value,
                    previewPanelPx = previewPanelPx,
                    maxPreviewPx = maxPreviewState.value,
                    presenterManager = presenterManager,
                    mediaViewModel = mediaViewModel,
                    instanceLinkSendClear = instanceLinkSendClear,
                    livePreviewAppSettings = livePreviewAppSettings,
                    appSettings = appSettings,
                    serverUrl = serverUrl,
                    qaDisplayUrl = qaDisplayUrl,
                    sttManager = sttManager,
                    companionSatelliteViewModel = companionSatelliteViewModel,
                )
            }
            } // end Box (available-width measurement)
        }
    }

    AddLabelDialog(
        isVisible = showAddLabelDialog,
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
        // Empty, not a hardcoded pair: a new label picks its colours up from the active theme.
        existingTextColor = editingLabelItem?.textColor.orEmpty(),
        existingBackgroundColor = editingLabelItem?.backgroundColor.orEmpty(),
        isEdit = editingLabelItem != null
    )

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

/**
 * The right-hand sidebar: the display controls, the live preview, and any Companion surface routed
 * here.
 *
 * A private composable in this same file rather than its own: splitting it out keeps `MainDesktop`'s
 * own method small enough for JaCoCo to fit its probes into — a method near the JVM's 64KB ceiling
 * cannot be instrumented, and an uninstrumentable class reports zero coverage for everything in it.
 * Keeping it here means no view model leaves the file that already owns this wiring, so this is not
 * a new instance of passing view models around.
 */
@Composable
private fun PreviewSidebar(
    collapsed: Boolean,
    visibleFraction: Float,
    previewPanelPx: Float,
    maxPreviewPx: Float,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel?,
    instanceLinkSendClear: (() -> Unit)?,
    livePreviewAppSettings: AppSettings,
    appSettings: AppSettings,
    serverUrl: String,
    qaDisplayUrl: String,
    sttManager: STTManager?,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
) {
    if (isPanelRendered(collapsed, visibleFraction)) {
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val widthPx = panelRenderWidthPx(previewPanelPx, maxPreviewPx, visibleFraction)
                    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
                    layout(widthPx, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
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
                        instanceLinkSendClear?.invoke()
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
                    mutableStateOf(resolveSelectedConnectionId(null, rightSidebarConnections))
                }
                LaunchedEffect(rightSidebarConnections.map { it.id }) {
                    selectedRightSidebarId = resolveSelectedConnectionId(selectedRightSidebarId, rightSidebarConnections)
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

/**
 * Any Companion surface routed to the left sidebar, under the schedule.
 *
 * Private and in this file for the same reason as [PreviewSidebar]: it keeps `MainDesktop`'s own
 * method small enough to instrument, without a view model leaving the file that owns the wiring.
 */
@Composable
private fun ScheduleSidebarCompanionPanel(
    connections: List<CompanionSatelliteSettings>,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
) {
        val leftSidebarConnections = connections.filter { it.showInLeftSidebar && it.host.isNotBlank() }
        if (leftSidebarConnections.isNotEmpty()) {
            HorizontalDivider()
            var selectedLeftSidebarId by remember(leftSidebarConnections.map { it.id }) {
                mutableStateOf(resolveSelectedConnectionId(null, leftSidebarConnections))
            }
            LaunchedEffect(leftSidebarConnections.map { it.id }) {
                selectedLeftSidebarId = resolveSelectedConnectionId(selectedLeftSidebarId, leftSidebarConnections)
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
}
