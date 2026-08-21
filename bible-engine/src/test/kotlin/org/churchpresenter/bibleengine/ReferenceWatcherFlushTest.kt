package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.detection.ReferenceWatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceWatcherFlushTest {

    private class TestSticky : ReferenceWatcher.Sticky {
        override var watchBook: Int? = null
        override var watchChapter: Int? = null
        override var watchExpiresAt: Long = 0L
    }

    private val savedInfer = Config.inferBookAtEnd

    @AfterTest
    fun restore() {
        Config.inferBookAtEnd = savedInfer
    }

    private fun refs(vararg utterances: String, now: Long = 1_000L): List<ReferenceWatcher.Ref> {
        val s = TestSticky()
        val all = mutableListOf<ReferenceWatcher.Ref>()
        for (u in utterances) all += ReferenceWatcher.process(u, s, now)
        return all
    }

    @Test
    fun `an empty utterance yields nothing`() {
        assertTrue(refs("").isEmpty())
        assertTrue(refs("   ").isEmpty())
    }

    @Test
    fun `bare book and two numbers read as chapter then verse`() {
        val r = refs("Матфея 5 3").firstOrNull()

        assertEquals(40, r?.bookNum)
        assertEquals(5, r?.chapter)
        assertEquals(3, r?.verseStart)
    }

    @Test
    fun `bare book and three numbers with a range takes the third as the range end`() {
        val r = refs("Матфея 5 3-7").firstOrNull()

        assertEquals(5, r?.chapter)
        assertEquals(3, r?.verseStart)
        assertEquals(7, r?.verseEnd)
    }

    @Test
    fun `a single trailing number after a book is the chapter only`() {
        assertTrue(refs("Матфея 5").isEmpty(), "book + chapter alone does not emit")
    }

    @Test
    fun `once a verse is bound a leftover number does not become a chapter`() {
        val r = refs("Матфея 5 глава 3 стих 6").firstOrNull()

        assertEquals(5, r?.chapter, "the leftover 6 must not be promoted to a chapter")
        assertEquals(3, r?.verseStart)
    }

    @Test
    fun `a verse list after a keyword keeps the first as the start`() {
        val r = refs("Матфея 5 глава стихи 3 4").firstOrNull()

        assertEquals(3, r?.verseStart)
    }

    @Test
    fun `English short aliases still resolve`() {
        val r = refs("Acts chapter 2 verse 4").firstOrNull()

        assertEquals(44, r?.bookNum)
    }

    @Test
    fun `a possessive plural is not mistaken for a short alias`() {
        assertTrue(refs("James's 3 16").isEmpty() || refs("James's 3 16").all { it.bookNum == 59 })
    }

    @Test
    fun `New King James is a version name, not a citation`() {
        assertTrue(refs("reading from the New King James 3 16").isEmpty())
    }

    @Test
    fun `a chapter keyword with the number after it binds the chapter`() {
        val r = refs("Матфея глава 5 стих 3").firstOrNull()

        assertEquals(5, r?.chapter)
        assertEquals(3, r?.verseStart)
    }

    @Test
    fun `a colon separated reference binds chapter and verse`() {
        val r = refs("Матфея 5:3").firstOrNull()

        assertEquals(5, r?.chapter)
        assertEquals(3, r?.verseStart)
    }

    @Test
    fun `a numbered epistle marker with filler words between still resolves`() {
        val r = refs("Первое соборное послание Иоанна 1 глава 9 стих").firstOrNull()

        assertEquals(62, r?.bookNum)
    }

    @Test
    fun `a second-epistle ordinal selects the second book`() {
        val r = refs("Второе послание Иоанна 1 глава 1 стих").firstOrNull()

        assertEquals(63, r?.bookNum)
    }

    @Test
    fun `a from-marker range spanning two numbers keeps both ends`() {
        val r = refs("Матфея 5 глава с 3 по 7 стих").firstOrNull { it.verseEnd != null }

        assertEquals(3, r?.verseStart)
        assertEquals(7, r?.verseEnd)
    }
}
