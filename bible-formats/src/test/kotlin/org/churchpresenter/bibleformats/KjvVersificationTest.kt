package org.churchpresenter.bibleformats

import kotlin.test.Test
import kotlin.test.assertEquals

class KjvVersificationTest {

    @Test
    fun `the table holds the KJV's 66 books, 1189 chapters and 31102 verses`() {
        val books = 1..KjvVersification.BOOKS
        assertEquals(1189, books.sumOf { KjvVersification.chapterCount(it) })
        val verses = books.sumOf { book ->
            (1..KjvVersification.chapterCount(book)).sumOf { KjvVersification.verseCount(book, it) }
        }
        assertEquals(31_102, verses)
    }

    @Test
    fun `a few chapters everyone knows`() {
        assertEquals(50, KjvVersification.chapterCount(1))
        assertEquals(176, KjvVersification.verseCount(19, 119))
        assertEquals(27, KjvVersification.verseCount(40, 17))
        assertEquals(14, KjvVersification.verseCount(64, 1))
    }

    @Test
    fun `outside the table there is nothing`() {
        assertEquals(0, KjvVersification.chapterCount(0))
        assertEquals(0, KjvVersification.chapterCount(67))
        assertEquals(0, KjvVersification.verseCount(1, 51))
        assertEquals(0, KjvVersification.verseCount(67, 1))
    }
}
