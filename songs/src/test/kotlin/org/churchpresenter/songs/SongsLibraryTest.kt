package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongsLibraryTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-songs-coverage").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun library(vararg songs: SongItem) = Songs().also { it.addSongs(songs.toList()) }

    private fun sps(name: String, vararg lines: String): File =
        File(dir, name).apply { writeText(lines.joinToString("\n")) }

    private val grace = SongItem(number = "12", title = "Amazing Grace", songbook = "Hymnal")
    private val vision = SongItem(number = "120", title = "Be Thou My Vision", songbook = "Chorus Book")

    @Test
    fun `the count is the number of songs held`() {
        assertEquals(0, Songs().getSongCount())
        assertEquals(2, library(grace, vision).getSongCount())
    }

    @Test
    fun `all songbooks returns every song`() {
        assertEquals(listOf(grace, vision), library(grace, vision).getSongsBySongbook("All songbooks"))
    }

    @Test
    fun `all song categories returns every song`() {
        assertEquals(listOf(grace, vision), library(grace, vision).getSongsByCategory("All song categories"))
    }

    @Test
    fun `the default search is contains and matches a number`() {
        assertEquals(listOf(grace, vision), library(grace, vision).findSongs("12"))
    }

    @Test
    fun `starts-with matches the start of a number`() {
        assertEquals(listOf(grace, vision), library(grace, vision).findSongs("12", Constants.STARTS_WITH))
    }

    @Test
    fun `exact match on a number finds only that number`() {
        assertEquals(listOf(grace), library(grace, vision).findSongs("12", Constants.EXACT_MATCH))
    }

    @Test
    fun `a songbook that is nowhere is an error`() {
        assertFailsWith<IllegalArgumentException> {
            Songs().loadFromSps(File(dir, "missing.sps").absolutePath)
        }
    }

    @Test
    fun `only the second header line names the songbook`() {
        val file = sps("book.sps", "##first", "##Hymnal", "##third", "1#\$#Grace#\$#9#\$#G#\$#Newton#\$#Excell#\$#")
        val songs = Songs().also { it.loadFromSps(file.absolutePath) }.getSongs()
        assertEquals("Hymnal", songs.single().songbook)
    }

    @Test
    fun `with one header line the songbook is the file name`() {
        val file = sps("Psalter.sps", "##only", "1#\$#Grace#\$#9#\$#G#\$#Newton#\$#Excell#\$#")
        val songs = Songs().also { it.loadFromSps(file.absolutePath) }.getSongs()
        assertEquals("Psalter", songs.single().songbook)
    }

    @Test
    fun `an unheaded section leads and keeps no chorus after it`() {
        val file = sps("a.sps", "1#\$#Grace#\$#1#\$#G#\$#A#\$#C#\$#hello@%world@\$Verse 1@%a@\$Chorus@%c")
        val song = Songs().also { it.loadFromSps(file.absolutePath) }.getSongs().single()
        assertEquals(listOf("hello", "world", "", "[Verse 1]", "a", "", "{Chorus}", "c"), song.lyrics)
    }

    @Test
    fun `a song that is only a chorus has no lyrics to step through`() {
        val file = sps("b.sps", "1#\$#Grace#\$#1#\$#G#\$#A#\$#C#\$#Chorus@%c")
        val song = Songs().also { it.loadFromSps(file.absolutePath) }.getSongs().single()
        assertTrue(song.lyrics.isEmpty())
    }

    @Test
    fun `a song file that cannot be written reports unsaved`() {
        val blocker = File(dir, "blocker").apply { writeText("x") }
        val target = File(blocker, "song.song").absolutePath
        val song = grace.copy(sourceFile = target)
        assertFalse(Songs().saveSongToFile(song, song, dir.absolutePath))
    }

    @Test
    fun `lyrics with no heading are stored as one section`() {
        assertEquals("a@%b", Songs().formatLyricsForSps(listOf("a", "b")))
    }

    @Test
    fun `two headings in a row store an empty-bodied section`() {
        assertEquals("Intro@\$Refrain@%x", Songs().formatLyricsForSps(listOf("[Intro]", "{Refrain}", "x")))
    }

    @Test
    fun `a heading alone is stored as its name`() {
        assertEquals("Verse 1", Songs().formatLyricsForSps(listOf("[Verse 1]")))
    }

    @Test
    fun `no lyrics store as nothing`() {
        assertEquals("", Songs().formatLyricsForSps(emptyList()))
    }

    @Test
    fun `a database with no songs table cannot be read`() {
        val file = File(dir, "empty.sps")
        JdbcDatabase.openConnection(file.absolutePath).use { c ->
            c.createStatement().use { it.executeUpdate("CREATE TABLE SongBook (title TEXT)") }
        }
        assertFailsWith<SQLException> { Songs().loadFromSps(file.absolutePath) }
    }

    @Test
    fun `a database replaces what was loaded before`() {
        val text = sps("text.sps", "1#\$#Old#\$#1#\$#G#\$#A#\$#C#\$#")
        val db = File(dir, "db.sps")
        JdbcDatabase.openConnection(db.absolutePath).use { c ->
            c.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE Songs (number TEXT, title TEXT, category TEXT, tune TEXT, words TEXT, " +
                        "music TEXT, song_text TEXT)",
                )
                st.executeUpdate("INSERT INTO Songs VALUES ('2', 'New', '1', '', '', '', '')")
            }
        }
        val songs = Songs()
        songs.loadFromSps(text.absolutePath)
        songs.loadFromSps(db.absolutePath)
        assertEquals(listOf("New"), songs.getSongs().map { it.title })
        assertEquals("db", songs.getSongs().single().songbook)
    }
}
