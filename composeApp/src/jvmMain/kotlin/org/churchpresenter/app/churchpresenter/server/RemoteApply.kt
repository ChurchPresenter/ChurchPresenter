package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.ui.input.key.type
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import org.churchpresenter.bible.Bible
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

private const val SUMMARY_PREVIEW_CHARS = 60

/**
 * Applying what a *remote* instance sends to this one: the Instance Link follower path, and the
 * approved remote requests that arrive from a phone or a linked controller.
 *
 * This is behaviour, not window construction, and it used to live in `main.kt` where nothing could
 * reach it -- excluded from the coverage gate as app-entry wiring *and* sitting at 0%, so a defect
 * here was invisible twice over. What a follower does with the primary's live state decides what an
 * overflow room shows mid-service; it is worth testing on its own terms.
 *
 * Moved verbatim from `main.kt`; only visibility changed (private -> internal) so tests can reach it.
 */

/** Where fetched picture bytes are cached so PresenterManager.setSelectedImagePath (which needs a
 *  local path, not bytes) can display them like any other local file. */
internal val instanceLinkPictureCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/pictures").apply { mkdirs() }
}

/** Where fetched background image/video bytes are cached, keyed by slot — BackgroundConfig's
 *  image/video fields need a local path, not bytes, same reasoning as [instanceLinkPictureCacheDir]. */
internal val instanceLinkBackgroundCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/backgrounds").apply { mkdirs() }
}

/**
 * Whether this instance mirrors the primary's live output onto its own presenter — the single
 * decision behind every "does the follower follow?" gate in `main.kt` (live state, the dedicated
 * presentation-slide broadcast, and display_cleared).
 *
 * Only [InstanceLinkRole.CONTROLLED] mirrors. A [InstanceLinkRole.CONTROLLER] drives the primary
 * and keeps its own output: it goes live locally *and* sends the command, so mirroring the primary
 * as well would echo that command straight back and overwrite the content it had just put up — with
 * the primary's version of it, refetched over the network. The primary's connect snapshot replays
 * its current live state to every client, so an ungated Controller is clobbered the moment it
 * connects, before the operator does anything at all.
 */
internal fun shouldMirrorRemoteOutput(role: InstanceLinkRole): Boolean =
    role == InstanceLinkRole.CONTROLLED

/**
 * Whether content should be sourced from the primary rather than from this machine — the same
 * decision as [shouldMirrorRemoteOutput], plus a live connection to source it over.
 *
 * Gates the remote-asset fallbacks (picture bytes, presentation slides, the media stream URL). Those
 * exist for a *mirrored* schedule item, whose file only lives on the primary's disk. A Controller's
 * schedule is its own local one, so routing it through the primary streams the wrong bytes — or none
 * at all, for an item id the primary has never seen.
 */
internal fun shouldUseRemoteContent(status: InstanceLinkStatus, role: InstanceLinkRole): Boolean =
    status == InstanceLinkStatus.CONNECTED && shouldMirrorRemoteOutput(role)

/**
 * Whether to replace this instance's backgrounds with the primary's — [shouldUseRemoteContent] plus
 * the explicit opt-in, which is off by default because backgrounds are usually venue-specific.
 */
internal fun shouldMirrorRemoteBackgrounds(
    status: InstanceLinkStatus,
    role: InstanceLinkRole,
    mirrorBackgrounds: Boolean
): Boolean = mirrorBackgrounds && shouldUseRemoteContent(status, role)

/**
 * Which URL a follower actually plays for a schedule item's media.
 *
 * A mirrored item's path usually only exists on the primary's disk, so it is streamed from there
 * ([remoteStreamUrl], null when there is nothing to stream from). A path that *does* resolve here —
 * a shared network drive, or the same layout on both machines — is played from disk instead: no
 * network in the path, and seeking a local file beats seeking an HTTP stream. A URL-type item is
 * already reachable from anywhere and is never rewritten.
 */
internal fun followerMediaUrl(mediaType: String, localUrl: String, remoteStreamUrl: String?): String =
    if (mediaType == Constants.MEDIA_TYPE_LOCAL && remoteStreamUrl != null && !File(localUrl).exists()) {
        remoteStreamUrl
    } else {
        localUrl
    }

/**
 * Downloads the primary's configured background image/video assets (only for slots it actually has
 * set — most churches only use one or two) into a local cache, then returns a [BackgroundSettings]
 * copy with every image/video path rewritten to the cached file. BiblePresenter/SongPresenter then
 * render it exactly like a local background — no changes needed in either presenter. Only called
 * when the follower opted in via InstanceLinkSettings.mirrorBackgrounds; colors/gradients/opacity/
 * type are plain values already carried by [remote] as-is, no transfer needed for those.
 */
internal suspend fun downloadMirroredBackgroundSettings(
    remote: BackgroundSettings,
    instanceLinkViewModel: InstanceLinkViewModel
): BackgroundSettings {
    suspend fun cache(slot: String, path: String, isVideo: Boolean): String {
        if (path.isBlank()) return path
        val ext = File(path).extension.ifBlank { if (isVideo) "mp4" else "jpg" }
        val kind = if (isVideo) "video" else "image"
        val cacheFile = File(instanceLinkBackgroundCacheDir, "$slot-$kind.$ext")
        if (!cacheFile.exists()) {
            val bytes = instanceLinkViewModel.fetchBackgroundAsset(slot, isVideo)
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "background_asset_fetch_failed",
                    mapOf("slot" to slot, "isVideo" to isVideo)
                )
                return ""
            }
            // Temp-file + rename — same cancellation-safety reasoning as the picture cache:
            // the surrounding effect can be restarted mid-download.
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(cacheFile)) tmp.delete()
            if (!cacheFile.exists()) return ""
        }
        return cacheFile.absolutePath
    }
    return remote.withoutCameras().copy(
        defaultBackgroundImage = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundImage, false),
        defaultBackgroundVideo = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundVideo, true),
        defaultLowerThirdBackgroundImage = cache(
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            remote.defaultLowerThirdBackgroundImage,
            false
        ),
        defaultLowerThirdBackgroundVideo = cache(
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            remote.defaultLowerThirdBackgroundVideo,
            true
        ),
        bibleBackground = remote.bibleBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundVideo, true)
        ),
        bibleLowerThirdBackground = remote.bibleLowerThirdBackground.copy(
            backgroundImage = cache(
                Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
                remote.bibleLowerThirdBackground.backgroundImage,
                false
            ),
            backgroundVideo = cache(
                Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
                remote.bibleLowerThirdBackground.backgroundVideo,
                true
            )
        ),
        songBackground = remote.songBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundVideo, true)
        ),
        songLowerThirdBackground = remote.songLowerThirdBackground.copy(
            backgroundImage = cache(
                Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
                remote.songLowerThirdBackground.backgroundImage,
                false
            ),
            backgroundVideo = cache(
                Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
                remote.songLowerThirdBackground.backgroundVideo,
                true
            )
        )
    )
}

/**
 * [this] with every camera background dropped.
 *
 * A picture or a clip is fetched and cached, so a mirrored one resolves here. A camera cannot be:
 * the primary's device path names the primary's hardware, and the danger is not that it fails to
 * open on the follower but that it **succeeds** — `avfoundation://0` is a camera on almost any
 * machine, just not the one that was chosen. Dropping it puts the follower on its own configured
 * background, which is the same thing `cache` does by returning "" when an asset cannot be had.
 */
private fun BackgroundSettings.withoutCameras(): BackgroundSettings = copy(
    defaultBackgroundCamera = CameraDeviceRef(),
    defaultLowerThirdBackgroundCamera = CameraDeviceRef(),
    bibleBackground = bibleBackground.copy(camera = CameraDeviceRef()),
    bibleLowerThirdBackground = bibleLowerThirdBackground.copy(camera = CameraDeviceRef()),
    songBackground = songBackground.copy(camera = CameraDeviceRef()),
    songLowerThirdBackground = songLowerThirdBackground.copy(camera = CameraDeviceRef()),
)

/**
 * Applies a [LiveStateDto] received from another instance's CompanionServer to this instance's own
 * [PresenterManager], so an InstanceLink follower mirrors the primary's output. Bible verses, song
 * sections, announcements, website content, pictures, lower thirds (fetched by preset name),
 * media (streamed from the primary, no position sync — the DTO carries no transport state),
 * canvas scenes (matched by id against this instance's own local scenes), Q&A questions, and
 * Strong's dictionary entries (carried whole in the DTO) are all mirrored; presentations use
 * their own richer dedicated broadcast instead (see the remotePresentationSlide collector in
 * the caller). STT stays mode-only — there is no caption feed to mirror.
 */
internal suspend fun applyRemoteLiveState(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    bibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    localPrimaryBible: Bible? = null,
    /** This instance's own saved scenes — CANVAS mirroring is id-match only (no content endpoint). */
    localScenes: List<Scene> = emptyList(),
    /** Loads + starts media playback locally (MediaViewModel stays owned by its composable). */
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)? = null
) {
    val mode = runCatching { Presenting.valueOf(state.contentType) }.getOrNull()
    if (mode == null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to state.contentType, "resolved" to false, "reason" to "unknown_content_type")
        )
        return
    }
    when (mode) {
        Presenting.BIBLE ->
            applyRemoteBible(state, presenterManager, bibleSyncMode, localPrimaryBible)
        Presenting.LYRICS -> if (state.songTitle != null) {
            presenterManager.setLyricSection(
                LyricSection(
                    title = state.songTitle,
                    songNumber = state.songNumber ?: 0,
                    type = state.sectionType ?: "",
                    lines = state.lines ?: emptyList()
                )
            )
            presenterManager.setSongDisplaySectionIndex(state.songSectionIndex ?: -1)
            presenterManager.setSongDisplayLineIndex(state.songLineIndex ?: -1)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "LYRICS", "resolved" to true)
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "LYRICS", "resolved" to false, "reason" to "no_song_title_in_state")
            )
        }
        Presenting.ANNOUNCEMENTS -> {
            val text = state.announcementText
            if (text != null) {
                presenterManager.setAnnouncementText(text)
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER,
                    "apply_live_state",
                    mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to true)
                )
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to false, "reason" to "no_text_in_state")
                )
            }
        }
        Presenting.WEBSITE -> {
            state.websiteUrl?.let { presenterManager.setWebsiteUrl(it) }
            state.websiteTitle?.let { presenterManager.setWebPageTitle(it) }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "WEBSITE", "resolved" to (state.websiteUrl != null))
            )
        }
        Presenting.PICTURES ->
            if (!applyRemotePictures(state, presenterManager, instanceLinkViewModel)) return
        Presenting.LOWER_THIRD -> applyRemoteLowerThird(state, presenterManager, instanceLinkViewModel)
        Presenting.MEDIA -> applyRemoteMedia(state, presenterManager, instanceLinkViewModel, onPlayRemoteMedia)
        Presenting.CANVAS -> applyRemoteCanvas(state, presenterManager, localScenes)
        Presenting.QA -> applyRemoteQa(state, presenterManager)
        Presenting.DICTIONARY -> applyRemoteDictionary(state, presenterManager)
        else -> {
            // presentation: mirrored via its own dedicated broadcast (remotePresentationSlide
            // collector); stt: no caption feed exists to mirror. Mode still switches below.
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to mode.name, "resolved" to false, "reason" to "mode_only_no_feed")
            )
        }
    }
    presenterManager.setPresentingMode(mode)
    presenterManager.setShowPresenterWindow(true)
}

internal fun qaActionType(action: String): RemoteEventType = when (action) {
    "edit"    -> RemoteEventType.QA_EDIT
    "delete"  -> RemoteEventType.QA_DELETE
    "approve" -> RemoteEventType.QA_APPROVE
    "deny"    -> RemoteEventType.QA_DENY
    "done"    -> RemoteEventType.QA_DONE
    "display"       -> RemoteEventType.QA_DISPLAY
    "clear-display" -> RemoteEventType.QA_CLEAR_DISPLAY
    else            -> RemoteEventType.QA_ADD
}

/** Returns a (title, detail) pair describing a ScheduleItem for the remote event banner. */
internal fun remoteEventLabel(item: ScheduleItem): Pair<String, String> = when (item) {
    is ScheduleItem.SongItem -> "${item.songNumber} - ${item.title}" to item.songbook
    is ScheduleItem.BibleVerseItem -> {
        val ref = if (item.verseRange.isNotEmpty()) "${item.bookName} ${item.chapter}:${item.verseRange}"
        else "${item.bookName} ${item.chapter}:${item.verseNumber}"
        ref to item.verseText.take(SUMMARY_PREVIEW_CHARS)
    }

    is ScheduleItem.PictureItem -> item.folderName to "${item.imageCount} images"
    is ScheduleItem.PresentationItem -> item.fileName to item.fileType.uppercase()
    is ScheduleItem.MediaItem -> item.mediaTitle to item.mediaType
    is ScheduleItem.LabelItem -> item.text.take(SUMMARY_PREVIEW_CHARS) to ""
    is ScheduleItem.AnnouncementItem -> item.text.take(SUMMARY_PREVIEW_CHARS) to ""
    is ScheduleItem.LowerThirdItem -> item.presetLabel to ""
    is ScheduleItem.WebsiteItem -> item.title to item.url
    is ScheduleItem.SceneItem -> item.sceneName to "Scene"
    is ScheduleItem.DictionaryItem -> item.word to item.number
}

/**
 * Returns a (title, detail) pair summarising a batch add-to-schedule request for the operator's
 * approval prompt and activity toast.
 *
 * A single item is described by [remoteEventLabel] rather than as "1 items" — the operator is being
 * asked to approve something specific and a count tells them nothing.
 *
 * The detail lists the first three items joined by " · ", with " …" appended only when a fourth
 * exists; exactly three items get no ellipsis.
 *
 * Deliberately does **not** reuse [remoteEventLabel] for the per-item detail text: this renders a
 * song with an en dash and a verse without its [ScheduleItem.BibleVerseItem.verseRange], because the
 * detail is a compact one-line list rather than a banner heading.
 */
internal fun batchEventSummary(items: List<ScheduleItem>): Pair<String, String> {
    val count = items.size
    val title = if (count == 1) remoteEventLabel(items.first()).first else "$count items"
    val detail = items.take(3).joinToString(" · ") { item ->
        when (item) {
            is ScheduleItem.BibleVerseItem -> "${item.bookName} ${item.chapter}:${item.verseNumber}"
            is ScheduleItem.SongItem -> "${item.songNumber} – ${item.title}"
            else -> item.displayText.take(30)
        }
    }.let { if (count > 3) "$it …" else it }
    return title to detail
}

/**
 * Copies a remotely-projected [ScheduleItem.AnnouncementItem]'s style into
 * [AppSettings.announcementsSettings] so the live output renders with the
 * announcement's own colour / font / animation rather than the desktop's
 * current settings.
 */
internal fun AppSettings.withAnnouncement(item: ScheduleItem.AnnouncementItem): AppSettings =
    copy(
        announcementsSettings = announcementsSettings.copy(
            text                = item.text,
            textColor           = item.textColor,
            backgroundColor     = item.backgroundColor,
            fontSize            = item.fontSize,
            fontType            = item.fontType,
            bold                = item.bold,
            italic              = item.italic,
            underline           = item.underline,
            shadow              = item.shadow,
            shadowColor         = item.shadowColor,
            shadowSize          = item.shadowSize,
            shadowOpacity       = item.shadowOpacity,
            horizontalAlignment = item.horizontalAlignment,
            position            = item.position,
            animationType       = item.animationType,
            animationDuration   = item.animationDuration,
            loopCount           = item.loopCount,
            timerHours          = item.timerHours,
            timerMinutes        = item.timerMinutes,
            timerSeconds        = item.timerSeconds,
            timerTextColor      = item.timerTextColor,
            timerExpiredText    = item.timerExpiredText,
            timerMode           = item.timerMode,
            targetHour          = item.targetHour,
            targetMinute        = item.targetMinute,
            targetSecond        = item.targetSecond,
            liveClockFormat     = item.liveClockFormat
        )
    )

/**
 * Hands a remotely-projected item to whichever tab has to load its real content, and reports whether
 * any tab was asked.
 *
 * [executeProjectItem] adds the item to the schedule and flips `presentingMode`, but deliberately
 * does **not** push picture or slide content itself — the tab that owns that content does, driven by
 * these flows. So a type missing from this `when` goes live as an empty screen: the mode changes and
 * nothing loads.
 *
 * **Only the project path drives all three.** The add-to-schedule path
 * ([addScheduleItem]) navigates the Songs tab and nothing else, on purpose — adding a picture to the
 * schedule must not hijack the Pictures tab away from what the operator is showing. Merging the two
 * would do exactly that, which is why this is a separate function rather than a flag on that one.
 *
 * Returns false for every other type, so a test can pin "this drives no tab" as a positive result
 * rather than as the absence of an emission.
 */
internal suspend fun emitRemoteTabSelection(
    item: ScheduleItem,
    songFlow: MutableSharedFlow<ScheduleItem.SongItem>,
    pictureFlow: MutableSharedFlow<ScheduleItem.PictureItem>,
    presentationFlow: MutableSharedFlow<ScheduleItem.PresentationItem>,
): Boolean = when (item) {
    is ScheduleItem.SongItem -> { songFlow.emit(item); true }
    is ScheduleItem.PictureItem -> { pictureFlow.emit(item); true }
    is ScheduleItem.PresentationItem -> { presentationFlow.emit(item); true }
    else -> false
}

/**
 * Executes a project request — adds to schedule and sets presenter state.
 * Fixes the original bug where SongItem projection never selected the song in the Songs tab.
 */
internal fun executeProjectItem(
    item: ScheduleItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager? = null
) {
    when (item) {
        is ScheduleItem.SongItem -> {
            // Add to schedule AND select the song so the Songs tab navigates to it
            scheduleActions.addSong(item.songNumber, item.title, item.songbook, item.songId)
            presenterManager.setLyricSection(
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
            presenterManager.setPresentingMode(Presenting.LYRICS)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.BibleVerseItem -> {
            scheduleActions.addBibleVerse(
                item.bookName,
                item.chapter,
                item.verseNumber,
                item.verseText,
                item.verseRange,
                item.bookId
            )
            presenterManager.setSelectedVerses(
                listOf(
                    SelectedVerse(
                        bookName = item.bookName,
                        chapter = item.chapter,
                        verseNumber = item.verseNumber,
                        verseText = item.verseText,
                        verseRange = item.verseRange
                    )
                )
            )
            presenterManager.setPresentingMode(Presenting.BIBLE)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.PictureItem -> {
            // Deliberately does NOT call setSelectedImagePath(item.folderPath) — that setter expects
            // a single image FILE path, not a folder, and a folder path can never render. The actual
            // image push happens via remoteSelectPictureFlow in main.kt (MainDesktop loads the folder
            // into PicturesViewModel, whose own reactive effect pushes the current image once loaded).
            scheduleActions.addPicture(item.folderPath, item.folderName, item.imageCount)
            presenterManager.setPresentingMode(Presenting.PICTURES)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.PresentationItem -> {
            scheduleActions.addPresentation(item.filePath, item.fileName, item.slideCount, item.fileType)
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.MediaItem -> {
            scheduleActions.addMedia(item.mediaUrl, item.mediaTitle, item.mediaType)
            presenterManager.setCurrentMedia(item.mediaUrl, item.mediaType)
            presenterManager.setPresentingMode(Presenting.MEDIA)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.DictionaryItem -> {
            presenterManager.setDisplayedDictionaryEntry(
                StrongsEntry(
                    number = item.number,
                    word = item.word,
                    transliteration = item.transliteration,
                    pronunciation = "",
                    definition = item.definition
                )
            )
            presenterManager.setPresentingMode(Presenting.DICTIONARY)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.AnnouncementItem -> {
            if (item.isTimer) {
                val total = item.timerHours * 3600 + item.timerMinutes * 60 + item.timerSeconds
                when (item.timerMode) {
                    Constants.TIMER_MODE_COUNT_UP -> presenterManager.startAnnouncementCountUp(0)
                    Constants.TIMER_MODE_CLOCK -> presenterManager.startAnnouncementSpecificTime(
                        item.targetHour,
                        item.targetMinute,
                        item.targetSecond
                    )
                    Constants.TIMER_MODE_CLOCK_DISPLAY -> presenterManager.startAnnouncementClockDisplay(
                        item.liveClockFormat
                    )
                    else -> presenterManager.startAnnouncementCountdown(total, item.timerExpiredText)
                }
                presenterManager.setAnnouncementTickerLive(true)
            } else {
                presenterManager.setAnnouncementText(item.text)
            }
            presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.WebsiteItem -> {
            scheduleActions.addWebsite(item.url, item.title)
            presenterManager.setWebsiteUrl(item.url)
            presenterManager.setWebPageTitle(item.title)
            presenterManager.setPresentingMode(Presenting.WEBSITE)
            presenterManager.setShowPresenterWindow(true)
        }

        else -> Unit
    }
}

/**
 * Adds a remotely-requested [item] to the schedule, reporting whether anything was added.
 *
 * The same eight-way dispatch was written out four times in `main.kt` — twice for the single-add
 * path and twice for the batch path — and the batch copies were missing the dictionary,
 * announcement and website branches. `RemoteItemDto.toScheduleItem` produces all three and
 * `POST /api/schedule/add-batch` answers `{"ok":true,"added":N}` counting every item it parsed, so
 * a batch containing one of them told the phone it had been added while nothing reached the
 * schedule. Having one dispatch is what stops the two paths drifting again.
 *
 * [onSongAdded] is how the Songs tab is told to navigate to the song it just received; the caller
 * supplies it because the flow it emits on is scoped to the composable.
 *
 * Deliberately **not** shared with [executeProjectItem]: that path drives dictionary and
 * announcement items onto the presenter *without* adding them to the schedule, so routing it
 * through here would start adding a row every time one is projected.
 *
 * @return true when a schedule action fired; false for the types that are not schedule content
 *         (label, lower third — and scene, which has an `addScene` action no remote path uses).
 */
internal fun addScheduleItem(
    item: ScheduleItem,
    scheduleActions: ScheduleActions,
    onSongAdded: (ScheduleItem.SongItem) -> Unit = {}
): Boolean {
    when (item) {
        is ScheduleItem.SongItem -> {
            scheduleActions.addSong(item.songNumber, item.title, item.songbook, item.songId)
            onSongAdded(item)
        }

        is ScheduleItem.BibleVerseItem -> scheduleActions.addBibleVerse(
            item.bookName,
            item.chapter,
            item.verseNumber,
            item.verseText,
            item.verseRange,
            item.bookId
        )

        is ScheduleItem.PresentationItem -> scheduleActions.addPresentation(
            item.filePath,
            item.fileName,
            item.slideCount,
            item.fileType
        )

        is ScheduleItem.PictureItem -> scheduleActions.addPicture(
            item.folderPath,
            item.folderName,
            item.imageCount
        )

        is ScheduleItem.MediaItem -> scheduleActions.addMedia(
            item.mediaUrl,
            item.mediaTitle,
            item.mediaType
        )

        is ScheduleItem.DictionaryItem -> scheduleActions.addDictionary(
            item.number,
            item.word,
            item.transliteration,
            item.definition
        )

        is ScheduleItem.AnnouncementItem -> scheduleActions.addAnnouncement(item)

        is ScheduleItem.WebsiteItem -> scheduleActions.addWebsite(item.url, item.title)

        else -> return false
    }
    return true
}


/** The BIBLE half of [applyRemoteLiveState]: either this instance's own wording, or the primary's. */
private fun applyRemoteBible(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    bibleSyncMode: BibleSyncMode,
    localPrimaryBible: Bible?,
) {
    val codeBook = state.verseCodeBook
    val codeChapter = state.verseCodeChapter
    val codeVerse = state.verseCodeVerse
    val hasFullCode = codeBook != null && codeChapter != null && codeVerse != null
    if (bibleSyncMode == BibleSyncMode.REFERENCE_ONLY && hasFullCode) {
        // Reference-only: never touch a downloaded file — resolve the SAME canonical verse in
        // this instance's own independently-configured (possibly different-language) Bible via
        // Bible.getVerseDetailsByCode, so the follower shows its own translation's wording, not
        // the primary's. If this Bible has no verse at that code (versification mismatch, or no
        // local bible configured), there's nothing sensible to show — quietly no-op.
        val result = localPrimaryBible?.getVerseDetailsByCode(codeBook, codeChapter, codeVerse)
        if (result != null) {
            presenterManager.setSelectedVerses(
                listOf(
                    SelectedVerse(
                        bibleAbbreviation = localPrimaryBible.getBibleAbbreviation(),
                        bibleName = localPrimaryBible.getBibleTitle(),
                        bookName = result.bookName,
                        chapter = result.displayChapter,
                        verseNumber = result.displayVerse,
                        verseText = result.verseText,
                        verseRange = state.verseRange ?: ""
                    )
                )
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "reference_only")
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf(
                    "contentType" to "BIBLE", "resolved" to false, "mode" to "reference_only",
                    "reason" to if (localPrimaryBible == null) "no_local_bible_loaded" else "verse_code_not_found"
                )
            )
        }
    } else if (state.bookName != null) {
        // Full replica: the primary's own wording, verbatim.
        // setSelectedVerses (plural), not setSelectedVerse — only the plural setter feeds the
        // selectedVerses -> displayedVerses bridging LaunchedEffect that BiblePresenter actually
        // renders from; the singular setter alone leaves the screen blank despite the mode
        // correctly switching to BIBLE.
        presenterManager.setSelectedVerses(
            listOf(
                SelectedVerse(
                    bookName = state.bookName,
                    chapter = state.chapter ?: 0,
                    verseNumber = state.verseNumber ?: 0,
                    verseText = state.verseText ?: "",
                    verseRange = state.verseRange ?: ""
                )
            )
        )
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "full_replica")
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "BIBLE", "resolved" to false, "reason" to "no_book_name_in_state")
        )
    }
}

private suspend fun applyRemotePictures(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
): Boolean {
    val folderId = state.pictureFolderId
    val index = state.pictureIndex
    if (folderId != null && index != null) {
        val cacheFile = File(instanceLinkPictureCacheDir, "${folderId}_$index.jpg")
        if (!cacheFile.exists() && !cachePictureImage(cacheFile, folderId, index, instanceLinkViewModel)) {
            return false
        }
        presenterManager.setSelectedImagePath(cacheFile.absolutePath)
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER,
            "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to true)
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "missing_folder_id_or_index")
        )
    }
    return true
}

private suspend fun applyRemoteMedia(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)?,
) {
    // No position/transport sync in this pass: LiveStateDto carries which media is live,
    // not where playback is — a follower starts the same media from the top.
    val mediaType = state.mediaType
    val streamUrl = state.mediaId?.let { instanceLinkViewModel.mediaStreamUrl(it) }
    when {
        mediaType == Constants.MEDIA_TYPE_URL && state.mediaUrl != null && onPlayRemoteMedia != null -> {
            onPlayRemoteMedia(state.mediaUrl, mediaType)
            presenterManager.setCurrentMedia(state.mediaUrl, mediaType)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "url", "positionSync" to false)
            )
        }
        streamUrl != null && onPlayRemoteMedia != null -> {
            val type = mediaType ?: Constants.MEDIA_TYPE_LOCAL
            onPlayRemoteMedia(streamUrl, type)
            presenterManager.setCurrentMedia(streamUrl, type)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "stream", "positionSync" to false)
            )
        }
        else -> InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            // Media launched outside the primary's schedule has no stream mapping
            // (LiveStateDto.mediaId comes from its schedule-item → path map).
            mapOf("contentType" to "MEDIA", "resolved" to false, "reason" to "no_media_id")
        )
    }
}

private suspend fun applyRemoteLowerThird(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
) {
    val name = state.lowerThirdName
    if (name != null) {
        val bytes = instanceLinkViewModel.fetchLowerThirdJson(name)
        if (bytes != null) {
            presenterManager.setLottieContent(
                String(bytes, Charsets.UTF_8), pauseAtFrame = false, pauseFrame = -1f,
                pauseDurationMs = 2000L, presetName = name
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "LOWER_THIRD", "resolved" to true)
            )
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "fetch_failed")
            )
        }
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "no_name_in_state")
        )
    }
}


private fun applyRemoteCanvas(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    localScenes: List<Scene>,
) {
    val scene = state.sceneId?.let { id -> localScenes.find { it.id == id } }
    if (scene != null) {
        presenterManager.setActiveScene(scene)
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "CANVAS", "resolved" to true)
        )
    } else {
        // Id-match only: scene content isn't fetchable over the link — mirroring works
        // when the same scenes.json exists on both instances. Mode still switches.
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf(
                "contentType" to "CANVAS", "resolved" to false,
                "reason" to "scene_not_found_locally", "sceneName" to state.sceneName
            )
        )
    }
}

private fun applyRemoteQa(
    state: LiveStateDto,
    presenterManager: PresenterManager,
) {
    val questionId = state.questionId
    val questionText = state.questionText
    if (questionId != null && questionText != null) {
        presenterManager.setDisplayedQuestion(
            Question(
                id = questionId,
                text = questionText,
                timestamp = System.currentTimeMillis(),
                status = QuestionStatus.APPROVED
            )
        )
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER,
            "apply_live_state",
            mapOf("contentType" to "QA", "resolved" to true)
        )
    } else {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "QA", "resolved" to false, "reason" to "no_question_in_state")
        )
    }
}

private fun applyRemoteDictionary(
    state: LiveStateDto,
    presenterManager: PresenterManager,
) {
    val entry = state.dictionaryEntry
    val word = state.dictionaryWord
    when {
        entry != null -> {
            presenterManager.setDisplayedDictionaryEntry(entry)
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER,
                "apply_live_state",
                mapOf("contentType" to "DICTIONARY", "resolved" to true)
            )
        }
        word != null -> {
            // Old primary that doesn't carry the full entry — show what we have.
            presenterManager.setDisplayedDictionaryEntry(
                StrongsEntry(number = "", word = word, transliteration = "", pronunciation = "", definition = "")
            )
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "DICTIONARY", "resolved" to true, "partial" to true)
            )
        }
        else -> InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "DICTIONARY", "resolved" to false, "reason" to "no_word_in_state")
        )
    }
}

/** Fetches one picture into the follower's cache; false once the failure has been logged. */
private suspend fun cachePictureImage(
    cacheFile: File,
    folderId: String,
    index: Int,
    instanceLinkViewModel: InstanceLinkViewModel,
): Boolean {
    val bytes = instanceLinkViewModel.fetchPictureImageBytes(folderId, index)
    if (bytes == null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "fetch_failed")
        )
        return false
    }
    // Temp-file + rename: this apply can be cancelled mid-write by a newer live state
    // (collectLatest) — a truncated file must never land under the final name, or the exists()
    // cache gate would trust it forever.
    val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(cacheFile)) tmp.delete()
    if (cacheFile.exists()) return true
    InstanceLinkLogger.log(
        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
        mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "cache_write_failed")
    )
    return false
}
