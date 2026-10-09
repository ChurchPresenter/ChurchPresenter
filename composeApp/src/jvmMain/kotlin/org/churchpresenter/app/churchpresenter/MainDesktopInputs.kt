package org.churchpresenter.app.churchpresenter

import org.churchpresenter.showcontrol.Action
import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import kotlinx.coroutines.flow.Flow
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.server.SongDetailDto
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.bible.Bible
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.controlin.ControlHub
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.InstanceLinkRole
import java.io.File
import org.churchpresenter.slides.PresentationSlidesLoaded

/*
 * The inputs MainDesktop takes, grouped by concern. main.kt builds one of each per composition, so a
 * holder carries the same values and the same lambda instances a flat parameter list used to.
 */

/** What the main screen sends to the live output as the operator presents. */
data class LiveOutputCallbacks(
    val presenting: (Presenting) -> Unit,
    val onVerseSelected: (List<SelectedVerse>) -> Unit,
    val onSongItemSelected: (LyricSection) -> Unit,
    val onAllSectionsChanged: (List<LyricSection>) -> Unit = {},
    val onSectionIndexChanged: (Int) -> Unit = {},
    val onLineIndexChanged: (Int) -> Unit = {},
    /** A row the operator put on screen from the Schedule -- timed, so its length can be learnt. */
    val onRowWentLive: (ScheduleItem) -> Unit = {},
    /** A row put on screen from the Schedule, with the actions it runs as it reaches the air. */
    val onRowActions: (ScheduleItem, List<Action>) -> Unit = { _, _ -> },
    /** Runs a macro from the Macros panel or a key. */
    val onRunMacro: (Macro) -> Unit = {},
    /** The MIDI and OSC ports, for the dialog that sets them up and learns triggers. */
    val controlHub: ControlHub? = null,
    /** Dev mode: the features not ready for production are offered -- see `AppRootState.devMode`. */
    val devMode: Boolean = false,
)

/** The planned service the Schedule works against, from the calendar. */
data class ServicePlanLink(
    /** The planned service the calendar will load into the Schedule by itself next, if any. */
    val upcomingServiceLoad: UpcomingLoad? = null,
    /** Loads [upcomingServiceLoad] now instead of waiting for it: into a cleared Schedule, or after it. */
    val onLoadServiceNow: (replace: Boolean) -> Unit = {},
    /** The planned service the Schedule holds, and whether it has changed there since. */
    val scheduleService: ScheduleServiceLink? = null,
    /** Writes the Schedule's rows back into [scheduleService]. */
    val onSaveScheduleToCalendar: () -> Unit = {},
    /** Opens the Calendar Manager on a new service built from the Schedule's rows; null offers none. */
    val onAddScheduleToCalendar: (() -> Unit)? = null,
    /** Fires a cue row of the Schedule by hand -- the same path the automation engine takes. */
    val onPresentCue: (ScheduleItem.CueItem) -> Unit = {},
    /** How long a song usually runs here, measured -- shown in the song editor. */
    val typicalSongSeconds: (SongItem) -> Int? = { null },
)

/** What the main screen publishes outward as its content changes. */
data class MainDesktopPublishers(
    val onSongsLoaded: ((List<SongItem>) -> Unit)? = null,
    val onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null,
    /** This instance's saved Canvas scenes, re-published whenever they change — the InstanceLink
     *  follower path resolves a mirrored CANVAS live state by scene id against this list. */
    val onScenesChanged: ((List<Scene>) -> Unit)? = null,
    val onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    val onPresentationSlidesLoaded: PresentationSlidesLoaded? = null,
    val onPicturesLoaded: PicturesLoaded? = null,
    val onSlideChanged: ((id: String, slideIndex: Int, total: Int, isPlaying: Boolean) -> Unit)? = null,
    val onScheduleActionsReady: (ScheduleActions) -> Unit = {},
    val onScheduleItemSelected: (String?) -> Unit = {},
    val onTabChange: (Int) -> Unit = {},
    /** The tab now showing, by what it is — the helper reads "make it bigger" by it. */
    val onCurrentTabChange: (Tabs) -> Unit = {},
)

/** A picture folder's images, ready to serve to remote clients. */
typealias PicturesLoaded = (folderId: String, folderName: String, folderPath: String, imageFiles: List<File>) -> Unit

/** Requests from remote clients (phones, the REST API, Instance Link) that drive the tabs directly. */
data class RemoteControlFlows(
    val selectPictureImageFlow: Flow<Pair<String, Int>>? = null,
    /**
     * Resolves an image [File] by folder-id and index from the companion server's file map.
     * When non-null, remote picture selections are served from the correct folder even when
     * the requested folder differs from the one currently loaded in the Pictures tab UI
     * (e.g. session-only device_uploads photos).
     */
    val resolveImageFile: ((folderId: String, index: Int) -> File?)? = null,
    /** Emits (presentationId, slideIndex) — instantly navigates to that slide without approval. */
    val selectSlideFlow: Flow<Pair<String, Int>>? = null,
    /** Emits a verse to display instantly without approval. */
    val selectBibleVerseFlow: Flow<SelectBibleVerseRequest>? = null,
    val remoteSelectSongFlow: Flow<RemoteSongSelection>? = null,
    /** Same backfill mechanism as [remoteSelectSongFlow] — a remote PROJECT go-live for a picture
     *  folder/presentation only adds it to the schedule and flips slideContent; these drive the
     *  main screen to actually load the real content into the corresponding ViewModel. */
    val remoteSelectPictureFlow: Flow<ScheduleItem.PictureItem>? = null,
    val remoteSelectPresentationFlow: Flow<ScheduleItem.PresentationItem>? = null,
    /** A projected video, handed to the Media tab so it actually loads and plays it. */
    val remoteSelectMediaFlow: Flow<ScheduleItem.MediaItem>? = null,
    /** Instance Link Controller-mode navigation — advance/retreat whatever the primary currently has
     *  live (no id needed, see Constants.WS_CMD_NEXT_PICTURE and siblings). Received on the primary
     *  side; sent from the Controller side via [InstanceLinkBridge.sendNextPicture] and siblings. */
    val nextPictureFlow: Flow<Unit>? = null,
    val previousPictureFlow: Flow<Unit>? = null,
    val nextSlideFlow: Flow<Unit>? = null,
    val previousSlideFlow: Flow<Unit>? = null,
    /** Emits a presentation [File] uploaded by a mobile client — loaded into the Presentation tab automatically. */
    val uploadPresentationFlow: Flow<File>? = null,
    val remotePresentationPlayPauseFlow: Flow<Unit>? = null,
    val remotePresentationLoopToggleFlow: Flow<Unit>? = null,
    val remotePresentationGotoFlow: Flow<Int>? = null,
    /** Tabs the helper asks the main screen to switch to, when it points at something on one. */
    val selectTabFlow: Flow<Tabs>? = null,
)

/** This instance's side of an Instance Link, as a follower, a controller or a primary. */
data class InstanceLinkBridge(
    /** Persistent "Following <host>" badge shown above the Schedule panel while connected. */
    val connectionStatus: InstanceLinkStatus = InstanceLinkStatus.DISCONNECTED,
    val followingHost: String = "",
    /** Absolute wall-clock ms of the next reconnect attempt while status is ERROR, else null. */
    val nextRetryAtMs: Long? = null,
    /** Persistent "Primary — N follower(s) connected" badge — the symmetric primary-side counterpart. */
    val followerCount: Int = 0,
    /** Reconnects using the last-saved Instance Link settings — lets the Connect/Disconnect button
     *  next to the badge work without reopening the Connect dialog. */
    val onConnect: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    /** The primary's live schedule while connected — mirrored into the ScheduleViewModel. */
    val remoteSchedule: List<ScheduleItemDto> = emptyList(),
    /** The primary's song catalog while connected — mirrored into the SongsViewModel. */
    val remoteSongCatalog: SongCatalogResponse? = null,
    /** Fetches one song's full lyrics from the primary on demand — see SongsViewModel.setInstanceLinkSource. */
    val fetchSongDetail: (suspend (number: String, songbook: String) -> SongDetailDto?)? = null,
    /** Downloads the primary's bible file while connected — see BibleViewModel.setInstanceLinkSource. */
    val fetchBibleFile: (suspend () -> ByteArray?)? = null,
    /** Bumped when the primary announces its bible/secondary-bible changed — triggers cache
     *  invalidation + re-download in the bible mirror effect. */
    val bibleUpdatedSignal: Int = 0,
    val secondaryBibleUpdatedSignal: Int = 0,
    /** How the Bible tab tracks the primary while connected — see BibleSyncMode. */
    val bibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    val fetchSecondaryBibleFile: (suspend () -> ByteArray?)? = null,
    val fetchBibleTranslations: (suspend () -> List<Pair<String, ByteArray>>)? = null,
    /** Reports the secondary bible's local file path to CompanionServer (for GET /api/bible/file/secondary). */
    val onSecondaryBibleFilePathChanged: ((filePath: String) -> Unit)? = null,
    val onBibleFilePathsChanged: ((filePaths: List<String>) -> Unit)? = null,
    /** Non-null while connected — see MediaTab's instanceLinkMediaStreamUrl. */
    val mediaStreamUrl: ((itemId: String) -> String)? = null,
    /** Non-null only when connected AND the operator has enabled pushing items to the primary's
     *  schedule — see ScheduleViewModel.onPushToRemoteSchedule. */
    val sendAddToSchedule: ((ScheduleItem) -> Unit)? = null,
    /** Same gate as [sendAddToSchedule] — see ScheduleViewModel.onRemoveFromRemoteSchedule. */
    val sendRemoveFromSchedule: ((id: String) -> Unit)? = null,
    /** See InstanceLinkRole — CONTROLLED (default, mirror the primary) or CONTROLLER (drive it). */
    val role: InstanceLinkRole = InstanceLinkRole.CONTROLLED,
    /** Controller mode "go live with a new item" — approval-gated the first time on the primary,
     *  instant afterwards. Non-null only when connected AND in Controller mode. */
    val sendProject: ((ScheduleItem) -> Unit)? = null,
    /** Controller mode instant Bible verse display — non-null only when connected AND controlling. */
    val sendVerse: (
        (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit
    )? = null,
    /** Controller mode instant song-section navigation (within an already-live song) — non-null only
     *  when connected AND controlling. */
    val sendSongSection: ((number: String, section: Int, lineIndex: Int) -> Unit)? = null,
    /** Controller mode instant clear — non-null only when connected AND controlling. */
    val sendClear: (() -> Unit)? = null,
    /** Controller mode instant Bible Hold toggle — non-null only when connected AND controlling. */
    val sendBibleHold: ((Boolean) -> Unit)? = null,
    /** Controller mode next/previous navigation for whatever the primary currently has live —
     *  non-null only when connected AND controlling. See [RemoteControlFlows.nextPictureFlow] and
     *  siblings for the primary-side receive. */
    val sendNextPicture: (() -> Unit)? = null,
    val sendPreviousPicture: (() -> Unit)? = null,
    val sendNextSlide: (() -> Unit)? = null,
    val sendPreviousSlide: (() -> Unit)? = null,
    /** Fetches remote bytes for a mirrored Picture/Presentation schedule item whose local path
     *  doesn't resolve on this machine (network/shared-drive mismatch) — non-null only while
     *  connected. See PicturesTab/PresentationTab's fallback in their selected-item effects. */
    val fetchPictureImageBytes: (suspend (folderId: String, index: Int) -> ByteArray?)? = null,
    val fetchPresentationSlideBytes: (suspend (id: String, index: Int) -> ByteArray?)? = null,
)

/** The server addresses, the public tunnel and the remote-presentation controls the tabs show. */
data class WebAccessState(
    val serverUrl: String = "",
    val tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    val tunnelUrl: String = "",
    val onStartTunnel: () -> Unit = {},
    val onStopTunnel: () -> Unit = {},
    val qaDisplayUrl: String = "",
    val onQaDisplayUrlChanged: (String) -> Unit = {},
    val presentationDisplayUrl: String = "",
    val onPresentationDisplayUrlChanged: (String) -> Unit = {},
    val presentationFrozen: Boolean = false,
    val onFreezeToggle: () -> Unit = {},
    val onClearPresentation: () -> Unit = {},
)
