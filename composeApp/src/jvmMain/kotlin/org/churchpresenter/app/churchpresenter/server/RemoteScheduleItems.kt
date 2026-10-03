package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.core.models.songs.SongItem
import kotlinx.coroutines.flow.MutableSharedFlow
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.core.models.schedule.ScheduleItem

private const val SUMMARY_PREVIEW_CHARS = 60

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
    is ScheduleItem.CueItem -> item.displayText to item.absoluteTime
    is ScheduleItem.MinistryItem -> item.title to item.detail
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
    mediaFlow: MutableSharedFlow<ScheduleItem.MediaItem>,
): Boolean = when (item) {
    is ScheduleItem.SongItem -> { songFlow.emit(item); true }
    is ScheduleItem.PictureItem -> { pictureFlow.emit(item); true }
    is ScheduleItem.PresentationItem -> { presentationFlow.emit(item); true }
    // Without this a projected video set the presenter to MEDIA mode and played nothing: the file
    // is loaded by the Media tab, which only learns of it by being handed the item.
    is ScheduleItem.MediaItem -> { mediaFlow.emit(item); true }
    else -> false
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
 * @return true when a schedule action fired; false for the types a plain remote add does not
 *         carry (label, lower third, scene) unless [wholePlan] asks for them.
 */
internal fun addScheduleItem(
    item: ScheduleItem,
    scheduleActions: ScheduleActions,
    /**
     * Whether the row types no remote path sends — a section heading, a lower third, a scene — are
     * added too.
     *
     * False for every remote path, which is exactly what it has always done: those arrive one item
     * at a time from a phone, and a heading is not something a remote client adds. The Calendar
     * Manager passes true, because a planned run of show is loaded **whole** — its headings are
     * part of the plan, and a scene that was copied out of the Schedule tab has to survive the
     * trip back into it.
     */
    wholePlan: Boolean = false,
    onSongAdded: (ScheduleItem.SongItem) -> Unit = {}
): Boolean {
    // One guard rather than three inside the branches below, which would put this function over
    // detekt's ReturnCount limit.
    if (!wholePlan && item.isPlanOnly()) return false
    when (item) {
        is ScheduleItem.LabelItem ->
            scheduleActions.addLabel(item.text, item.textColor, item.backgroundColor)

        is ScheduleItem.LowerThirdItem -> scheduleActions.addLowerThird(
            item.presetId,
            item.presetLabel,
            item.pauseAtFrame,
            item.pauseDurationMs,
        )

        is ScheduleItem.SceneItem -> scheduleActions.addScene(item.sceneId, item.sceneName)

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
            item.mediaType,
            item.subtitleUrl
        )

        is ScheduleItem.DictionaryItem -> scheduleActions.addDictionary(
            item.number,
            item.word,
            item.transliteration,
            item.definition
        )

        is ScheduleItem.AnnouncementItem -> scheduleActions.addAnnouncement(item)

        is ScheduleItem.WebsiteItem -> scheduleActions.addWebsite(item.url, item.title)

        is ScheduleItem.CueItem -> scheduleActions.addCue(item)

        else -> return false
    }
    return true
}

/**
 * The row types only a whole plan carries — never sent one at a time by a remote client.
 *
 * A heading and a scene are structure a phone does not add, and a lower third is triggered from the
 * Lower Third tab rather than queued remotely. [addScheduleItem] skips all three unless it is being
 * asked to load a plan.
 */
private fun ScheduleItem.isPlanOnly(): Boolean =
    this is ScheduleItem.LabelItem ||
        this is ScheduleItem.LowerThirdItem ||
        this is ScheduleItem.SceneItem ||
        this is ScheduleItem.CueItem
