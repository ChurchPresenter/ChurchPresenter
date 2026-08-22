package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the search box narrows to, and what "all words" means.
 *
 * [BibleSearchAndNumberingTest] covers that a search finds a verse at all. This covers the three
 * overloads an operator actually reaches: search everything, search one book, search one chapter —
 * and the all-words toggle, which is the difference between typing two words and meaning "either"
 * or "both". Getting the scope wrong shows the operator a verse from the wrong book mid-service.
 */
class BibleSearchScopeTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-search-scope").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun bible(): Bible = SpbFixture.loadedBible(dir)

    /** "earth" is in Genesis 1:1, 1:2 and 2:1 and nowhere else in the fixture. */
    private val earth = Regex("earth", RegexOption.IGNORE_CASE)

    @Test
    fun `an unscoped search returns every match in the whole Bible`() {
        val hits = bible().searchBible(allWords = false, searchExp = earth)
        assertEquals(3, hits.size, "got ${hits.map { it.verseText }}")
        assertTrue(hits.all { it.book == "Genesis" })
    }

    @Test
    fun `scoping to a book drops matches from every other book`() {
        val god = Regex("God", RegexOption.IGNORE_CASE)
        val everywhere = bible().searchBible(allWords = false, searchExp = god)
        val inJohn = bible().searchBible(allWords = false, searchExp = god, book = 43)

        assertTrue(everywhere.size > inJohn.size, "the whole Bible must have more than one book")
        assertTrue(inJohn.isNotEmpty())
        assertTrue(inJohn.all { it.book == "John" }, "got ${inJohn.map { it.book }}")
    }

    @Test
    fun `scoping to a chapter drops matches from the other chapters of that book`() {
        val inGenesis = bible().searchBible(allWords = false, searchExp = earth, book = 1)
        val inChapterOne = bible().searchBible(allWords = false, searchExp = earth, book = 1, chapter = 1)

        assertEquals(3, inGenesis.size)
        assertEquals(2, inChapterOne.size, "got ${inChapterOne.map { it.verseText }}")
        assertTrue(inChapterOne.all { it.chapter == "1" })
    }

    @Test
    fun `a chapter that has no match returns nothing rather than falling back to the book`() {
        val hits = bible().searchBible(allWords = false, searchExp = earth, book = 1, chapter = 99)
        assertTrue(hits.isEmpty(), "got ${hits.map { it.verseText }}")
    }

    @Test
    fun `all words requires every word, where any-word requires only one`() {
        // "God" and "light" appear together only in Genesis 1:3.
        val either = Regex("\\b(God|light)\\b", RegexOption.IGNORE_CASE)

        val anyWord = bible().searchBible(allWords = false, searchExp = either)
        val allWords = bible().searchBible(allWords = true, searchExp = either)

        assertTrue(anyWord.size > allWords.size, "any-word must be the looser of the two")
        assertEquals(1, allWords.size, "got ${allWords.map { it.verseText }}")
        assertTrue(allWords.single().verseText.contains("light"))
    }

    @Test
    fun `a result carries the book name and its own reference, not just the verse text`() {
        val hit = bible().searchBible(allWords = false, searchExp = Regex("shepherd")).single()

        assertEquals("Psalms", hit.book)
        assertEquals("23", hit.chapter)
        assertEquals("1", hit.verse)
        assertTrue(hit.verseText.startsWith("Psalms 23:1 "), "got ${hit.verseText}")
    }

    @Test
    fun `all words and a book scope apply together, not one instead of the other`() {
        // Both words are in Genesis 1:3; the book scope must not loosen the all-words test.
        val either = Regex("\\b(God|light)\\b", RegexOption.IGNORE_CASE)

        val inGenesis = bible().searchBible(allWords = true, searchExp = either, book = 1)
        val inJohn = bible().searchBible(allWords = true, searchExp = either, book = 43)

        assertEquals(1, inGenesis.size, "got ${inGenesis.map { it.verseText }}")
        assertTrue(inJohn.isEmpty(), "John has God but not light, so all-words matches nothing")
    }

    @Test
    fun `a term that is in no verse finds nothing`() {
        assertTrue(bible().searchBible(allWords = false, searchExp = Regex("aardvark")).isEmpty())
    }

    @Test
    fun `searching a Bible that never loaded finds nothing instead of throwing`() {
        val hits = Bible().searchBible(allWords = false, searchExp = earth)
        assertTrue(hits.isEmpty())
    }
}
