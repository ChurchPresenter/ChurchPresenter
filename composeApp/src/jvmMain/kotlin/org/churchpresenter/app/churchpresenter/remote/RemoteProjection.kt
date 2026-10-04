package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

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
        is ScheduleItem.SongItem -> projectSong(item, scheduleActions, presenterManager, statisticsManager)

        is ScheduleItem.BibleVerseItem -> projectBibleVerse(item, scheduleActions, presenterManager)

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
            scheduleActions.addMedia(item.mediaUrl, item.mediaTitle, item.mediaType, item.subtitleUrl)
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
            scheduleActions.addWebsite(item.url, item.title)
            presenterManager.setWebsiteUrl(item.url)
            presenterManager.setWebPageTitle(item.title)
            presenterManager.setPresentingMode(Presenting.WEBSITE)
            presenterManager.setShowPresenterWindow(true)
        }

        else -> Unit
    }
}

/** Adds [item] to the schedule and puts its song up, so the Songs tab navigates to it. */
private fun projectSong(
    item: ScheduleItem.SongItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager?,
) {
    // Add to schedule AND select the song so the Songs tab navigates to it
    scheduleActions.addSong(item.songNumber, item.title, item.songbook, item.songId)
    LiveHistoryLogger.noteLiveSong(item.songId, item.songbook, item.songNumber, item.title, "remote")
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
