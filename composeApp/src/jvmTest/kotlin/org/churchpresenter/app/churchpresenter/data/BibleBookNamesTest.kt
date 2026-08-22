package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.Bible
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The table of Bible book names, checked as a table rather than as text.
 *
 * Sixty-six string resources are listed by hand in canonical order, and everything downstream
 * addresses a book by its position in that list — so a duplicated or missing entry does not fail,
 * it renames a book. `bible_book_40` appearing twice would leave one book showing another's name in
 * every one of the fifteen languages at once, and nothing about that is visible until someone opens
 * the Bible tab and reads it.
 *
 * Resolving a resource to its text normally goes through Compose's resource environment, which
 * needs a real display's DPI and throws `HeadlessException` in this suite — see
 * [ComposeResourceEnvironmentTestSupport]. The lookup tests below run under a fixed environment it
 * installs; everything else here needs only the table itself.
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

    // ── Resolving the names themselves ──────────────────────────────────────────

    @Test
    fun `the english names come back in canonical order`() {
        val names = ComposeResourceEnvironmentTestSupport.withFixedEnvironment {
            runBlocking { BibleBookNames.getEnglishBookNames() }
        }

        assertEquals(66, names.size)
        assertEquals("Genesis", names.first())
        assertEquals("Revelation", names.last())
    }

    @Test
    fun `the book name mapping is keyed by the lowercased english name`() {
        val mapping = ComposeResourceEnvironmentTestSupport.withFixedEnvironment {
            runBlocking { BibleBookNames.getBookNameMapping() }
        }

        assertEquals(66, mapping.size)
        assertEquals("Genesis", mapping["genesis"], "case is normalised away on the key side")
        assertTrue(
            "Genesis" !in mapping,
            "the un-lowercased form must not also work, or a caller could rely on it by accident",
        )
    }
}
