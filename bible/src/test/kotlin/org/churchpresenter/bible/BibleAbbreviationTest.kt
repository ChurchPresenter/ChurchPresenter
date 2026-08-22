package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleAbbreviationTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-abbrev").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun abbreviationOf(title: String, fileName: String = "module.spb"): String {
        val content = SpbFixture.buildContent(
            title = title,
            books = listOf(SpbFixture.Book(43, "John", 1)),
            verses = listOf(SpbFixture.Verse(43, 1, 1, "In the beginning was the Word.")),
        )
        val file = SpbFixture.spbFile(dir, name = fileName, content = content)
        return Bible().also { it.loadFromSpb(file.absolutePath) }.getBibleAbbreviation()
    }

    @Test
    fun `a short title is used as it stands`() {
        assertEquals("KJV", abbreviationOf("KJV"))
    }

    @Test
    fun `a short title keeps its length up to five characters`() {
        assertEquals("NASB1", abbreviationOf("NASB1"))
    }

    @Test
    fun `punctuation riding along with a short title is dropped`() {
        assertEquals("KJV", abbreviationOf("KJV."))
    }

    @Test
    fun `a multi-word title becomes an acronym of its initials`() {
        assertEquals("KJB", abbreviationOf("King James Bible"))
    }

    @Test
    fun `an acronym stops at four words however long the title is`() {
        assertEquals("TNKJ", abbreviationOf("The New King James Version Of The Bible"))
    }

    @Test
    fun `a parenthesised aside is not part of the acronym`() {
        assertEquals("KJB", abbreviationOf("King James Bible (Authorised)"))
    }

    @Test
    fun `a title that is nothing but an aside still names itself`() {
        assertEquals("KJV", abbreviationOf("(KJV)"))
    }

    @Test
    fun `a single long word is shortened to its first letter`() {
        assertEquals("R", abbreviationOf("Reformation"))
    }

    @Test
    fun `a digit at the start of a word counts towards the acronym`() {
        assertEquals("1B", abbreviationOf("1611 Bible"))
    }

    @Test
    fun `a blank title falls back to the file name`() {
        assertEquals("kjv", abbreviationOf("", fileName = "kjv.spb"))
    }

    @Test
    fun `the file-name fallback drops the extension only`() {
        assertEquals("king.james", abbreviationOf("", fileName = "king.james.spb"))
    }

    @Test
    fun `a title that is only an empty aside falls back to the file name`() {
        // Nothing is left of the title once the aside and its punctuation are gone, so the title
        // cannot name the module at all.
        assertEquals("ru_RST77", abbreviationOf("( )", fileName = "ru_RST77.spb"))
    }

    @Test
    fun `a title of pure punctuation abbreviates to nothing`() {
        // It is a title, so the file name is not consulted — but there is no letter or digit in it
        // to build an abbreviation from either.
        assertEquals("", abbreviationOf("...", fileName = "ru_RST77.spb"))
    }
}
