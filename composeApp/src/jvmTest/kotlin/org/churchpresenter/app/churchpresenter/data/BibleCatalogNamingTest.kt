package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.converter.bible.BibleCatalogNaming
import org.churchpresenter.converter.bible.XmlToSpbConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The names installed Bibles get.
 *
 * A stem is the identity the app persists as someone's primary Bible, so it has to be a legal file
 * name on every platform whatever the source translation was called, and it has to stay stable —
 * which is why it is derived from the archive's file name rather than from anything editable inside
 * the module.
 *
 * Two shapes in the archive stutter if taken literally: an identifier that is just the language
 * again (Swahili's `SWA`), and one that repeats the language as a prefix (Afrikaans' `AFR3353`).
 * Both are real, and both are handled here rather than shipping `SWA_SWA.spb`.
 *
 * The abbreviation is shared with [XmlToSpbConverter], which writes it into each module's
 * `##Abbreviation:` header — it lives in one place so a catalog naming a translation differently
 * from the file's own header is impossible.
 */
class BibleCatalogNamingTest {

    @Test
    fun `a stem is the language joined to the module's identifier`() {
        assertEquals("ENG_ACV", BibleCatalogNaming.fileStem("ENG", "ACV"))
        assertEquals("THA_KJVTHAI", BibleCatalogNaming.fileStem("THA", "KJVTHAI"))
    }

    @Test
    fun `an identifier that is just the language does not stutter`() {
        assertEquals("SWA", BibleCatalogNaming.fileStem("SWA", "SWA"))
    }

    @Test
    fun `an identifier that repeats the language drops the repetition`() {
        assertEquals("AFR_3353", BibleCatalogNaming.fileStem("AFR", "AFR3353"))
    }

    @Test
    fun `accented identifiers are transliterated rather than gutted`() {
        // Without decomposing first, 'Č' is simply dropped and ČSP becomes "SP".
        assertEquals("CZE_CSP", BibleCatalogNaming.fileStem("CZE", "ČSP"))
    }

    @Test
    fun `a missing language is marked unknown`() {
        assertEquals("UND_ACV", BibleCatalogNaming.fileStem(null, "ACV"))
        assertEquals("UND_ACV", BibleCatalogNaming.fileStem("   ", "ACV"))
    }

    @Test
    fun `an identifier that reduces to nothing leaves the bare language`() {
        assertEquals("ENG", BibleCatalogNaming.fileStem("ENG", "«»"))
        assertEquals("ENG", BibleCatalogNaming.fileStem("ENG", null))
    }

    @Test
    fun `a stem is always safe as a file name`() {
        val stem = BibleCatalogNaming.fileStem("rus", "Синодальный")

        assertTrue(
            stem.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' },
            "a non-Latin identifier must still yield a portable file name, got '$stem'",
        )
        assertTrue(stem.startsWith("RUS"), "the language should survive lowercasing, got '$stem'")
    }

    @Test
    fun `a taken stem is suffixed rather than colliding`() {
        assertEquals("ENG_KJV", BibleCatalogNaming.deduplicate("ENG_KJV", emptySet()))
        assertEquals("ENG_KJV_2", BibleCatalogNaming.deduplicate("ENG_KJV", setOf("ENG_KJV")))
        assertEquals("ENG_KJV_3", BibleCatalogNaming.deduplicate("ENG_KJV", setOf("ENG_KJV", "ENG_KJV_2")))
    }

    @Test
    fun `the abbreviation is the initial of each word`() {
        assertEquals("KJV", BibleCatalogNaming.abbreviation("King James Version"))
        assertEquals("ASV", BibleCatalogNaming.abbreviation("American  Standard  Version"))
        assertEquals("", BibleCatalogNaming.abbreviation("   "))
    }
}
