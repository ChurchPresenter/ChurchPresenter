package org.churchpresenter.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.model.bibleVerseItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem

/** Which source the picker is showing. */
internal enum class PickKind { SONGS, BIBLE, SECTION, PRESETS }

/** The tab a row of this kind would have come from. */
internal fun pickKindOf(item: ScheduleItem): PickKind = when (item) {
    is ScheduleItem.SongItem -> PickKind.SONGS
    is ScheduleItem.BibleVerseItem -> PickKind.BIBLE
    is ScheduleItem.LabelItem -> PickKind.SECTION
    else -> PickKind.PRESETS
}

/** What the picker is showing: the tab, the search, the scopes, and the verse range being built. */
internal class PickerState(initialKind: PickKind) {
    var kind by mutableStateOf(initialKind)
    var query by mutableStateOf("")
    var songBook by mutableStateOf<String?>(null)
    /** Which kind of preset the Presets tab is narrowed to, or null for all of them. */
    var presetKind by mutableStateOf<PresetKind?>(null)
    var book by mutableStateOf<CalendarBibleBook?>(null)
    var chapter by mutableStateOf<Int?>(null)
    // The verse range being built: the first tap anchors it, a second tap extends it.
    private var anchor by mutableStateOf<Int?>(null)
    private var extent by mutableStateOf<Int?>(null)

    /** The verse range two taps describe, in ascending order, or null when nothing is selected. */
    val selection: IntRange? get() = verseRange(anchor, extent)

    /** The verses the footer offers to add: a book, a chapter and a range, all chosen on the Bible tab. */
    val pendingVerses: ScheduleItem.BibleVerseItem?
        get() {
            if (kind != PickKind.BIBLE) return null
            val book = book ?: return null
            val chapter = chapter ?: return null
            val range = selection ?: return null
            return bibleVerseItem(book.bookId, book.name, chapter, range.first, range.last)
        }

    fun showKind(entry: PickKind) {
        kind = entry
        query = ""
        showAllBooks()
    }

    fun showAllBooks() {
        book = null
        showBook()
    }

    fun showBook() {
        chapter = null
        clearVerses()
    }

    fun showChapter(number: Int) {
        chapter = number
        clearVerses()
    }

    fun selectWholeChapter() {
        anchor = 1
        extent = chapter?.let { book?.verseCount(it) } ?: 1
    }

    fun tapVerse(verse: Int) {
        when {
            anchor == null -> {
                anchor = verse
                extent = verse
            }
            // Tapping the only selected verse again clears it.
            anchor == verse && extent == verse -> clearVerses()
            else -> extent = verse
        }
    }

    fun clearVerses() {
        anchor = null
        extent = null
    }
}

/** The verse range two taps describe, in ascending order, or null when nothing is selected. */
private fun verseRange(anchor: Int?, extent: Int?): IntRange? {
    if (anchor == null) return null
    val other = extent ?: anchor
    return minOf(anchor, other)..maxOf(anchor, other)
}
