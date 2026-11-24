package org.churchpresenter.bibletab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChineseBookInitialsTest {
    @Test
    fun `two letters offer prefix and missing-letter candidates with exact matches first`() {
        assertEquals(40, ChineseBookInitials.candidates(" MT ").first())
        assertTrue(43 in ChineseBookInitials.candidates("yh"))
        assertTrue(62 in ChineseBookInitials.candidates("yh"))
        assertTrue(1 in ChineseBookInitials.candidates("cj"))
        assertEquals(3, ChineseBookInitials.candidates("lwj").first())
        assertTrue(ChineseBookInitials.candidates("c").isEmpty())
        assertTrue(ChineseBookInitials.candidates("csj 12").isEmpty())
    }

    @Test
    fun `initials identify canonical books regardless of case`() {
        assertEquals(1, ChineseBookInitials.bookId("csj"))
        assertEquals(43, ChineseBookInitials.bookId(" YHFY "))
        assertEquals(66, ChineseBookInitials.bookId("qsl"))
        assertEquals(9, ChineseBookInitials.bookId("smejs"))
        assertEquals(10, ChineseBookInitials.bookId("smejx"))
        assertEquals(62, ChineseBookInitials.bookId("yhys"))
        assertEquals(63, ChineseBookInitials.bookId("yhes"))
        assertEquals(64, ChineseBookInitials.bookId("yhss"))
        assertNull(ChineseBookInitials.bookId("yh"))
        assertNull(ChineseBookInitials.bookId(""))
    }

    @Test
    fun `quick reference accepts spaces single verses and ranges`() {
        assertTrue(isChineseQuickReference("csj 1 12 16"))
        assertTrue(isChineseQuickReference("yhfy 3 16"))
        assertTrue(isChineseQuickReference("csj1:12-16"))
    }
}
