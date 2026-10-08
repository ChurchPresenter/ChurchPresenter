package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import org.churchpresenter.app.churchpresenter.remote.emitRemoteTabSelection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.liveoutput.shouldShowPresenterWindowFor
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_added_schedule
import org.churchpresenter.strings.generated.resources.helper_done_announcement
import org.churchpresenter.strings.generated.resources.helper_done_announcement_cued
import org.churchpresenter.strings.generated.resources.helper_done_countdown
import org.churchpresenter.strings.generated.resources.helper_live_announcement
import org.churchpresenter.strings.generated.resources.helper_live_countdown
import org.churchpresenter.strings.generated.resources.helper_live_nothing
import org.churchpresenter.strings.generated.resources.helper_live_now
import org.churchpresenter.strings.generated.resources.helper_schedule_at_end
import org.churchpresenter.strings.generated.resources.helper_schedule_at_start
import org.churchpresenter.strings.generated.resources.helper_schedule_empty
import org.churchpresenter.strings.generated.resources.helper_schedule_live
import org.churchpresenter.strings.generated.resources.helper_schedule_not_found
import org.churchpresenter.strings.generated.resources.helper_schedule_ready
import org.churchpresenter.strings.generated.resources.helper_schedule_selected
import org.churchpresenter.strings.generated.resources.helper_song_found
import org.churchpresenter.strings.generated.resources.helper_song_not_found
import org.churchpresenter.strings.generated.resources.helper_songs_not_loaded
import org.churchpresenter.strings.generated.resources.helper_version
import org.churchpresenter.strings.generated.resources.timer_expired
import org.churchpresenter.updater.UpdateChecker
import org.jetbrains.compose.resources.getString
import java.util.UUID

// The helper's jobs that do something outright, the way a phone remote or a Schedule row would.

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60

/** Puts [text] — or, for a timer, a countdown of [minutes] — on screen as an announcement. */
internal fun AppRootState.helperAnnounce(text: String, minutes: Int? = null): ActionOutcome {
    val item = ScheduleItem.AnnouncementItem(
        id = UUID.randomUUID().toString(),
        text = text,
        isTimer = minutes != null,
        timerHours = (minutes ?: 0) / MINUTES_PER_HOUR,
        timerMinutes = (minutes ?: 0) % MINUTES_PER_HOUR,
        timerMode = TimerModes.DURATION,
    )
    showAnnouncement(item)
    return when {
        minutes != null -> ActionOutcome.Done(helperText(Res.string.helper_done_countdown, minutes))
        appSettings.projectionSettings.previewModeEnabled ->
            ActionOutcome.Done(helperText(Res.string.helper_done_announcement_cued))
        else -> ActionOutcome.Done(helperText(Res.string.helper_done_announcement))
    }
}

/** The way a Schedule announcement row goes up, so the Announcements tab shows the same thing. */
private fun AppRootState.showAnnouncement(item: ScheduleItem.AnnouncementItem) {
    coroutineScope.launch {
        presentAnnouncementItem(
            item,
            timerExpiredDefaultLabel = getString(Res.string.timer_expired),
            presenterManager = presenterManager,
            onSettingsChange = { edit ->
                appSettings = edit(appSettings)
                settingsManager.saveSettings(appSettings)
            },
            presenting = { mode ->
                presenterManager.previewBus.present(mode)
                if (shouldShowPresenterWindowFor(mode)) presenterManager.setShowPresenterWindow(true)
            },
        )
    }
}

/** The library song [query] names: its number first, then its title exactly, then a title starting or containing it. */
internal fun findHelperSong(songs: List<SongItem>, query: String): SongItem? {
    val q = query.trim().lowercase()
    return songs.firstOrNull { it.number == q } ?: songs.firstOrNull { it.title.lowercase() == q }
        ?: songs.firstOrNull { it.title.lowercase().startsWith(q) }
        ?: songs.firstOrNull { it.title.lowercase().contains(q) }
}

private fun SongItem.asScheduleItem() = ScheduleItem.SongItem(
    id = UUID.randomUUID().toString(),
    songNumber = number.toIntOrNull() ?: 0,
    title = title,
    songbook = songbook,
    songId = songId,
)

/** The song opened on the Songs tab and left there, for the operator to put live. */
private fun readyNotLive(song: ScheduleItem.SongItem) = RemoteSongSelection(song, goLive = false, source = "helper")

private fun SongItem.label(): String = if (number.isNotBlank()) "$number. $title" else title

/** Finds the song and opens it on the Songs tab, ready for Go Live — nothing goes on screen. */
internal fun AppRootState.helperFindSong(query: String): ActionOutcome {
    val song = lookUpSong(query) ?: return songMissing(query)
    remoteSelectSongFlow.tryEmit(readyNotLive(song.asScheduleItem()))
    return ActionOutcome.Done(helperText(Res.string.helper_song_found, song.label()))
}

internal fun AppRootState.helperAddSongToSchedule(query: String): ActionOutcome {
    val song = lookUpSong(query) ?: return songMissing(query)
    currentScheduleActions.addSong(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
    return ActionOutcome.Done(helperText(Res.string.helper_done_added_schedule, song.label()))
}

private fun AppRootState.lookUpSong(query: String): SongItem? = findHelperSong(helperSongs, query)

private fun AppRootState.songMissing(query: String): ActionOutcome =
    if (helperSongCount == null) {
        ActionOutcome.Refused(helperText(Res.string.helper_songs_not_loaded))
    } else {
        ActionOutcome.Refused(helperText(Res.string.helper_song_not_found, query))
    }

/** Adds the verse with no text and no book id: the Schedule matches the name against the Bible, as for a phone. */
internal fun AppRootState.helperAddVerseToSchedule(action: HelperAction.AddVerseToSchedule): ActionOutcome {
    val range = if (action.lastVerse > action.verse) "${action.verse}-${action.lastVerse}" else ""
    currentScheduleActions.addBibleVerse(action.book, action.chapter, action.verse, "", range, 0)
    return ActionOutcome.Done(helperText(Res.string.helper_done_added_schedule, action.display))
}

/** Rows that show something; labels head sections and cues fire on their own. */
private fun ScheduleItem.isShowable() = this !is ScheduleItem.LabelItem && this !is ScheduleItem.CueItem

/** The schedule's next or previous showable row from the selected one, made ready to show. */
internal fun AppRootState.helperScheduleStep(forward: Boolean): ActionOutcome {
    val rows = currentScheduleItems.filter { it.isShowable() }
    if (rows.isEmpty()) return ActionOutcome.Refused(helperText(Res.string.helper_schedule_empty))
    val at = rows.indexOfFirst { it.id == selectedScheduleItemId }
    val target = when {
        at < 0 -> if (forward) rows.first() else rows.last()
        forward -> rows.getOrNull(at + 1)
            ?: return ActionOutcome.Refused(helperText(Res.string.helper_schedule_at_end))
        else -> rows.getOrNull(at - 1)
            ?: return ActionOutcome.Refused(helperText(Res.string.helper_schedule_at_start))
    }
    return readyScheduleRow(target)
}

internal fun AppRootState.helperScheduleGoTo(name: String): ActionOutcome {
    val q = name.lowercase()
    val target = currentScheduleItems.filter { it.isShowable() }.firstOrNull { it.displayText.lowercase().contains(q) }
        ?: return ActionOutcome.Refused(helperText(Res.string.helper_schedule_not_found, name))
    return readyScheduleRow(target)
}

/**
 * Selects [item] and readies it as its own tab would: a song, pictures, a deck or a video are loaded
 * for Go Live; a verse, an announcement or a scene go straight up, as a phone's would. Nothing is
 * added to the schedule — it is already there.
 */
private fun AppRootState.readyScheduleRow(item: ScheduleItem): ActionOutcome {
    currentScheduleActions.selectItem(item.id)
    val name = item.displayText
    return when (item) {
        is ScheduleItem.BibleVerseItem -> {
            companionServer.onSelectBibleVerse.tryEmit(
                SelectBibleVerseRequest(
                    bookName = item.bookName,
                    chapter = item.chapter,
                    verseNumber = item.verseNumber,
                    verseRange = item.verseRange,
                ),
            )
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        is ScheduleItem.AnnouncementItem -> {
            showAnnouncement(item)
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        is ScheduleItem.SceneItem -> {
            currentScheduleActions.presentScene(item.sceneId)
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        // Not through the tab selection: that puts a song live, and here it waits for Go Live.
        is ScheduleItem.SongItem -> {
            remoteSelectSongFlow.tryEmit(readyNotLive(item))
            ActionOutcome.Done(helperText(Res.string.helper_schedule_ready, name))
        }
        is ScheduleItem.PictureItem, is ScheduleItem.PresentationItem, is ScheduleItem.MediaItem -> {
            coroutineScope.launch {
                emitRemoteTabSelection(
                    item, remoteSelectSongFlow, remoteSelectPictureFlow, remoteSelectPresentationFlow,
                    remoteSelectMediaFlow,
                )
            }
            ActionOutcome.Done(helperText(Res.string.helper_schedule_ready, name))
        }
        else -> ActionOutcome.Done(helperText(Res.string.helper_schedule_selected, name))
    }
}

/** What each live layer shows, in words: the verse, the song, the announcement, or the tab it is from. */
internal fun AppRootState.helperWhatsLive(): ActionOutcome {
    val live = presenterManager.liveContent.value
    if (live.isEmpty()) return ActionOutcome.Done(helperText(Res.string.helper_live_nothing))
    val parts = live.mapNotNull { mode -> describeLive(mode) }
    return ActionOutcome.Done(helperText(Res.string.helper_live_now, HelperText.Joined(parts)))
}

private fun AppRootState.describeLive(mode: Presenting): HelperText? = when (mode) {
    Presenting.NONE -> null
    Presenting.BIBLE -> presenterManager.displayedVerses.value.takeIf { it.isNotEmpty() }?.let { verses ->
        val first = verses.first()
        val last = verses.last()
        val span = if (verses.size > 1) "${first.verseNumber}-${last.verseNumber}" else "${first.verseNumber}"
        HelperText.Plain("${first.bookName} ${first.chapter}:$span")
    } ?: helperTabName(Tabs.BIBLE)
    Presenting.LYRICS -> presenterManager.displayedLyricSection.value.title.takeIf { it.isNotBlank() }
        ?.let { HelperText.Plain(it) } ?: helperTabName(Tabs.SONGS)
    Presenting.ANNOUNCEMENTS -> if (presenterManager.timerRunning.value) {
        val left = presenterManager.timerRemainingSeconds.value
        helperText(Res.string.helper_live_countdown, "%d:%02d".format(left / SECONDS_PER_MINUTE, left % SECONDS_PER_MINUTE))
    } else {
        helperText(Res.string.helper_live_announcement, presenterManager.announcementText.value)
    }
    Presenting.PICTURES -> helperTabName(Tabs.PICTURES)
    Presenting.PRESENTATION -> helperTabName(Tabs.PRESENTATION)
    Presenting.MEDIA -> helperTabName(Tabs.MEDIA)
    Presenting.LOWER_THIRD -> helperTabName(Tabs.LOWER_THIRD)
    Presenting.WEBSITE -> helperTabName(Tabs.WEB)
    Presenting.CANVAS -> helperTabName(Tabs.CANVAS)
    Presenting.QA -> helperTabName(Tabs.QA)
    Presenting.STT -> helperTabName(Tabs.STT)
    Presenting.DICTIONARY -> helperTabName(Tabs.DICTIONARY)
}

/** Says the version, then checks the way Help → Check for Updates does, whose window gives the answer. */
internal fun AppRootState.helperCheckForUpdates(): ActionOutcome {
    coroutineScope.launch {
        pendingUpdateResult = UpdateChecker.checkForUpdate(includePrereleases = appSettings.participateInPrereleases)
        pendingUpdateCheckWasManual = true
        appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
        settingsManager.saveSettings(appSettings)
    }
    return ActionOutcome.Done(helperText(Res.string.helper_version, BuildConfig.VERSION_DISPLAY))
}
