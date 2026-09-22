package org.churchpresenter.calendar.sync

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SanitizeTest {

    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `control and format characters are stripped and whitespace collapsed`() {
        assertEquals("song.pptx", Sanitize.cleanText("s‮ong\u0000.​pptx", 50))
        assertEquals("a b", Sanitize.cleanText("  a \t\n  b  ", 50))
    }

    @Test
    fun `text is normalized and capped`() {
        assertEquals("é", Sanitize.cleanText("é", 10))
        assertEquals("abcde", Sanitize.cleanText("abcdefgh", 5))
    }

    @Test
    fun `colors must be six hex digits`() {
        assertEquals("#A1b2C3", Sanitize.hexColor("#A1b2C3", "#000000"))
        assertEquals("#000000", Sanitize.hexColor("red", "#000000"))
        assertEquals("#000000", Sanitize.hexColor("#12345", "#000000"))
    }

    @Test
    fun `ids are bounded and plain`() {
        assertTrue(Sanitize.isId("3f2a-…".take(4)))
        assertTrue(Sanitize.isId("Hymnal::42"))
        assertFalse(Sanitize.isId(""))
        assertFalse(Sanitize.isId("a".repeat(65)))
        assertFalse(Sanitize.isId("x' OR 1=1"))
        assertFalse(Sanitize.isId("../etc"))
    }

    @Test
    fun `dates must parse and sit inside the retention window`() {
        assertEquals(LocalDate.of(2026, 9, 27), Sanitize.storedDate("2026-09-27", today))
        assertEquals(today.minusDays(90), Sanitize.storedDate(today.minusDays(90).toString(), today))
        assertNull(Sanitize.storedDate(today.minusDays(91).toString(), today))
        assertNull(Sanitize.storedDate(today.plusDays(2 * 366 + 1).toString(), today))
        assertNull(Sanitize.storedDate("tomorrow", today))
        assertNull(Sanitize.storedDate("2026-13-01", today))
    }

    @Test
    fun `times must be HH mm and lose seconds`() {
        assertEquals(LocalTime.of(9, 45), Sanitize.storedTime("09:45"))
        assertEquals(LocalTime.of(9, 45), Sanitize.storedTime("09:45:30"))
        assertNull(Sanitize.storedTime("9:45 AM"))
        assertNull(Sanitize.storedTime("25:00"))
    }


    @Test
    fun `the zero-width non-joiner stays in Persian text`() {
        val title = "می\u200Cخواهم"

        assertEquals(title, Sanitize.cleanText(title, 200))
    }

    @Test
    fun `the zero-width joiner stays in emoji sequences and Indic conjuncts`() {
        val family = "👨\u200D👩\u200D👧"
        val conjunct = "क्\u200Dष"

        assertEquals(family, Sanitize.cleanText(family, 200))
        assertEquals(conjunct, Sanitize.cleanText(conjunct, 200))
    }

    @Test
    fun `bidi overrides, zero-width space, word joiner and BOM are stripped`() {
        val disguised = "a\u202Eb\u200Bc\u2060d\uFEFFe\u2066f\u200Eg"

        assertEquals("abcdefg", Sanitize.cleanText(disguised, 200))
    }

    @Test
    fun `tag characters are stripped without leaving half a surrogate pair`() {
        val tagged = "x\uDB40\uDC41y"

        assertEquals("xy", Sanitize.cleanText(tagged, 200))
    }

    @Test
    fun `control characters go, whitespace collapses, length is capped`() {
        assertEquals("a b c", Sanitize.cleanText("a\u0000  b\t\n c", 200))
        assertEquals("abc", Sanitize.cleanText("abcdef", 3))
    }
}
