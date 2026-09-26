package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.bible.Bible
import org.churchpresenter.calendar.CalendarBibleBook

/**
 * The loaded Bible flattened to what the calendar's picker and pre-flight check read: every book,
 * in this Bible's order, with the verse count of each of its chapters.
 *
 * [shortNames] are the app's own short book names by canonical id, 1 to 66; a book outside them
 * shows its full name.
 *
 * Chapter counts are looked up by the book's position and verse counts by its **id** -- the two
 * lookups [Bible] offers take different keys, and passing the position to both gave every book the
 * verse counts of the book before it.
 */
internal fun calendarBibleBooks(bible: Bible, shortNames: List<String>): List<CalendarBibleBook> {
    val names = bible.getBooks()
    return (0 until bible.getBookCount()).map { index ->
        val bookId = bible.getBookId(index)
        val name = names.getOrElse(index) { "" }
        CalendarBibleBook(
            bookId = bookId,
            name = name,
            shortName = shortNames.getOrNull(bookId - 1) ?: name,
            verseCounts = (1..bible.getChapterCount(index)).map { chapter ->
                bible.getVerseCountForChapter(bookId, chapter)
            },
        )
    }
}
