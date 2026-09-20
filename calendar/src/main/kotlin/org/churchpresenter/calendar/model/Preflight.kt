package org.churchpresenter.calendar.model

import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.io.File

/** What is wrong with a planned row -- why it will not go on screen on the day. */
enum class PreflightProblem {
    /** A clip or a deck whose file is no longer where the row points. */
    MISSING_FILE,
    /** A picture folder that is no longer there. */
    MISSING_FOLDER,
    /** A song the library no longer has -- deleted, renumbered or moved to another book. */
    SONG_NOT_IN_LIBRARY,
    /** A book the primary Bible does not have. */
    BOOK_NOT_IN_BIBLE,
    CHAPTER_OUT_OF_RANGE,
    VERSE_OUT_OF_RANGE,
}

/**
 * Every row of [items] that would fail on the day, by row id -- the check a planner runs on a
 * Wednesday so the file that was moved on Tuesday is found before Sunday.
 *
 * What is checked is what a row points at: a file or folder still being there, a song still being
 * in the library, a reference still being inside the primary Bible. A cue is checked through its
 * payload. Nothing is checked against a library that has not been read yet -- an empty [songs] or
 * [books] means "unknown", not "everything is missing" -- and a stream is not a file, so a media
 * row with a URL is never a problem here.
 *
 * [fileExists] and [folderExists] are parameters so the decision can be tested without a disk.
 */
fun preflight(
    items: List<ScheduleItem>,
    songs: List<SongItem>,
    books: List<CalendarBibleBook>,
    fileExists: (path: String) -> Boolean = { File(it).isFile },
    folderExists: (path: String) -> Boolean = { File(it).isDirectory },
): Map<String, PreflightProblem> {
    val songIds = songs.mapTo(HashSet()) { it.songId }
    fun check(item: ScheduleItem): PreflightProblem? = when (item) {
        is ScheduleItem.MediaItem -> if (isLocalPath(item.mediaUrl) && !fileExists(item.mediaUrl)) {
            PreflightProblem.MISSING_FILE
        } else {
            null
        }
        is ScheduleItem.PresentationItem -> if (fileExists(item.filePath)) null else PreflightProblem.MISSING_FILE
        is ScheduleItem.PictureItem -> if (folderExists(item.folderPath)) null else PreflightProblem.MISSING_FOLDER
        is ScheduleItem.SongItem -> if (songs.isEmpty() || songIds.contains(item.songId) || songs.any { it.matches(item) }) {
            null
        } else {
            PreflightProblem.SONG_NOT_IN_LIBRARY
        }
        is ScheduleItem.BibleVerseItem -> if (books.isEmpty()) null else verseProblem(item, books)
        is ScheduleItem.CueItem -> item.payload?.let(::check)
        else -> null
    }
    return items.mapNotNull { item -> check(item)?.let { item.id to it } }.toMap()
}

/** A path on this machine, as opposed to a stream the player opens by URL. */
private fun isLocalPath(mediaUrl: String): Boolean = !mediaUrl.contains("://")

/**
 * An older row carries no [ScheduleItem.SongItem.songId]; it is matched the way the app itself
 * falls back -- by book and number, or by book and title where there is no number.
 */
private fun SongItem.matches(row: ScheduleItem.SongItem): Boolean =
    songbook == row.songbook && if (row.songNumber > 0) {
        number.toIntOrNull() == row.songNumber
    } else {
        title.equals(row.title, ignoreCase = true)
    }

private fun verseProblem(item: ScheduleItem.BibleVerseItem, books: List<CalendarBibleBook>): PreflightProblem? {
    val book = if (item.bookId != 0) {
        books.firstOrNull { it.bookId == item.bookId }
    } else {
        books.firstOrNull { it.name.equals(item.bookName, ignoreCase = true) }
    } ?: return PreflightProblem.BOOK_NOT_IN_BIBLE
    if (item.chapter !in 1..book.chapterCount) return PreflightProblem.CHAPTER_OUT_OF_RANGE
    val last = lastVerseOf(item)
    return if (last in 1..book.verseCount(item.chapter)) null else PreflightProblem.VERSE_OUT_OF_RANGE
}

/** The highest verse the row shows: the end of its range, or the one verse it has. */
private fun lastVerseOf(item: ScheduleItem.BibleVerseItem): Int =
    Regex("\\d+").findAll(item.verseRange).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: item.verseNumber
