package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.server.ScheduleSongDto
/**
 * Executes a project request — adds to schedule and sets presenter state.
 * Fixes the original bug where SongItem projection never selected the song in the Songs tab.
 *
 * An item that is already a row of [scheduleItems] — a remote projecting a row it read from the
 * schedule, a calendar cue or show-control action firing one — is put up without being added
 * again; selecting that row is the caller's business, since a preview must not mark it live.
 */
internal fun executeProjectItem(
    item: ScheduleItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
    scheduleItems: List<ScheduleItem>,
) {
    // A row already there is put up as it is: the default actions add nothing.
    val schedule = if (isScheduledRow(item, scheduleItems)) ScheduleActions() else scheduleActions
    when (item) {
        is ScheduleItem.SongItem -> projectSong(item, schedule, presenterManager)

        is ScheduleItem.BibleVerseItem -> projectBibleVerse(item, schedule, presenterManager)

        is ScheduleItem.PictureItem -> {
            // Deliberately does NOT call setSelectedImagePath(item.folderPath) — that setter expects
            // a single image FILE path, not a folder, and a folder path can never render. The actual
            // image push happens via remoteSelectPictureFlow in main.kt (MainDesktop loads the folder
            // into PicturesViewModel, whose own reactive effect pushes the current image once loaded).
            schedule.addPicture(item.folderPath, item.folderName, item.imageCount)
            presenterManager.setPresentingMode(Presenting.PICTURES)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.PresentationItem -> {
            schedule.addPresentation(item.filePath, item.fileName, item.slideCount, item.fileType)
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.MediaItem -> {
            schedule.addMedia(item.mediaUrl, item.mediaTitle, item.mediaType, item.subtitleUrl)
            presenterManager.setCurrentMedia(item.mediaUrl, item.mediaType)
            presenterManager.setPresentingMode(Presenting.MEDIA)
            presenterManager.setShowPresenterWindow(true)
        }

        // A cue is fired through `fireCue`, never projected as content; a remote asking for it gets nothing.
        is ScheduleItem.CueItem -> Unit

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

        is ScheduleItem.AnnouncementItem -> projectAnnouncement(item, presenterManager)

        is ScheduleItem.WebsiteItem -> {
            schedule.addWebsite(item.url, item.title)
            presenterManager.setWebsiteUrl(item.url)
            presenterManager.setWebPageTitle(item.title)
            presenterManager.setPresentingMode(Presenting.WEBSITE)
            presenterManager.setShowPresenterWindow(true)
        }

        else -> Unit
    }
}

/** The row of [scheduleItems] that [item] is, by id — not merely one with the same content. */
internal fun scheduledRowFor(item: ScheduleItem, scheduleItems: List<ScheduleItem>): ScheduleItem? =
    if (item.id.isBlank()) null else scheduleItems.firstOrNull { it.id == item.id }

/** Whether [item] is one of [scheduleItems] itself. */
internal fun isScheduledRow(item: ScheduleItem, scheduleItems: List<ScheduleItem>): Boolean =
    scheduledRowFor(item, scheduleItems) != null

/**
 * The song row a remote's `select_song` names: the row with its id, or else the first song row of
 * the same songbook with the same number — the same title, for a song with no number.
 */
internal fun scheduledSongFor(song: ScheduleSongDto, scheduleItems: List<ScheduleItem>): ScheduleItem.SongItem? {
    val rows = scheduleItems.filterIsInstance<ScheduleItem.SongItem>()
    return rows.firstOrNull { song.id.isNotBlank() && it.id == song.id }
        ?: rows.firstOrNull { row ->
            row.songbook == song.songbook && if (song.songNumber > 0) {
                row.songNumber == song.songNumber
            } else {
                row.title.equals(song.title, ignoreCase = true)
            }
        }
}

/**
 * Adds [item] to the schedule and opens the output window. The song itself goes up from the Songs
 * tab, which the caller hands it to (`emitRemoteTabSelection`): it goes live there with its first
 * section in one push, and counts it. Nothing is put on screen from here -- a placeholder ahead of
 * the song showed as a blank slide.
 */
private fun projectSong(
    item: ScheduleItem.SongItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
) {
    scheduleActions.addSong(item.songNumber, item.title, item.songbook, item.songId)
    presenterManager.setShowPresenterWindow(true)
}

/** Adds the verse to the schedule and puts it up. */
private fun projectBibleVerse(
    item: ScheduleItem.BibleVerseItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
) {
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

/** Puts an announcement up — its text, or its timer started — without adding it to the schedule. */
private fun projectAnnouncement(item: ScheduleItem.AnnouncementItem, presenterManager: PresenterManager) {
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
