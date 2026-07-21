package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The table of Bible book names, checked as a table rather than as text.
 *
 * Sixty-six string resources are listed by hand in canonical order, and everything downstream
 * addresses a book by its position in that list — so a duplicated or missing entry does not fail,
 * it renames a book. `bible_book_40` appearing twice would leave one book showing another's name in
 * every one of the fifteen languages at once, and nothing about that is visible until someone opens
 * the Bible tab and reads it.
 *
 * Only the list itself is reachable here: resolving a resource to its text goes through Compose's
 * resource environment, which needs a graphics environment this suite does not have (see the note
 * on string resources in AGENT.md). So `getEnglishBookNames` and `getBookNameMapping`, which both
 * resolve, are out of scope — what is testable is that the table they read from is well formed.
 */
class BibleBookNamesTest {

    private val books = BibleBookNames.getBookResourceIds()

    @Test
    fun `there is one entry for every book of the bible`() {
        assertEquals(66, books.size, "a short list leaves the last books unreachable in the picker")
    }

    @Test
    fun `no book is listed twice`() {
        assertEquals(
            books.size,
            books.toSet().size,
            "a repeated resource shows one book under another's name, in every language at once",
        )
    }

    @Test
    fun `the same list is handed out every time`() {
        assertEquals(
            books,
            BibleBookNames.getBookResourceIds(),
            "the list is read on every book lookup; it has to be stable",
        )
    }

    @Test
    fun `the list is indexable by book number`() {
        // Callers address a book as `getBookResourceIds()[bookId - 1]`, so both ends must exist.
        assertEquals(books.first(), books[0], "book 1 is the first entry")
        assertEquals(books.last(), books[65], "book 66 is the last")
    }
}
