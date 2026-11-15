package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a library's printed song number becomes. The app reads a number only from digits at the very
 * start of a file name, so anything else a library prints has to be split before it gets there.
 */
class SongOutputTest {

    private val temp: File = Files.createTempDirectory("converter-song-output-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `a plain number is the number`() {
        assertEquals(SongNumber(digits = "104"), SongOutput.numberOf("104"))
        assertEquals(SongNumber(digits = "007"), SongOutput.numberOf(" 007 "))
    }

    @Test
    fun `a book's name in front of its number is dropped`() {
        assertEquals(SongNumber(digits = "001"), SongOutput.numberOf("WP Scores 001"))
        assertEquals(SongNumber(digits = "12"), SongOutput.numberOf("SOP 12"))
        assertEquals(SongNumber(digits = "12"), SongOutput.numberOf("A-12"))
    }

    @Test
    fun `text that is not a number is kept whole as the alias`() {
        assertEquals(SongNumber(alias = "12a"), SongOutput.numberOf("12a"))
        assertEquals(SongNumber(alias = "12/3"), SongOutput.numberOf("12/3"))
        assertEquals(SongNumber(alias = "Intro"), SongOutput.numberOf("Intro"))
        assertEquals(SongNumber(), SongOutput.numberOf("  "))
    }

    @Test
    fun `only digits go in front of a file name`() {
        assertEquals("0012 - Grace.song", SongOutput.fileName("Grace", "12"))
        assertEquals("Grace.song", SongOutput.fileName("Grace", "12/3"))
    }

    @Test
    fun `a number with a slash in it neither makes a folder nor is lost`() {
        val file = SongOutput.write(temp, ParsedSong("Grace"), mutableSetOf(), number = "12/3", position = "5")

        assertEquals(temp, file.parentFile)
        assertEquals("0005 - Grace.song", file.name)
        assertTrue(file.readText().contains("alias: 12/3"), file.readText())
    }

    @Test
    fun `the book's name and number write the number alone, with no alias`() {
        val file = SongOutput.write(temp, ParsedSong("Grace"), mutableSetOf(), number = "WP Scores 001", position = "9")

        assertEquals("0001 - Grace.song", file.name)
        assertEquals(false, file.readText().contains("alias:"), file.readText())
    }

    @Test
    fun `a song's own alias is not replaced by the printed number`() {
        val file = SongOutput.write(temp, ParsedSong("Grace", alias = "Hymn"), mutableSetOf(), number = "12a")

        assertEquals("Grace.song", file.name)
        assertTrue(file.readText().contains("alias: Hymn"), file.readText())
        assertEquals(false, file.readText().contains("12a"), file.readText())
    }
}
