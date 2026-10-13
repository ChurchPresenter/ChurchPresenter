package org.churchpresenter.calendar

import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * One book of the primary Bible, flattened to exactly what the picker draws.
 *
 * [verseCounts] is indexed by chapter, so `verseCounts[0]` is chapter 1's verse count and
 * `verseCounts.size` is the chapter count — one structure rather than two parallel lookups.
 */
data class CalendarBibleBook(
    /** The canonical, Bible-agnostic book id that [ScheduleItem.BibleVerseItem] stores. */
    val bookId: Int,
    val name: String,
    val verseCounts: List<Int>,
    /**
     * What the picker's tiles call it. Many translations title a book in full -- "Книга пророка
     * Исаии" -- which a tile cuts to "Книга прор…"; the host passes the app's short name instead.
     * Display only: the rows the picker makes still carry [name], which is what the Bible knows.
     */
    val shortName: String = name,
) {
    val chapterCount: Int get() = verseCounts.size

    fun verseCount(chapter: Int): Int = verseCounts.getOrElse(chapter - 1) { 0 }
}
