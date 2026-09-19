package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.RowTiming
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
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> },
    /** A cue row, as loaded from the Calendar Manager -- kept whole, its payload and time with it. */
    val addCue: (item: ScheduleItem.CueItem) -> Unit = { },
    /**
     * A planned row loaded whole from the Calendar Manager, id and all, with how it runs on its
     * own. The one path a plan comes in by, so its timing lands on the very row it was set on.
     */
    val addRow: (item: ScheduleItem, timing: RowTiming?) -> Unit = { _, _ -> },
    /** How each row of the schedule runs on its own -- what the automation engine reads each tick. */
    val currentTiming: () -> Map<String, RowTiming> = { emptyMap() },
    /**
     * The two structural rows, added for the Calendar Manager.
     *
     * A planned run of show carries its section headings as [ScheduleItem.LabelItem]s, so loading a
     * service without these would silently drop every heading. They are deliberately *not* reached
     * by the remote paths — see `addScheduleItem`'s `wholePlan`.
     */
    val addLabel: (text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _ -> },
    val addLowerThird: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit =
        { _, _, _, _ -> },
    /**
     * Puts a canvas scene on screen by id — what a tap on a scene row in the Schedule tab does.
     *
     * Here rather than in `executeProjectItem` because the scenes live in a ViewModel owned by
     * `MainDesktop`, which this bridge is the one sanctioned way out of; a calendar cue that shows
     * a scene comes through it.
     */
    val presentScene: (sceneId: String) -> Unit = {},
    /**
     * Starts a slideshow or a deck playing through [plays] times — 1 once, 0 until stopped — for a
     * calendar cue that shows a picture folder or a presentation. Same reason as [presentScene]:
     * the two ViewModels belong to `MainDesktop`.
     */
    val playSlideshow: (item: ScheduleItem, plays: Int) -> Unit = { _, _ -> },
    /** Selects a row, so the Schedule shows what the automation has just put on screen. */
    val selectItem: (id: String) -> Unit = {},
)
