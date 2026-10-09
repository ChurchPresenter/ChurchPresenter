package org.churchpresenter.app.churchpresenter

import org.churchpresenter.songs.ScheduleSongAction
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.cueOrSetAnnouncementText
import org.churchpresenter.settings.AppSettings

/**
 * What the main screen does when a Schedule row is put on screen: open it in its tab and hand it to
 * that tab to go live with, or put it up directly.
 *
 * [state] is the main screen's own; [selectTab] shows a tab, [presenting] takes content live, and
 * [lowerThirdFolder] is read as each lower-third row is presented.
 */
internal class ScheduleRowHandlers(
    private val state: MainDesktopState,
    private val selectTab: (Tabs) -> Unit,
    private val presenting: (Presenting) -> Unit,
    private val presenterManager: PresenterManager,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    private val lowerThirdFolder: () -> String,
) {
    /** Puts a schedule verse on screen by handing it to the Bible tab to go live with. */
    fun presentBible(item: ScheduleItem.BibleVerseItem) {
        selectTab(Tabs.BIBLE)
        state.select(item, verseGoLive = true)
    }

    /**
     * Puts a schedule song on screen by handing it to the Songs tab to go live with, as its own Go
     * Live does. Nothing is pushed from here: a placeholder put up ahead of the song showed as a blank
     * slide -- for a whole transition, or for good when the tab could not find the song.
     */
    fun presentSong(item: ScheduleItem.SongItem) {
        selectTab(Tabs.SONGS)
        state.select(item, ScheduleSongAction.GO_LIVE)
    }

    fun presentPresentation(item: ScheduleItem.PresentationItem) {
        selectTab(Tabs.PRESENTATION)
        state.select(item)
        presenting(Presenting.PRESENTATION)
    }

    fun presentPictures(item: ScheduleItem.PictureItem) {
        state.select(item)
        selectTab(Tabs.PICTURES)
        presenting(Presenting.PICTURES)
    }

    fun presentMedia(item: ScheduleItem.MediaItem) {
        selectTab(Tabs.MEDIA)
        state.select(item)
        presenting(Presenting.MEDIA)
    }

    /** [timerExpiredDefaultLabel] is what a timer row with no expiry text of its own shows when it runs out. */
    fun presentAnnouncement(item: ScheduleItem.AnnouncementItem, timerExpiredDefaultLabel: String) =
        presentAnnouncementItem(item, timerExpiredDefaultLabel, presenterManager, onSettingsChange, presenting)

    fun presentLowerThird(item: ScheduleItem.LowerThirdItem) =
        presentLowerThirdItem(item, lowerThirdFolder(), presenterManager)

    fun presentWebsite(item: ScheduleItem.WebsiteItem) {
        state.select(item)
        selectTab(Tabs.WEB)
        presenterManager.setWebsiteUrl(item.url)
        presenting(Presenting.WEBSITE)
    }

    fun presentDictionary(item: ScheduleItem.DictionaryItem) {
        cueOrSetAnnouncementText(presenterManager, "${item.word} (${item.transliteration})\n\n${item.definition}")
        presenterManager.setShowPresenterWindow(true)
        presenting(Presenting.ANNOUNCEMENTS)
    }
}
