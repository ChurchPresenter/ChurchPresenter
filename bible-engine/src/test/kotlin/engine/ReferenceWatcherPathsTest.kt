package engine

import engine.detection.ReferenceWatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceWatcherPathsTest {

    private class TestSticky : ReferenceWatcher.Sticky {
        override var watchBook: Int? = null
        override var watchChapter: Int? = null
        override var watchExpiresAt: Long = 0L
    }

    private val savedInferBookAtEnd = Config.inferBookAtEnd
    private val savedSuppressMusic = Config.suppressDuringMusic

    @AfterTest
    fun restore() {
        Config.inferBookAtEnd = savedInferBookAtEnd
        Config.suppressDuringMusic = savedSuppressMusic
    }

    private fun run(vararg utterances: String, now: Long = 1_000L): List<ReferenceWatcher.Ref> {
        val s = TestSticky()
        val all = mutableListOf<ReferenceWatcher.Ref>()
        for (u in utterances) all += ReferenceWatcher.process(u, s, now)
        return all
    }

    @Test
    fun `a music segment is skipped entirely when the gate is on`() {
        Config.suppressDuringMusic = true
        val s = TestSticky()

        assertTrue(ReferenceWatcher.process("Матфея 5 глава 3 стих", s, 1_000L, isMusic = true).isEmpty())
        assertNull(s.watchBook, "a music segment must not seed the sticky either")
    }

    @Test
    fun `a music segment is processed when the gate is off`() {
        Config.suppressDuringMusic = false
        val s = TestSticky()

        assertTrue(ReferenceWatcher.process("Матфея 5 глава 3 стих", s, 1_000L, isMusic = true).isNotEmpty())
    }

    @Test
    fun `King James is a version name, not the book of James`() {
        assertTrue(run("we read from the King James 3 16").isEmpty())
    }

    @Test
    fun `the two-track overload prefers the transcript when the tracks name different books`() {
        val s = TestSticky()

        val refs = ReferenceWatcher.process("Матфея 5 глава 3 стих", "John 5 3", s, 1_000L)

        assertTrue(refs.all { it.bookNum == 40 }, "expected Matthew from the transcript only")
    }

    @Test
    fun `the two-track overload combines the tracks when they agree`() {
        val s = TestSticky()

        val refs = ReferenceWatcher.process("Матфея 5 глава", "verse 3", s, 1_000L)

        assertEquals(listOf(40 to 5), refs.map { it.bookNum to it.chapter })
    }

    @Test
    fun `an empty translation track does not disturb the transcript`() {
        val s = TestSticky()

        val refs = ReferenceWatcher.process("Матфея 5 глава 3 стих", "", s, 1_000L)

        assertTrue(refs.isNotEmpty())
    }

    @Test
    fun `Евангелие от Иоанна stays the gospel rather than the epistle`() {
        val refs = run("Евангелие от Иоанна 3 глава 16 стих")

        assertEquals(43, refs.firstOrNull()?.bookNum, "expected the gospel of John")
    }

    @Test
    fun `a numbered book resolved ahead across a connector`() {
        val refs = run("Первое послание к Коринфянам 13 глава 4 стих")

        assertTrue(refs.isNotEmpty(), "expected a resolved reference")
    }

    @Test
    fun `a bare царств marker does not default to the first book`() {
        val refs = run("книга царств")

        assertTrue(refs.isEmpty(), "царств has markerAloneDefaultsToFirst = false")
    }

    @Test
    fun `an out-of-range ordinal falls back to the first epistle rather than off the end`() {
        val refs = run("девятое послание Иоанна 1 глава 1 стих")

        assertTrue(refs.all { it.bookNum == 62 }, "expected 1 John, got ${refs.map { it.bookNum }}")
    }

    @Test
    fun `a verse range emits an end verse`() {
        val refs = run("Матфея 5 глава с 3 по 7 стих")

        val ref = refs.firstOrNull { it.verseEnd != null }
        assertEquals(3, ref?.verseStart)
        assertEquals(7, ref?.verseEnd)
    }

    @Test
    fun `a descending range is dropped rather than inverted`() {
        val refs = run("Матфея 5 глава с 7 по 3 стих")

        assertTrue(refs.all { it.verseEnd == null }, "end must exceed start or be dropped")
    }

    @Test
    fun `a range whose end equals its start is dropped`() {
        val refs = run("Матфея 5 глава с 3 по 3 стих")

        assertTrue(refs.all { it.verseEnd == null })
    }

    @Test
    fun `a chapter keyword after the number marks it as a citation`() {
        assertTrue(run("Матфея 5 глава 3 стих").isNotEmpty())
    }

    @Test
    fun `a chapter keyword before the number marks it as a citation`() {
        assertTrue(run("Матфея глава 5 стих 3").isNotEmpty())
    }

    @Test
    fun `a trailing book is not attached when the inference is off`() {
        Config.inferBookAtEnd = false

        assertTrue(run("14 стих 3 главы Матфея").none { it.bookNum == 40 && it.chapter == 3 })
    }

    @Test
    fun `a trailing book is attached when the inference is on`() {
        Config.inferBookAtEnd = true

        val refs = run("14 стих 3 главы Матфея")

        assertTrue(refs.any { it.bookNum == 40 && it.chapter == 3 && it.verseStart == 14 })
    }

    @Test
    fun `an expired sticky is cleared before the utterance is read`() {
        val s = TestSticky().also { it.watchBook = 40; it.watchChapter = 5; it.watchExpiresAt = 500L }

        ReferenceWatcher.process("3 стих", s, now = 1_000L)

        assertNull(s.watchBook)
        assertNull(s.watchChapter)
    }

    @Test
    fun `a sticky with no expiry set is never cleared`() {
        val s = TestSticky().also { it.watchBook = 40; it.watchChapter = 5; it.watchExpiresAt = 0L }

        val refs = ReferenceWatcher.process("3 стих", s, now = 9_999_999L)

        assertEquals(40, refs.firstOrNull()?.bookNum)
    }
}
