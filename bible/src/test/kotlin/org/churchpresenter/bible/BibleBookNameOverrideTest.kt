package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleBookNameOverrideTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-book-names-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun loaded(content: String = SpbFixture.sampleContent(), names: List<String> = emptyList()): Bible =
        Bible().also { it.loadFromSpb(SpbFixture.spbFile(dir, content = content).absolutePath, names) }

    @Test
    fun `an overridden book is listed under its new name and the others stay`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(19 to "Psalter")) }

        assertEquals(listOf("Genesis", "Psalter", "John"), bible.getBooks())
        assertEquals("Psalter", bible.getBookName(19))
        assertEquals("Genesis", bible.getBookName(1))
    }

    @Test
    fun `every way of reading a verse carries the new name`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(43 to "Yohanan")) }

        assertEquals("Yohanan", bible.getVerseDetails(43, 3, 16)?.first)
        assertEquals("Yohanan", bible.getVerseDetailsByCode(43, 3, 16)?.bookName)
        assertEquals("Yohanan", bible.searchBible(false, Regex("loved")).single().book)
    }

    @Test
    fun `the short form follows the new name`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(1 to "Bereshit")) }

        assertEquals(bible.generateAbbreviation("Bereshit"), bible.getBookAbbreviation(1))
        assertEquals(bible.generateAbbreviation("John"), bible.getBookAbbreviation(43))
    }

    @Test
    fun `a book is found by either of its names`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(43 to "Yohanan")) }

        assertEquals(43, bible.getBookIdByName("Yohanan"))
        assertEquals(43, bible.getBookIdByName("John"), "a schedule saved before the override names it this way")
        assertNull(bible.getBookIdByName("Hezekiah"))
    }

    @Test
    fun `a name is trimmed and a blank one is no override`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(1 to "  Bereshit ", 43 to "   ")) }

        assertEquals("Bereshit", bible.getBookName(1))
        assertEquals("John", bible.getBookName(43))
    }

    @Test
    fun `an empty set of overrides gives the file's names back`() {
        val bible = loaded().apply { applyBookNameOverrides(mapOf(1 to "Bereshit")) }

        bible.applyBookNameOverrides(emptyMap())

        assertEquals(listOf("Genesis", "Psalms", "John"), bible.getBooks())
    }

    @Test
    fun `the file itself is left as it was`() {
        val file = SpbFixture.spbFile(dir)
        val before = file.readText()

        Bible().apply {
            loadFromSpb(file.absolutePath)
            applyBookNameOverrides(mapOf(1 to "Bereshit"))
        }

        assertEquals(before, file.readText())
    }

    private fun placeholders() = SpbFixture.buildContent(
        title = "Numbered",
        books = listOf(SpbFixture.Book(1, "Book 1", 1), SpbFixture.Book(2, "Exodus", 1)),
        verses = listOf(SpbFixture.Verse(1, 1, 1, "First."), SpbFixture.Verse(2, 1, 1, "Second.")),
    )

    @Test
    fun `a placeholder in the header gives way to the name list and a real name does not`() {
        assertEquals(listOf("Genesis", "Exodus"), loaded(placeholders(), listOf("Genesis", "Listed")).getBooks())
    }

    @Test
    fun `a placeholder stays when there is no name list to replace it`() {
        assertEquals(listOf("Book 1", "Exodus"), loaded(placeholders()).getBooks())
    }

    @Test
    fun `only a blank name or a numbered one is a placeholder`() {
        listOf("", "   ", "Book 5", " Book 66 ").forEach { assertTrue(isPlaceholderBookName(it), "\"$it\"") }
        listOf("Genesis", "Book of Mormon", "1 Book", "Book", "Book 5a").forEach {
            assertFalse(isPlaceholderBookName(it), "\"$it\"")
        }
    }
}
