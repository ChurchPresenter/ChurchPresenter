package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

data class ScheduleTabActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},

    val removeById: (id: String) -> Unit = {},
    val clearSchedule: () -> Unit = {},
    val moveSelectedToTop: () -> Unit = {},
    val moveSelectedUp: () -> Unit = {},
    val moveSelectedDown: () -> Unit = {},
    val moveSelectedToBottom: () -> Unit = {},
    val addLabel: (text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _ -> },
    val updateLabel: (id: String, text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (
        bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int,
    ) -> Unit = { _, _, _, _, _, _ -> },
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit =
        { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String, subtitleUrl: String) -> Unit =
        { _, _, _, _ -> },
    val addLowerThird: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit =
        { _, _, _, _ -> },
    val addAnnouncement: (
        text: String, textColor: String, backgroundColor: String, fontSize: Int, fontType: String,
        bold: Boolean, italic: Boolean, underline: Boolean, shadow: Boolean, shadowColor: String,
        shadowSize: Int, shadowOpacity: Int, horizontalAlignment: String, position: String,
        animationType: String, animationDuration: Int, loopCount: Int, isTimer: Boolean,
        timerHours: Int, timerMinutes: Int, timerSeconds: Int, timerTextColor: String,
        timerExpiredText: String, timerMode: String, targetHour: Int, targetMinute: Int,
        targetSecond: Int, liveClockFormat: String, backdrop: TextBackdrop, outline: TextOutline,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> },
    val updateWebsiteTitle: (url: String, title: String) -> Unit = { _, _ -> },
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> },
    val addDictionary: (number: String, word: String, transliteration: String, definition: String) -> Unit =
        { _, _, _, _ -> },
    val addCue: (item: ScheduleItem.CueItem) -> Unit = { },
    val addRow: (item: ScheduleItem, timing: RowTiming?) -> Unit = { _, _ -> },
    /** Selects a row, so the Schedule shows what the automation has just put on screen. */
    val selectItem: (id: String) -> Unit = {},
    val currentTiming: () -> Map<String, RowTiming> = { emptyMap() },
    /** The loaded service's `HH:mm` start, the clock column's anchor where no row is pinned. */
    val setServiceStart: (startTime: String?) -> Unit = {},
)

/** The three file commands, each launched behind its own dialog by the tab that owns the strings. */
internal class ScheduleFileCommands(
    val open: () -> Unit,
    val save: () -> Unit,
    val saveAs: () -> Unit,
)

/** What the tab hands its parent: every menu and keyboard command, bound to this view model. */
internal fun ScheduleViewModel.tabActions(files: ScheduleFileCommands) = ScheduleTabActions(
    newSchedule      = { newSchedule() },
    openSchedule     = files.open,
    saveSchedule     = files.save,
    saveScheduleAs   = files.saveAs,
    removeSelected   = { selectedItemId?.let { removeItem(it) } },
    removeById       = { id -> removeItem(id) },
    clearSchedule    = { clearSchedule() },
    moveSelectedToTop    = { selectedItemId?.let { moveItemToTop(it) } },
    moveSelectedUp       = { selectedItemId?.let { moveItemUp(it) } },
    moveSelectedDown     = { selectedItemId?.let { moveItemDown(it) } },
    moveSelectedToBottom = { selectedItemId?.let { moveItemToBottom(it) } },
    addLabel    = { text, textColor, bg -> addLabel(text, textColor, bg) },
    updateLabel = { id, text, textColor, bg -> updateLabel(id, text, textColor, bg) },
    addBibleVerse    = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
        addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
    },
    addSong          = { songNumber, title, songbook, songId -> addSong(songNumber, title, songbook, songId) },
    addPicture       = { folderPath, folderName, imageCount -> addPicture(folderPath, folderName, imageCount) },
    addPresentation  = { filePath, fileName, slideCount, fileType ->
        addPresentation(filePath, fileName, slideCount, fileType)
    },
    addMedia         = { mediaUrl, mediaTitle, mediaType, subtitleUrl ->
        addMedia(mediaUrl, mediaTitle, mediaType, subtitleUrl)
    },
    addLowerThird    = { presetId, presetLabel, pauseAtFrame, pauseDurationMs ->
        addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs)
    },
    addAnnouncement  = {
        text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline,
        shadow, shadowColor, shadowSize, shadowOpacity, horizontalAlignment, position,
        animationType, animationDuration, loopCount, isTimer, timerHours, timerMinutes,
        timerSeconds, timerTextColor, timerExpiredText, timerMode, targetHour,
        targetMinute, targetSecond, liveClockFormat, backdrop, outline,
        ->
        addAnnouncement(
            text, textColor, backgroundColor, fontSize, fontType, bold, italic,
            underline, shadow, shadowColor, shadowSize, shadowOpacity,
            horizontalAlignment, position, animationType, animationDuration, loopCount,
            isTimer, timerHours, timerMinutes, timerSeconds, timerTextColor,
            timerExpiredText, timerMode, targetHour, targetMinute, targetSecond,
            liveClockFormat, backdrop, outline,
        )
    },
    addWebsite       = { url, title -> addWebsite(url, title) },
    updateWebsiteTitle = { url, title -> updateWebsiteTitle(url, title) },
    addScene         = { sceneId, sceneName -> addScene(sceneId, sceneName) },
    addDictionary    = { number, word, transliteration, definition ->
        addDictionary(number, word, transliteration, definition)
    },
    addCue           = { item -> addCue(item) },
    addRow           = { item, timing -> addRow(item, timing) },
    selectItem       = { id -> selectOnly(id) },
    setServiceStart  = { setServiceStart(it) },
    currentTiming    = { timing.toMap() },
)
