package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.util.UUID

/**
 * `John 3:16`, `1 Cor 13`, `Пс 23:1-6` — a typed reference, turned into a schedule row.
 *
 * The book name is taken as typed and **not** resolved to a book id here. `:calendar` has no Bible
 * loaded and no business loading one to let somebody plan a reading; `ScheduleItem.BibleVerseItem`
 * already documents `bookId = 0` as "unknown, match the name against the primary Bible's book list
 * instead", which is exactly this case and is what the app does with items arriving from remote
 * clients too.
 *
 * The consequence is worth knowing rather than hiding: a reference planned while the primary Bible
 * is English will not resolve if the primary Bible is Russian by the Sunday. The row still shows
 * the reference as typed; it is the verse text that will not be found.
 */
private val REFERENCE = Regex(
    // An optional leading ordinal (1 John), the book name in any script, the chapter, and an
    // optional verse or verse range.
    """^\s*(\d?\s*[\p{L}][\p{L}.\s]*?)\s+(\d{1,3})(?::(\d{1,3})(?:\s*-\s*(\d{1,3}))?)?\s*$"""
)

/** Which capture group of [REFERENCE] holds what. */
private const val BOOK = 1
private const val CHAPTER = 2
private const val FIRST_VERSE = 3
private const val LAST_VERSE = 4

/** The parsed pieces of a reference, before it becomes a row. */
data class ParsedReference(
    val bookName: String,
    val chapter: Int,
    val firstVerse: Int,
    val lastVerse: Int,
) {
    /** `John 3:16`, `John 3:16-17`, or `John 3` for a whole chapter. */
    val display: String
        get() = when {
            firstVerse == 0 -> "$bookName $chapter"
            lastVerse > firstVerse -> "$bookName $chapter:$firstVerse-$lastVerse"
            else -> "$bookName $chapter:$firstVerse"
        }

    /** The `verseRange` field's form — empty for a single verse, as [ScheduleItem.BibleVerseItem] expects. */
    val verseRange: String get() = if (lastVerse > firstVerse) "$firstVerse-$lastVerse" else ""
}

/** Parses [text] as a reference, or null when it is not one. */
fun parseReference(text: String): ParsedReference? {
    val groups = REFERENCE.find(text)?.groupValues ?: return null
    val book = groups[BOOK].replace(Regex("""\s+"""), " ").trim()
    val chapter = groups[CHAPTER].toIntOrNull()
    if (book.isEmpty() || chapter == null) return null
    val first = groups[FIRST_VERSE].toIntOrNull() ?: 0
    val last = groups[LAST_VERSE].toIntOrNull() ?: first
    // "John 3:16-12" is a typo, not a range — keep the first verse rather than inventing a backwards one.
    return ParsedReference(book, chapter, first, if (last >= first) last else first)
}

/** A parsed reference as a run-of-show row. */
fun ParsedReference.toScheduleItem(): ScheduleItem.BibleVerseItem = ScheduleItem.BibleVerseItem(
    id = UUID.randomUUID().toString(),
    bookName = bookName,
    chapter = chapter,
    verseNumber = firstVerse,
    verseText = "",
    verseRange = verseRange,
    bookId = 0,
    displayText = display,
)

/**
 * A verse or a run of verses picked out of the browse grids, where the book id is known.
 *
 * Unlike a typed reference this carries the canonical [bookId], so the Schedule tab resolves it
 * whatever language the primary Bible is in later — a typed reference can only store the name and
 * has to be matched by text.
 *
 * [last] equal to [first] is a single verse, and stores an empty `verseRange`, which is the form
 * `ScheduleItem.BibleVerseItem` documents for one verse.
 */
fun bibleVerseItem(
    bookId: Int,
    bookName: String,
    chapter: Int,
    first: Int,
    last: Int = first,
): ScheduleItem.BibleVerseItem {
    val from = minOf(first, last)
    val to = maxOf(first, last)
    val range = if (to > from) "$from-$to" else ""
    return ScheduleItem.BibleVerseItem(
        id = UUID.randomUUID().toString(),
        bookName = bookName,
        chapter = chapter,
        verseNumber = from,
        verseText = "",
        verseRange = range,
        bookId = bookId,
        displayText = if (range.isEmpty()) "$bookName $chapter:$from" else "$bookName $chapter:$range",
    )
}
