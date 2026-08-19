package engine

import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineTranslationNavTest {

    private fun t(verses: List<EngineVerse>, books: List<EngineBook> = listOf(EngineBook(43, "John", 21))) =
        EngineTranslation(
            id = "T", title = "T", abbreviation = "T", language = "ENG", numbering = "hebrew",
            books = books,
            byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
            byChapter = verses.groupBy { it.bookNum to it.chapter },
            byCode = verses.associateBy { it.code },
        )

    private fun v(c: Int, n: Int, header: Boolean = false) =
        EngineVerse("B043C%03dV%03d".format(c, n), 43, c, n, "text $c:$n", header)

    private val full = t(listOf(v(3, 0, header = true), v(3, 16), v(3, 17), v(4, 1), v(4, 2)))

    @Test
    fun `a known verse is found and an unknown one is not`() {
        assertEquals("text 3:16", full.lookupVerse(43, 3, 16)?.text)
        assertNull(full.lookupVerse(43, 3, 99))
        assertNull(full.lookupVerse(99, 1, 1))
    }

    @Test
    fun `the first content verse skips the chapter header`() {
        assertEquals(16, full.firstContentVerse(43, 3)?.verse)
    }

    @Test
    fun `an unknown chapter has no first verse`() {
        assertNull(full.firstContentVerse(43, 99))
    }

    @Test
    fun `a chapter of only headers has no content verse`() {
        val headersOnly = t(listOf(v(9, 0, header = true)))

        assertNull(headersOnly.firstContentVerse(43, 9))
    }

    @Test
    fun `the next verse within a chapter is the following one`() {
        assertEquals(17, full.nextVerse(full.lookupVerse(43, 3, 16)!!)?.verse)
    }

    @Test
    fun `the next verse past the end of a chapter rolls into the next chapter`() {
        val next = full.nextVerse(full.lookupVerse(43, 3, 17)!!)

        assertEquals(4, next?.chapter)
        assertEquals(1, next?.verse)
    }

    @Test
    fun `the next verse past the last chapter is nothing`() {
        assertNull(full.nextVerse(full.lookupVerse(43, 4, 2)!!))
    }

    @Test
    fun `a verse from an unknown chapter has no successor`() {
        assertNull(full.nextVerse(EngineVerse("x", 43, 77, 1, "orphan", false)))
    }

    @Test
    fun `a verse not present in its own chapter list still resolves the chapter roll`() {
        val next = full.nextVerse(EngineVerse("x", 43, 3, 99, "not indexed", false))

        assertEquals(4, next?.chapter)
    }

    @Test
    fun `a book name is resolved and an unknown book is labelled`() {
        assertEquals("John", full.bookName(43))
        assertEquals("Unknown", full.bookName(1))
    }
}
