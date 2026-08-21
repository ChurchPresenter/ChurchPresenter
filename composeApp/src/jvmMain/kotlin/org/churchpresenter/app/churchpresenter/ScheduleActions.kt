package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.ScheduleItem

// Kept for NavigationTopBar / menu — wraps ScheduleTabActions
data class ScheduleActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},
    /** Removes a specific item by id — used to apply an approved remote "remove from schedule". */
    val removeById: (id: String) -> Unit = {},
    val clearSchedule: () -> Unit = {},
    // Remote-API add helpers (populated from ScheduleTabActions)
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit = { _, _, _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> },
    val addDictionary: (number: String, word: String, transliteration: String, definition: String) -> Unit = { _, _, _, _ -> },
    val addAnnouncement: (item: ScheduleItem.AnnouncementItem) -> Unit = { },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> }
)
