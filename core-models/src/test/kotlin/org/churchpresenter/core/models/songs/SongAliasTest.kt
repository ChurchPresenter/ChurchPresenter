package org.churchpresenter.core.models.songs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A song's alias: the free text the output shows where its number goes ("Ch 25"), written as the
 * `alias:` header line. It must read back as itself, never land in [SongItem.extraHeader] (where it
 * would be written twice), and leave a song without one exactly as it was.
 */
class SongAliasTest {

    private val parser = SongFileParser()
    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-song-alias").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun roundTrip(song: SongItem): Pair<String, SongItem?> {
        val file = File(dir, "0025 - Song.song")
        parser.writeSongFile(song, file.absolutePath)
        return file.readText() to parser.parseSongFile(file.absolutePath)
    }

    @Test
    fun `the alias header line is read into its own field`() {
        val song = parser.parseSongContent(
            """
            ---
            author: Someone
            alias: Ch 25
            ---

            [Primary]
            title: Through the Blood
            line
            """.trimIndent(),
        )

        assertEquals("Ch 25", song?.alias)
        assertEquals(emptyMap(), song?.extraHeader, "a known key must not also be carried as an unknown one")
    }

    @Test
    fun `a song written with an alias reads back with it, on one line`() {
        val (text, back) = roundTrip(SongItem("25", "Through the Blood", alias = "Ch 25", lyrics = listOf("line")))

        assertEquals("Ch 25", back?.alias)
        assertEquals(1, text.lines().count { it.startsWith("alias:") })
    }

    @Test
    fun `an alias alone is enough to write the header`() {
        val (text, _) = roundTrip(SongItem("25", "T", alias = "A12", lyrics = listOf("line")))

        assertTrue(text.startsWith("---"), "the alias has nowhere to live but the header")
    }

    @Test
    fun `a song without an alias writes no alias line and reads back blank`() {
        val (text, back) = roundTrip(SongItem("25", "T", author = "Someone", lyrics = listOf("line")))

        assertTrue("alias:" !in text)
        assertEquals("", back?.alias)
    }

    @Test
    fun `a file from before aliases reads with a blank alias`() {
        val song = parser.parseSongContent("[Primary]\ntitle: Old Song\nline")

        assertEquals("", song?.alias)
    }

    @Test
    fun `the alias is shown in place of the number`() {
        assertEquals("Ch 25", LyricSection(songNumber = 25, alias = "Ch 25").shownNumber())
    }

    @Test
    fun `an alias of only spaces falls back to the number`() {
        assertEquals("25", LyricSection(songNumber = 25, alias = "  ").shownNumber())
    }

    @Test
    fun `an alias is shown even for a song with no number`() {
        assertEquals("Intro", LyricSection(songNumber = 0, alias = " Intro ").shownNumber())
    }

    @Test
    fun `with neither an alias nor a number nothing is shown`() {
        assertNull(LyricSection(songNumber = 0).shownNumber())
    }

    @Test
    fun `a section takes its song's alias`() {
        val section = LyricSection(songNumber = 25).withAliasOf(SongItem("25", "T", alias = "Ch 25"))

        assertEquals("Ch 25", section.alias)
    }
}
