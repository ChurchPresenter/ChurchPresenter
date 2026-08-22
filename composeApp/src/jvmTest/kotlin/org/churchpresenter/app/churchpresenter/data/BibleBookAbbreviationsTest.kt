package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.Bible
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [BibleBookAbbreviations] turns typed-in reference prefixes ("Gen", "1 Cor", "1cor.") into a
 * canonical book id so Planning Center plan text can be matched against the loaded Bible. The
 * resource-backed loading normally needs Compose's string-resource environment, which throws
 * `HeadlessException` in this suite — see [ComposeResourceEnvironmentTestSupport], which the
 * end-to-end tests below run under. The parsing and matching helpers underneath are pure and
 * don't need it; they carry the tricky rules: a trailing period is ignored, case and inner
 * whitespace don't matter, and "1cor" must match a "1 Cor" variant.
 */
class BibleBookAbbreviationsTest {

    @Test
    fun `normalize lowercases, trims and drops a trailing period`() {
        assertEquals("gen", BibleBookAbbreviations.normalize("  Gen.  "))
        assertEquals("revelation", BibleBookAbbreviations.normalize("Revelation"))
    }

    @Test
    fun `normalize collapses runs of internal whitespace to a single space`() {
        assertEquals("1 cor", BibleBookAbbreviations.normalize("1   Cor"))
        assertEquals("song of songs", BibleBookAbbreviations.normalize("Song  of\tSongs"))
    }

    @Test
    fun `normalize only strips the trailing period, not internal ones`() {
        assertEquals("ph.il", BibleBookAbbreviations.normalize("Ph.il."))
    }

    @Test
    fun `parseVariants splits on the pipe and trims each variant`() {
        assertEquals(listOf("Gen", "Ge", "Gn"), BibleBookAbbreviations.parseVariants("Gen | Ge | Gn"))
    }

    @Test
    fun `parseVariants drops blank entries from stray or trailing pipes`() {
        assertEquals(listOf("Gen", "Ge"), BibleBookAbbreviations.parseVariants("Gen || Ge |  | "))
    }

    @Test
    fun `parseVariants of an empty string is an empty list`() {
        assertEquals(emptyList(), BibleBookAbbreviations.parseVariants(""))
        assertEquals(emptyList(), BibleBookAbbreviations.parseVariants("   |  "))
    }

    private val sample = mapOf(
        1 to listOf("Gen", "Ge", "Gn"),
        46 to listOf("1 Cor", "1Co"),
        19 to listOf("Ps", "Psalm"),
    )

    private fun resolve(text: String): Int? {
        val normalized = BibleBookAbbreviations.normalize(text)
        return BibleBookAbbreviations.findBookId(sample, normalized, normalized.replace(" ", ""))
    }

    @Test
    fun `findBookId matches a variant exactly, ignoring case and a trailing period`() {
        assertEquals(1, resolve("Gen"))
        assertEquals(1, resolve("gen."))
        assertEquals(1, resolve("GN"))
        assertEquals(19, resolve("Psalm"))
    }

    @Test
    fun `findBookId matches a numbered book whether or not a space follows the numeral`() {
        assertEquals(46, resolve("1 Cor"))
        assertEquals(46, resolve("1cor"))
        assertEquals(46, resolve("1CO"))
    }

    @Test
    fun `findBookId returns null for an unknown abbreviation`() {
        assertNull(resolve("Xyz"))
        assertNull(resolve("2 Cor"), "a numbered book that is not in the table must not match its sibling")
    }

    @Test
    fun `findBookId returns null against an empty table`() {
        assertNull(BibleBookAbbreviations.findBookId(emptyMap(), "gen", "gen"))
    }

    // ── Resolving an abbreviation end to end, against the real strings.xml table ────

    private fun resolveEndToEnd(text: String) = ComposeResourceEnvironmentTestSupport.withFixedEnvironment {
        runBlocking { BibleBookAbbreviations.resolveBookId(text) }
    }

    @Test
    fun `a plain-text abbreviation resolves to its book's canonical id`() {
        assertEquals(1, resolveEndToEnd("Gen"), "book 1 is Genesis")
        assertEquals(43, resolveEndToEnd("Jn"), "book 43 is John")
    }

    @Test
    fun `the end-to-end lookup is case and punctuation insensitive too`() {
        assertEquals(43, resolveEndToEnd("john."))
        assertEquals(43, resolveEndToEnd("JHN"))
    }

    @Test
    fun `an abbreviation belonging to no book resolves to nothing`() {
        assertNull(resolveEndToEnd("Xyz"))
    }
}
