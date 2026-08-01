package converter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-language book-name tables used to label a converted Bible module.
 *
 * These are data, so the tests are structural: every table has to cover all 66 books with no gaps
 * or duplicates, because a missing entry shows up as a blank book name in the app's Bible tab and
 * a duplicated one makes two different books look identical in the picker. Checking every table
 * the same way is what stops a typo in the fourteenth language going unnoticed.
 */
class BookNamesTest {

    private val allTables: Map<String, Map<Int, String>> = mapOf(
        "ENGLISH" to BookNames.ENGLISH,
        "UKRAINIAN" to BookNames.UKRAINIAN,
        "RUSSIAN" to BookNames.RUSSIAN,
        "GERMAN" to BookNames.GERMAN,
        "FRENCH" to BookNames.FRENCH,
        "SPANISH" to BookNames.SPANISH,
        "PORTUGUESE" to BookNames.PORTUGUESE,
        "ITALIAN" to BookNames.ITALIAN,
        "DUTCH" to BookNames.DUTCH,
        "POLISH" to BookNames.POLISH,
        "CHINESE" to BookNames.CHINESE,
        "KOREAN" to BookNames.KOREAN,
        "ARABIC" to BookNames.ARABIC,
        "HEBREW" to BookNames.HEBREW,
    )

    @Test
    fun `every table covers all 66 books`() {
        for ((name, table) in allTables) {
            assertEquals((1..66).toSet(), table.keys, "$name must have exactly books 1..66")
        }
    }

    @Test
    fun `no table has a blank name`() {
        for ((name, table) in allTables) {
            val blanks = table.filterValues { it.isBlank() }.keys
            assertTrue(blanks.isEmpty(), "$name has blank names at $blanks")
        }
    }

    @Test
    fun `no table repeats a name`() {
        // A duplicate makes two different books indistinguishable in the book picker.
        for ((name, table) in allTables) {
            val duplicated = table.values.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertTrue(duplicated.isEmpty(), "$name repeats $duplicated")
        }
    }

    @Test
    fun `the canonical anchors are in the right slots`() {
        assertEquals("Genesis", BookNames.ENGLISH[1])
        assertEquals("Malachi", BookNames.ENGLISH[39], "the Old Testament ends at 39")
        assertEquals("Matthew", BookNames.ENGLISH[40], "the New Testament starts at 40")
        assertEquals("Revelation", BookNames.ENGLISH[66])
    }

    @Test
    fun `the language lookup resolves its codes to real tables`() {
        for ((code, table) in BookNames.LANGUAGE_LOOKUPS) {
            assertEquals(66, table.size, "the table behind $code is complete")
            assertTrue(
                allTables.values.any { it === table },
                "$code points at one of the declared tables rather than a copy",
            )
        }
    }

    @Test
    fun `alternate codes for the same language share one table`() {
        // Sources in the wild use both ISO-639-2/T and /B forms for Chinese.
        assertTrue(
            BookNames.LANGUAGE_LOOKUPS["ZHO"] === BookNames.LANGUAGE_LOOKUPS["CHI"],
            "ZHO and CHI must resolve to the same names",
        )
    }

    @Test
    fun `right-to-left languages are recognised regardless of case or spacing`() {
        assertTrue(BookNames.isRightToLeft("HEB"))
        assertTrue(BookNames.isRightToLeft("heb"), "codes arrive lower-cased from some sources")
        assertTrue(BookNames.isRightToLeft("  ara  "), "and sometimes padded")
    }

    @Test
    fun `left-to-right languages and unknown codes are not flagged`() {
        assertTrue(!BookNames.isRightToLeft("ENG"))
        assertTrue(!BookNames.isRightToLeft("ZZZ"))
        assertTrue(!BookNames.isRightToLeft(null), "a module with no language is not RTL by default")
        assertTrue(!BookNames.isRightToLeft(""))
    }

    @Test
    fun `both scripted languages carry their own names rather than falling back to English`() {
        // A table that had been left as a copy of ENGLISH would silently ship English book names
        // for that language; these two are the easiest to check by script.
        assertTrue(BookNames.HEBREW.values.none { it in BookNames.ENGLISH.values })
        assertTrue(BookNames.ARABIC.values.none { it in BookNames.ENGLISH.values })
    }
}
