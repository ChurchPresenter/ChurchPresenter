package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [readPreviewVerse] scans a module for one verse without loading the whole thing. */
class PreviewVerseTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanup() = temps.forEach { it.deleteRecursively() }

    private fun module(body: String): String {
        val dir = Files.createTempDirectory("cp-preview-verse").toFile().also { temps.add(it) }
        return File(dir, "module.spb").apply { writeText(body) }.absolutePath
    }

    private val johnHeader = "43 Евангелие от Иоанна 21"

    @Test
    fun `reads John 3 16 when the module has it`() {
        val path = module(
            """
            ##Title: Test
            1 Бытие 50
            $johnHeader
            -----
            B001C001V001 1 1 1 In the beginning.
            B043C003V016 43 3 16 For God so loved the world.
            B043C003V017 43 3 17 Not to condemn.
            """.trimIndent(),
        )

        val verse = readPreviewVerse(path)

        assertEquals("For God so loved the world.", verse?.text)
        assertEquals(3, verse?.chapter)
        assertEquals(16, verse?.verseNumber)
    }

    @Test
    fun `names the book as the module names it`() {
        val path = module(
            """
            $johnHeader
            -----
            B043C003V016 43 3 16 Так возлюбил Бог мир.
            """.trimIndent(),
        )

        assertEquals("Евангелие от Иоанна 3:16", readPreviewVerse(path)?.reference)
    }

    @Test
    fun `falls back to the first verse when John 3 16 is absent`() {
        val path = module(
            """
            1 Genesis 50
            -----
            B001C001V001 1 1 1 In the beginning God created.
            B001C001V002 1 1 2 And the earth was without form.
            """.trimIndent(),
        )

        val verse = readPreviewVerse(path)

        assertEquals("In the beginning God created.", verse?.text)
        assertEquals("Genesis 1:1", verse?.reference)
    }

    @Test
    fun `skips a verse whose text is empty`() {
        val path = module(
            """
            1 Genesis 50
            -----
            B001C001V001 1 1 1 
            B001C001V002 1 1 2 And the earth was without form.
            """.trimIndent(),
        )

        assertEquals("And the earth was without form.", readPreviewVerse(path)?.text)
    }

    @Test
    fun `a module that is not there reads as nothing rather than throwing`() {
        assertNull(readPreviewVerse("/no/such/module.spb"))
    }

    @Test
    fun `a file in no recognisable format reads as nothing`() {
        assertNull(readPreviewVerse(module("this is not a bible module at all")))
    }

    @Test
    fun `metadata and blank lines are ignored`() {
        val path = module(
            """
            ##Title: Something
            ##Copyright: Someone

            $johnHeader
            -----

            B043C003V016 43 3 16 The verse itself.
            """.trimIndent(),
        )

        assertEquals("The verse itself.", readPreviewVerse(path)?.text)
    }

    @Test
    fun `the display numbering is what the reference reports`() {
        // Column 5 and 6 are the module's own numbering, which for some translations differs from
        // the BxxxCxxxVxxx code the file is keyed by.
        val path = module(
            """
            43 John 21
            -----
            B043C003V016 43 4 17 Shifted numbering.
            """.trimIndent(),
        )

        val verse = readPreviewVerse(path)

        assertEquals(4, verse?.chapter)
        assertEquals(17, verse?.verseNumber)
    }
}
