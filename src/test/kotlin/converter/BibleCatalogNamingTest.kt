package converter

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How an installed Bible module is named.
 *
 * The stem is the identity the app persists as the user's selected Bible, so getting it wrong
 * renames a translation out from under someone who has already chosen it. Every case below is one
 * of the shapes the real eBible/Zefania archives contain.
 */
class BibleCatalogNamingTest {

    @Test
    fun `the abbreviation is the initial of each word`() {
        assertEquals("ACV", BibleCatalogNaming.abbreviation("A Conservative Version"))
        assertEquals("KJV", BibleCatalogNaming.abbreviation("King James Version"))
    }

    @Test
    fun `extra spacing does not produce empty initials`() {
        assertEquals("KJV", BibleCatalogNaming.abbreviation("  King   James  Version  "))
    }

    @Test
    fun `a single word abbreviates to one letter`() {
        assertEquals("S", BibleCatalogNaming.abbreviation("Synodal"))
    }

    @Test
    fun `an empty name abbreviates to nothing rather than throwing`() {
        assertEquals("", BibleCatalogNaming.abbreviation(""))
        assertEquals("", BibleCatalogNaming.abbreviation("   "))
    }

    @Test
    fun `language and identifier join with an underscore`() {
        assertEquals("ENG_ACV", BibleCatalogNaming.fileStem("ENG", "ACV"))
        assertEquals("THA_KJVTHAI", BibleCatalogNaming.fileStem("THA", "KJVTHAI"))
    }

    @Test
    fun `an identifier equal to the language does not stutter`() {
        assertEquals("SWA", BibleCatalogNaming.fileStem("SWA", "SWA"))
    }

    @Test
    fun `an identifier prefixed with the language has the prefix dropped`() {
        assertEquals("AFR_3353", BibleCatalogNaming.fileStem("AFR", "AFR3353"))
    }

    @Test
    fun `a missing identifier leaves just the language`() {
        assertEquals("ENG", BibleCatalogNaming.fileStem("ENG", null))
        assertEquals("ENG", BibleCatalogNaming.fileStem("ENG", ""))
    }

    @Test
    fun `a missing language falls back to the unknown marker`() {
        assertEquals("UND_ACV", BibleCatalogNaming.fileStem(null, "ACV"))
        assertEquals(BibleCatalogNaming.UNKNOWN_LANGUAGE, BibleCatalogNaming.fileStem(null, null))
    }

    @Test
    fun `accented letters are decomposed rather than dropped`() {
        // Without the NFD pass the Czech identifier CSP would slug to "SP" and the module would
        // install under a name nobody could recognise.
        assertEquals("CZE_CSP", BibleCatalogNaming.fileStem("CZE", "ČSP"))
        assertEquals("CSP", BibleCatalogNaming.slug("ČSP"))
    }

    @Test
    fun `punctuation and spacing are stripped from the slug`() {
        assertEquals("KJV1611", BibleCatalogNaming.slug("K.J.V- 1611!"))
    }

    @Test
    fun `a free stem is returned unchanged`() {
        assertEquals("ENG_ACV", BibleCatalogNaming.deduplicate("ENG_ACV", emptySet()))
    }

    @Test
    fun `a taken stem gains a numeric suffix`() {
        assertEquals("ENG_ACV_2", BibleCatalogNaming.deduplicate("ENG_ACV", setOf("ENG_ACV")))
    }

    @Test
    fun `deduplication keeps counting past an already-suffixed name`() {
        // Two distinct modules can reduce to the same stem; a browse list silently missing one of
        // them is worse than one with a slightly ugly name.
        assertEquals(
            "ENG_ACV_4",
            BibleCatalogNaming.deduplicate("ENG_ACV", setOf("ENG_ACV", "ENG_ACV_2", "ENG_ACV_3"))
        )
    }
}
