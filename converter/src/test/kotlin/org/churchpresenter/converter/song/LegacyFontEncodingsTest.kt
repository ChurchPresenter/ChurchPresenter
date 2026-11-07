package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyFontEncodingsTest {

    @Test
    fun `the Yoruba fonts are recognised by their names, in any case`() {
        listOf("Yoruba_ITC Officina Serif", "Yor_Arial", "Yor_Times New Roman", "Yur_Arial", "YOR_ARIAL", " Yor_Arial ")
            .forEach { assertTrue(LegacyFontEncodings.isLegacy(it), it) }
    }

    @Test
    fun `ordinary fonts are not legacy`() {
        listOf("", "  ", "Arial", "Ebrima", "Tahoma", "Yorkshire Serif", "Arial Rounded MT Bold")
            .forEach { assertFalse(LegacyFontEncodings.isLegacy(it), it) }
    }

    @Test
    fun `a Yoruba font's stand-ins become the letters it drew`() {
        assertEquals("Mo yọ sẹsẹ, Ọlọrun Ẹmi", LegacyFontEncodings.decode("Mo y] s[s[, }lọrun {mi", "Yor_Arial"))
    }

    @Test
    fun `text in an ordinary font is left exactly as it is`() {
        assertEquals("[B] {x}", LegacyFontEncodings.decode("[B] {x}", "Arial"))
        assertEquals("[B] {x}", LegacyFontEncodings.decode("[B] {x}", ""))
    }

    @Test
    fun `stand-ins count only when they touch a letter`() {
        assertTrue(LegacyFontEncodings.usesStandIns("Iw] ni ipin mi", "Yor_Arial"))
        assertTrue(LegacyFontEncodings.usesStandIns("SUNM}", "Yor_Arial"))
        assertFalse(LegacyFontEncodings.usesStandIns("a [ b ] c", "Yor_Arial"))
        assertFalse(LegacyFontEncodings.usesStandIns("Iw] ni", "Arial"))
        assertFalse(LegacyFontEncodings.usesStandIns("", "Yor_Arial"))
    }
}
