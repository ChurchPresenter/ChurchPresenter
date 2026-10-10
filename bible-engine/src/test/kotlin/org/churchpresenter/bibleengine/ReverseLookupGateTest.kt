package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.BibleIndex
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.detection.ReverseLookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReverseLookupGateTest {

    private val loaded = listOf(
        EngineTranslation(
            id = "ENG_KJV", title = "KJV", abbreviation = "KJV", language = "ENG", numbering = "hebrew",
            books = emptyList(), byBCV = emptyMap(), byChapter = emptyMap(), byCode = emptyMap(),
        ),
    )

    private fun hit(book: Int, chapter: Int, verse: Int, score: Double, id: String = "ENG_KJV") =
        BibleIndex.SearchResult(id, EngineVerse("B", book, chapter, verse, "text $book $chapter $verse", false), score)

    private fun result(top: BibleIndex.SearchResult, vararg rest: BibleIndex.SearchResult, threshold: Double = 0.0) =
        ReverseLookup.resultFor(top, listOf(top) + rest, threshold, loaded)

    @Test
    fun `confidence steps down with the margin over a different passage`() {
        val top = hit(43, 3, 16, 12.0)

        assertEquals(0.90, assertNotNull(result(top, hit(1, 1, 1, 1.0))).confidence)
        assertEquals(0.80, assertNotNull(result(top, hit(1, 1, 1, 2.0))).confidence)
        assertEquals(0.70, assertNotNull(result(top, hit(1, 1, 1, 3.0))).confidence)
        assertEquals(0.60, assertNotNull(result(top, hit(1, 1, 1, 10.0))).confidence)
    }

    @Test
    fun `a neighbour in the same chapter is not the competitor`() {
        val top = hit(40, 11, 29, 10.0)

        val r = assertNotNull(result(top, hit(40, 11, 28, 9.9), hit(40, 12, 1, 1.0)))

        assertEquals(10.0, r.ratio)
        assertEquals("text 40 11 29", r.text)
    }

    @Test
    fun `the same chapter number in another book is a competitor`() {
        val r = assertNotNull(result(hit(40, 11, 29, 10.0), hit(41, 11, 1, 5.0)))

        assertEquals(2.0, r.ratio)
    }

    @Test
    fun `a competitor scoring zero leaves the margin unbounded`() {
        val r = assertNotNull(result(hit(43, 3, 16, 4.0), hit(1, 1, 1, 0.0), threshold = 2.0))

        assertEquals(Double.MAX_VALUE, r.ratio)
        assertEquals(0.90, r.confidence)
    }

    @Test
    fun `a margin under the threshold is suppressed`() {
        assertNull(result(hit(43, 3, 16, 4.0), hit(1, 1, 1, 3.0), threshold = 2.0))
    }

    @Test
    fun `a hit from a translation that is not loaded is suppressed`() {
        assertNull(result(hit(43, 3, 16, 4.0, id = "RUS_RST")))
    }
}
