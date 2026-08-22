package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the book and verse lookups answer when asked for something that is not there.
 *
 * Every one of these is reached with a real Bible open — a follower instance mirroring a primary
 * that has a book this translation does not, a stale reference in a saved schedule, a display index
 * from a longer book list. They must answer, not throw, and the fallbacks they answer with are the
 * behaviour callers were written against: [getBookId] assumes canonical order when it has nothing
 * better, and [getVerseDetails] names an absent book rather than returning nothing at all.
 */
class BibleLookupFallbackTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-lookup").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun bible(): Bible = SpbFixture.loadedBible(dir)

    @Test
    fun `a display index past the end of the book list falls back to canonical order`() {
        // The fixture has three books, so index 40 is not one of them.
        assertEquals(41, bible().getBookId(displayIndex = 40), "canonical order is index + 1")
    }

    @Test
    fun `a display index inside the list gives that book's own id`() {
        assertEquals(19, bible().getBookId(displayIndex = 1), "Psalms is the second book here")
    }

    @Test
    fun `a book id this module does not have has no name and no abbreviation`() {
        val b = bible()
        assertNull(b.getBookName(66), "Revelation is not in this fixture")
        assertNull(b.getBookAbbreviation(66))
    }

    @Test
    fun `a book name this module does not use has no id`() {
        assertNull(bible().getBookIdByName("Revelation"))
    }

    @Test
    fun `a book id with no entry falls back to canonical position rather than -1`() {
        assertEquals(65, bible().getDisplayIndexForBookId(66), "bookId - 1")
    }

    @Test
    fun `a book id that is present gives its real position in the list`() {
        assertEquals(1, bible().getDisplayIndexForBookId(19), "Psalms sits second")
    }

    @Test
    fun `a verse that is not in the module returns nothing`() {
        val b = bible()
        assertNull(b.getVerseDetails(1, 1, 99), "Genesis 1 has no verse 99")
        assertNull(b.getVerseDetails(66, 1, 1), "and no book 66 at all")
    }

    @Test
    fun `a chapter that is not in the module has no verses`() {
        assertEquals(emptyList(), bible().getChapterVerses(1, 99))
    }

    @Test
    fun `a verse code that is not in the format parses to nothing`() {
        val b = bible()
        assertNull(b.parseVerseCode("Genesis 1:1"))
        assertNull(b.parseVerseCode("B1C1V1"), "the format is zero-padded to three digits")
    }

    @Test
    fun `a well-formed code parses to its three numbers`() {
        assertEquals(Triple(19, 23, 1), bible().parseVerseCode("B019C023V001"))
    }

    @Test
    fun `a code that names no verse in this module returns nothing`() {
        assertNull(bible().getVerseDetailsByCode(codeBook = 66, codeChapter = 1, codeVerse = 1))
    }

    @Test
    fun `a verse that is present has a code reference, and one that is not has none`() {
        val b = bible()
        assertEquals(Triple(19, 23, 1), b.getCodeReference(19, 23, 1))
        assertNull(b.getCodeReference(19, 23, 99), "no verse, no reference")
    }

    @Test
    fun `every book the module lists gets an abbreviation, derived from its own name`() {
        val b = bible()
        assertEquals("Gen", b.getBookAbbreviation(1))
        assertEquals("Psa", b.getBookAbbreviation(19))
    }

    @Test
    fun `a code that does name a verse resolves to this module's own numbering`() {
        val hit = bible().getVerseDetailsByCode(codeBook = 19, codeChapter = 23, codeVerse = 1)
        assertEquals("Psalms", hit?.bookName)
    }
}
