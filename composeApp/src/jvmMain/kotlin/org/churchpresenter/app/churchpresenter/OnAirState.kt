package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.sharedui.models.Presenting
import java.io.File

/*
 * Whether a Schedule row is still what the outputs show (#1049) -- so the row the Schedule last put
 * live keeps its LIVE mark while it is up, and gives way to a "Left off" bar once something else
 * is, or nothing.
 */

/** What the outputs show right now, as far as a Schedule row can be told apart by it. */
internal data class OnAirState(
    val content: Set<Presenting>,
    val songNumber: Int = 0,
    val songTitle: String = "",
    val verseBook: String = "",
    val verseChapter: Int = 0,
    val verseNumber: Int = 0,
    val verseRange: String = "",
    val imagePath: String? = null,
    val deckName: String? = null,
    val mediaUrl: String = "",
    val websiteUrl: String = "",
    val announcementText: String = "",
    val lowerThirdName: String = "",
    val sceneId: String? = null,
    val dictionaryWord: String? = null,
)

/** [PresenterManager]'s live state, read where a Schedule row is asked about. */
internal fun PresenterManager.onAirState(): OnAirState = OnAirState(
    content = liveContent.value,
    songNumber = lyricSection.value.songNumber,
    songTitle = lyricSection.value.title,
    verseBook = selectedVerse.value.bookName,
    verseChapter = selectedVerse.value.chapter,
    verseNumber = selectedVerse.value.verseNumber,
    verseRange = selectedVerse.value.verseRange,
    imagePath = selectedImagePath.value,
    deckName = liveSlide.value?.fileName,
    mediaUrl = currentMediaUrl.value,
    websiteUrl = websiteUrl.value,
    announcementText = announcementText.value,
    lowerThirdName = currentLowerThirdName.value,
    sceneId = activeScene.value?.id,
    dictionaryWord = displayedDictionaryEntry.value?.word,
)

/**
 * Whether [item] is still on screen in [state]: its kind of content is on air, and it is this
 * row's -- the same verse or range, the same song (through all its slides), a picture of the row's
 * folder (or the row's own picture), the same deck, clip, page, scene, preset or word. A timer
 * announcement's text changes every second, so it goes by kind alone.
 */
internal fun scheduleRowOnAir(item: ScheduleItem, state: OnAirState): Boolean = when (item) {
    is ScheduleItem.SongItem -> Presenting.LYRICS in state.content && sameSong(item, state)
    is ScheduleItem.BibleVerseItem -> Presenting.BIBLE in state.content && sameVerses(item, state)
    is ScheduleItem.PictureItem -> Presenting.PICTURES in state.content && samePicture(item, state.imagePath)
    is ScheduleItem.PresentationItem ->
        Presenting.PRESENTATION in state.content && state.deckName?.let { File(it).name } == File(item.filePath).name
    is ScheduleItem.MediaItem -> Presenting.MEDIA in state.content && item.mediaUrl == state.mediaUrl
    is ScheduleItem.WebsiteItem -> Presenting.WEBSITE in state.content && item.url == state.websiteUrl
    is ScheduleItem.SceneItem -> Presenting.CANVAS in state.content && item.sceneId == state.sceneId
    is ScheduleItem.AnnouncementItem ->
        Presenting.ANNOUNCEMENTS in state.content && (item.isTimer || item.text == state.announcementText)
    is ScheduleItem.LowerThirdItem -> Presenting.LOWER_THIRD in state.content &&
        state.lowerThirdName.isNotEmpty() && state.lowerThirdName in setOf(item.presetLabel, item.presetId)
    is ScheduleItem.DictionaryItem ->
        (Presenting.DICTIONARY in state.content || Presenting.ANNOUNCEMENTS in state.content) &&
            item.word == state.dictionaryWord
    is ScheduleItem.LabelItem, is ScheduleItem.CueItem, is ScheduleItem.MinistryItem -> false
}

private fun sameSong(item: ScheduleItem.SongItem, state: OnAirState): Boolean =
    if (item.songNumber > 0 && state.songNumber > 0) {
        item.songNumber == state.songNumber
    } else {
        item.title.equals(state.songTitle, ignoreCase = true)
    }

/** The row's verse, or its whole range: another verse of the same chapter is something else. */
private fun sameVerses(item: ScheduleItem.BibleVerseItem, state: OnAirState): Boolean =
    item.bookName == state.verseBook && item.chapter == state.verseChapter &&
        item.verseNumber == state.verseNumber && item.verseRange == state.verseRange

/** The row's own picture when it is one; any picture of its folder when it is the folder. */
private fun samePicture(item: ScheduleItem.PictureItem, imagePath: String?): Boolean {
    val shown = imagePath?.let(::File) ?: return false
    return if (item.imagePath.isNotEmpty()) {
        shown.absolutePath == File(item.imagePath).absolutePath
    } else {
        shown.parentFile?.absolutePath == File(item.folderPath).absolutePath
    }
}
