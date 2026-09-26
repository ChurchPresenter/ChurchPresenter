package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.bible.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [calendarBibleBooks] over a real loaded `.spb`, with books whose chapters hold different numbers
 * of verses -- so a lookup keyed by the wrong thing gives a different answer rather than the same one.
 */
class CalendarBibleBooksTest {
    private val dir: File = Files.createTempDirectory("calendar-bible-books").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun verse(book: Int, chapter: Int, verse: Int) =
        SpbFixture.Verse(book, chapter, verse, "text $book:$chapter:$verse")

    private val bible = SpbFixture.loadedBible(
        dir,
        SpbFixture.buildContent(
            title = "Test Bible",
            books = listOf(SpbFixture.Book(1, "Genesis", 2), SpbFixture.Book(2, "Exodus", 1)),
            verses = listOf(
                verse(1, 1, 1), verse(1, 1, 2), verse(1, 1, 3),
                verse(1, 2, 1),
                verse(2, 1, 1), verse(2, 1, 2),
            ),
        ),
    )

    @Test
    fun `each book carries its own verse counts, chapter by chapter`() {
        val books = calendarBibleBooks(bible, shortNames = emptyList())

        assertEquals(listOf(1, 2), books.map { it.bookId })
        assertEquals(listOf(3, 1), books[0].verseCounts, "Genesis")
        assertEquals(listOf(2), books[1].verseCounts, "Exodus")
    }

    @Test
    fun `short names come from the app's list by book id, falling back to the full name`() {
        val books = calendarBibleBooks(bible, shortNames = listOf("Gn"))

        assertEquals(listOf("Gn", "Exodus"), books.map { it.shortName })
        assertEquals(listOf("Genesis", "Exodus"), books.map { it.name })
    }
}
