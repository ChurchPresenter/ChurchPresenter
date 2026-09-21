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
 * [fileExists] and [folderExists] are parameters so the decision can be tested without a disk;
 * [resolveBook] is what a typed book name means, when it is not spelled the way the Bible spells
 * it -- see `CalendarHost.resolveBookId`.
 */
fun preflight(
    items: List<ScheduleItem>,
    songs: List<SongItem>,
    books: List<CalendarBibleBook>,
    fileExists: (path: String) -> Boolean = { File(it).isFile },
    folderExists: (path: String) -> Boolean = { File(it).isDirectory },
    resolveBook: (name: String) -> Int? = { null },
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
        is ScheduleItem.SongItem -> when {
            songs.isEmpty() -> null
            item.songId in songIds || songs.any { it.matches(item) } -> null
            else -> PreflightProblem.SONG_NOT_IN_LIBRARY
        }
        is ScheduleItem.BibleVerseItem -> if (books.isEmpty()) null else verseProblem(item, books, resolveBook)
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

private fun verseProblem(
    item: ScheduleItem.BibleVerseItem,
    books: List<CalendarBibleBook>,
    resolveBook: (String) -> Int?,
): PreflightProblem? {
    // A browsed row carries the canonical id. A typed one carries only a name, in whatever
    // language it was typed: the Bible's own spelling first, then whatever the app's
    // abbreviation tables make of it -- which is exactly how it is looked up at go-live.
    val book = if (item.bookId != 0) {
        books.firstOrNull { it.bookId == item.bookId }
    } else {
        books.firstOrNull { it.name.equals(item.bookName.trim(), ignoreCase = true) }
            ?: resolveBook(item.bookName)?.let { id -> books.firstOrNull { it.bookId == id } }
    } ?: return PreflightProblem.BOOK_NOT_IN_BIBLE
    if (item.chapter !in 1..book.chapterCount) return PreflightProblem.CHAPTER_OUT_OF_RANGE
    val last = item.lastVerse()
    return if (last in 1..book.verseCount(item.chapter)) null else PreflightProblem.VERSE_OUT_OF_RANGE
}

/** What can be done about a [PreflightProblem] from the row itself. */
enum class ProblemFix {
    /** Ask where the file went, and point the row there. */
    LOCATE_FILE,
    /** Ask where the folder went, and point the row there. */
    LOCATE_FOLDER,
    /** Open the picker on the row: the song or the verse has to be chosen again. */
    PICK_AGAIN,
}

val PreflightProblem.fix: ProblemFix
    get() = when (this) {
        PreflightProblem.MISSING_FILE -> ProblemFix.LOCATE_FILE
        PreflightProblem.MISSING_FOLDER -> ProblemFix.LOCATE_FOLDER
        PreflightProblem.SONG_NOT_IN_LIBRARY,
        PreflightProblem.BOOK_NOT_IN_BIBLE,
        PreflightProblem.CHAPTER_OUT_OF_RANGE,
        PreflightProblem.VERSE_OUT_OF_RANGE,
        -> ProblemFix.PICK_AGAIN
    }

/** The file or folder the row points at, or null for a row that points at none -- where "locate it" starts looking. */
fun ScheduleItem.pointedPath(): File? = when (this) {
    is ScheduleItem.MediaItem -> File(mediaUrl).takeIf { isLocalPath(mediaUrl) }
    is ScheduleItem.PresentationItem -> File(filePath)
    is ScheduleItem.PictureItem -> File(folderPath)
    is ScheduleItem.CueItem -> payload?.pointedPath()
    else -> null
}

/**
 * This row pointing at [target] instead -- what "locate it" does once the user has found the file.
 *
 * The row keeps its id, so its planned length and timing stay with it, and its title: the file
 * was moved, not replaced. What is read off the new path is what the row needs to be right about
 * it -- a deck's name and type, a folder's name and [imageCount]. Null for a row that has no
 * path to change.
 */
fun ScheduleItem.relocatedTo(target: File, imageCount: Int = 0): ScheduleItem? = when (this) {
    is ScheduleItem.MediaItem -> copy(mediaUrl = target.absolutePath)
    is ScheduleItem.PresentationItem -> copy(
        filePath = target.absolutePath,
        fileName = target.nameWithoutExtension,
        fileType = target.extension.lowercase(),
        displayText = "${target.nameWithoutExtension} ($slideCount slides)",
    )
    is ScheduleItem.PictureItem -> copy(
        folderPath = target.absolutePath,
        folderName = target.name,
        imageCount = imageCount,
        displayText = "${target.name} ($imageCount images)",
    )
    is ScheduleItem.CueItem -> payload?.relocatedTo(target, imageCount)?.let { copy(payload = it) }
    else -> null
}

/**
 * How many pictures a slideshow folder holds -- the same extensions the Pictures tab shows.
 *
 * [names] is the folder's listing, a parameter so the count can be tested without a disk; the
 * default reads the real one.
 */
fun countImages(folder: File, names: (File) -> List<String> = { it.list()?.toList().orEmpty() }): Int =
    names(folder).count { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

/** The book names the check will have to resolve: every typed verse row's, cue payloads included. */
fun typedBookNames(items: List<ScheduleItem>): Set<String> = items.flatMap { item ->
    val verse = when (item) {
        is ScheduleItem.BibleVerseItem -> item
        is ScheduleItem.CueItem -> item.payload as? ScheduleItem.BibleVerseItem
        else -> null
    }
    listOfNotNull(verse?.takeIf { it.bookId == 0 }?.bookName)
}.toSet()
