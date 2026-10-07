package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LiveHistoryLogger
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.liveoutput.cueOrSetAnnouncementText

/*
 * What the main screen does with a Schedule row: open it in its tab, or put it on screen.
 */

internal fun MainDesktopScope.presentBibleFromSchedule(item: ScheduleItem.BibleVerseItem) {
    selectTab(Tabs.BIBLE)
    state.select(item)
    live.presenting(Presenting.BIBLE)
}

internal fun MainDesktopScope.presentSongFromSchedule(item: ScheduleItem.SongItem) {
    selectTab(Tabs.SONGS)
    state.select(item)
    LiveHistoryLogger.noteLiveSong(item.songId, item.songbook, item.songNumber, item.title, "schedule")
    live.onSongItemSelected(
        LyricSection(
            title = item.title,
            songNumber = item.songNumber,
            lines = emptyList(),
            type = Constants.SECTION_TYPE_SONG
        )
    )
    // No statistics here: selecting the row makes the Songs tab push it, and with lyrics now the
    // live mode that push is the go-live it counts. Counting here as well logged every song twice.
    live.presenting(Presenting.LYRICS)
}

internal fun MainDesktopScope.presentPresentationFromSchedule(item: ScheduleItem.PresentationItem) {
    selectTab(Tabs.PRESENTATION)
    state.select(item)
    live.presenting(Presenting.PRESENTATION)
}

internal fun MainDesktopScope.presentPicturesFromSchedule(item: ScheduleItem.PictureItem) {
    state.select(item)
    selectTab(Tabs.PICTURES)
    live.presenting(Presenting.PICTURES)
}

internal fun MainDesktopScope.presentMediaFromSchedule(item: ScheduleItem.MediaItem) {
    selectTab(Tabs.MEDIA)
    state.select(item)
    live.presenting(Presenting.MEDIA)
}

/** [timerExpiredDefaultLabel] is what a timer row with no expiry text of its own shows when it runs out. */
internal fun MainDesktopScope.presentAnnouncementFromSchedule(
    item: ScheduleItem.AnnouncementItem,
    timerExpiredDefaultLabel: String,
) = presentAnnouncementItem(item, timerExpiredDefaultLabel, presenterManager, onSettingsChange, live.presenting)

internal fun MainDesktopScope.presentLowerThirdFromSchedule(item: ScheduleItem.LowerThirdItem) =
    presentLowerThirdItem(item, appSettings.streamingSettings.lowerThirdFolder, presenterManager)

internal fun MainDesktopScope.presentWebsiteFromSchedule(item: ScheduleItem.WebsiteItem) {
    state.select(item)
    selectTab(Tabs.WEB)
    presenterManager.setWebsiteUrl(item.url)
    live.presenting(Presenting.WEBSITE)
}

internal fun MainDesktopScope.presentDictionaryFromSchedule(item: ScheduleItem.DictionaryItem) {
    cueOrSetAnnouncementText(presenterManager, "${item.word} (${item.transliteration})\n\n${item.definition}")
    presenterManager.setShowPresenterWindow(true)
    live.presenting(Presenting.ANNOUNCEMENTS)
}
