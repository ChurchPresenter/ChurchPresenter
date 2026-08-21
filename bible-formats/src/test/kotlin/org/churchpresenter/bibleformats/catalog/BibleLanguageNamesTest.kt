package org.churchpresenter.bibleformats.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the pure merge seam. [BibleLanguageNames.table] itself is not tested here: it reads
 * whatever eBible catalogue happens to be cached on the machine, and the merge is the only part
 * with a decision in it.
 */
class BibleLanguageNamesTest {

    @Test
    fun `a Zefania-only code is named even with no catalogue at all`() {
        // The cold-start case: the Zefania tab opened before eBible was ever fetched.
        val names = BibleLanguageNames.resolve(emptyMap())

        assertEquals("German", names["GER"]?.english)
        assertEquals("Czech", names["CZE"]?.english)
        assertEquals("Afrikaans", names["AFR"]?.english)
    }

    @Test
    fun `a curated entry carries what the language calls itself`() {
        val names = BibleLanguageNames.resolve(emptyMap())

        assertEquals("Deutsch", names["GER"]?.native)
        assertEquals("Čeština", names["CZE"]?.native)
        assertEquals("български", names["BUL"]?.native)
    }

    @Test
    fun `a code with no settled autonym is left English-only rather than guessed`() {
        val names = BibleLanguageNames.resolve(emptyMap())

        // Not a language at all, and a script no common desktop font carries.
        assertEquals("", names["UND"]?.native)
        assertEquals("", names["GOT"]?.native)
        // Where the autonym would only repeat the English name it is left out too.
        assertEquals("", names["AFR"]?.native)
    }

    @Test
    fun `the catalogue's own names are merged in alongside the curated ones`() {
        val names = BibleLanguageNames.resolve(
            mapOf("ENG" to LanguageNaming("English"), "RUS" to LanguageNaming("Russian", "русский"))
        )

        assertEquals("русский", names["RUS"]?.native)
        assertEquals("Czech", names["CZE"]?.english, "a curated entry survives the merge")
    }

    @Test
    fun `the catalogue wins outright where both name the same code`() {
        val names = BibleLanguageNames.resolve(mapOf("GER" to LanguageNaming("Standard German", "Hochdeutsch")))

        assertEquals(
            LanguageNaming("Standard German", "Hochdeutsch"), names["GER"],
            "published data replaces the snapshot entry whole, which is what lets a fix take effect"
        )
    }

    @Test
    fun `an unknown code resolves to nothing rather than a placeholder`() {
        assertEquals(null, BibleLanguageNames.resolve(emptyMap())["ZZZ"])
    }

    @Test
    fun `every curated entry is an uppercase code with a usable pair of names`() {
        BibleLanguageNames.resolve(emptyMap()).forEach { (code, naming) ->
            // The codes are matched against uppercased folder names, so a lowercase key would be dead.
            assertEquals(code.uppercase(), code, "code '$code' is not uppercase")
            assertTrue(naming.english.isNotBlank(), "code '$code' has a blank English name")
            // An autonym that merely repeats the English name would render the word twice.
            assertFalse(
                naming.native.equals(naming.english, ignoreCase = true),
                "code '$code' repeats '${naming.english}' as its autonym; leave it blank instead"
            )
        }
    }
}
