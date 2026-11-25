package org.churchpresenter.bibleeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberingTest {

    private val kjv = NumberingTables.all.getValue(Numbering.KJV)

    @Test
    fun `the KJV table holds the whole KJV`() {
        val books = 1..NumberingTables.BOOKS
        assertEquals(1189, books.sumOf { kjv.chapterCount(it) })
        assertEquals(31_102, books.sumOf { book -> (1..kjv.chapterCount(book)).sumOf { kjv.verseCount(book, it) } })
        assertEquals(176, kjv.verseCount(19, 119))
        assertEquals(27, kjv.verseCount(40, 17))
    }

    @Test
    fun `every numbering has a table covering all 66 books`() {
        for ((numbering, table) in NumberingTables.all) {
            for (book in 1..NumberingTables.BOOKS) {
                assertTrue(table.chapterCount(book) > 0, "$numbering has no book $book")
            }
        }
        assertEquals(Numbering.entries.toSet(), NumberingTables.all.keys)
    }

    @Test
    fun `a table that leaves a book out takes it from the KJV`() {
        val hebrew = NumberingTables.all.getValue(Numbering.HEBREW)
        assertEquals(kjv.chapterCount(40), hebrew.chapterCount(40))
        assertEquals(9, hebrew.verseCount(19, 3))
    }

    @Test
    fun `a chapter or book a table does not have has no verses`() {
        assertEquals(0, kjv.chapterCount(0))
        assertEquals(0, kjv.chapterCount(67))
        assertEquals(0, kjv.verseCount(1, 51))
        assertEquals(0, kjv.verseCount(67, 1))
    }

    @Test
    fun `comments and blank lines are skipped, and a book read overrides the fallback`() {
        val fallback = VerseTable.parse("1 3 4\n2 5")
        val table = VerseTable.parse("# a comment\n\n1 7 8 9\n", fallback)
        assertEquals(3, table.chapterCount(1))
        assertEquals(9, table.verseCount(1, 3))
        assertEquals(5, table.verseCount(2, 1))
    }
}
