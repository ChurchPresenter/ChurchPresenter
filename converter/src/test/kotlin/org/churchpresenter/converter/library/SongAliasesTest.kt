package org.churchpresenter.converter.library

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The alias pass edits one header line in every song of a folder, so anything it gets wrong it gets
 * wrong across a whole library: these pin that it touches that line and nothing else.
 */
class SongAliasesTest {

    private val song = "---\nauthor: Someone\nccli: 123\n---\n\n[Primary]\ntitle: T\n\nline one\n"

    // ── The pattern ─────────────────────────────────────────────────────────────

    @Test
    fun `the placeholder is replaced by the number`() {
        assertEquals("Ch 25", SongAliases.aliasFor("Ch {n}", "25"))
        assertEquals("25b", SongAliases.aliasFor("{n}b", "25"))
    }

    @Test
    fun `a pattern without the placeholder is followed by the number`() {
        assertEquals("Ch 25", SongAliases.aliasFor(" Ch ", "25"))
    }

    @Test
    fun `a blank pattern clears the alias`() {
        assertEquals("", SongAliases.aliasFor("  ", "25"))
    }

    // ── The number ──────────────────────────────────────────────────────────────

    @Test
    fun `the number comes off the file name without its leading zeros`() {
        assertEquals("25", SongAliases.numberOf(File("0025 - Song.song")))
        assertEquals("7", SongAliases.numberOf(File("7-Song.song")))
        assertEquals("0", SongAliases.numberOf(File("000 - Zero.song")))
    }

    @Test
    fun `a file name with no number gives none`() {
        assertEquals("", SongAliases.numberOf(File("Song.song")))
    }

    // ── Reading and writing the line ────────────────────────────────────────────

    @Test
    fun `the alias is read from the header`() {
        assertEquals("Ch 3", SongAliases.aliasIn("---\nALIAS : Ch 3\n---\n[Primary]\ntitle: T\n"))
    }

    @Test
    fun `a song with no header, or no alias in it, has none`() {
        assertEquals("", SongAliases.aliasIn("[Primary]\ntitle: T\n"))
        assertEquals("", SongAliases.aliasIn(song))
        assertEquals("", SongAliases.aliasIn("---\nalias: unterminated\n[Primary]\n"))
    }

    @Test
    fun `setting an alias adds one line and changes nothing else`() {
        val updated = SongAliases.withAlias(song, "Ch 25")

        assertEquals("Ch 25", SongAliases.aliasIn(updated))
        assertEquals(song.lines().toSet() + "alias: Ch 25", updated.lines().toSet())
    }

    @Test
    fun `setting it again replaces the line rather than adding a second`() {
        val twice = SongAliases.withAlias(SongAliases.withAlias(song, "Ch 25"), "A 25")

        assertEquals(1, twice.lines().count { it.startsWith("alias:") })
        assertEquals("A 25", SongAliases.aliasIn(twice))
    }

    @Test
    fun `clearing removes the line and gives back the file as it was`() {
        assertEquals(song, SongAliases.withAlias(SongAliases.withAlias(song, "Ch 25"), " "))
    }

    @Test
    fun `a song with no header gets one holding only the alias`() {
        val bare = "[Primary]\ntitle: T\n\nline\n"
        val updated = SongAliases.withAlias(bare, "Ch 1")

        assertTrue(updated.startsWith("---\nalias: Ch 1\n---\n\n"))
        assertTrue(updated.endsWith(bare))
    }

    @Test
    fun `clearing a song with no header leaves it alone`() {
        val bare = "[Primary]\ntitle: T\n"
        assertEquals(bare, SongAliases.withAlias(bare, ""))
    }

    @Test
    fun `windows line endings are kept`() {
        val crlf = song.replace("\n", "\r\n")
        val updated = SongAliases.withAlias(crlf, "Ch 25")

        assertFalse(Regex("[^\r]\n").containsMatchIn(updated), "every line break stays CRLF")
        assertEquals("Ch 25", SongAliases.aliasIn(updated))
    }

    // ── A folder ────────────────────────────────────────────────────────────────

    @Test
    fun `a folder is previewed song by song, nested folders included`() {
        val dir = Files.createTempDirectory("cp-aliases").toFile()
        try {
            File(dir, "0001 - One.song").writeText(song)
            File(dir, "book/0002 - Two.song")
                .apply { parentFile.mkdirs() }
                .writeText(SongAliases.withAlias(song, "Ch 2"))
            File(dir, "Unnumbered.song").writeText(song)
            File(dir, "notes.txt").writeText("not a song")

            val entries = SongAliases.preview(dir, "Ch {n}") { it.readText() }.associateBy { it.file.name }

            assertEquals(setOf("0001 - One.song", "0002 - Two.song", "Unnumbered.song"), entries.keys)
            assertEquals("Ch 1", entries.getValue("0001 - One.song").newAlias)
            assertTrue(entries.getValue("0001 - One.song").changes)
            assertEquals("Ch 2", entries.getValue("0002 - Two.song").oldAlias)
            assertFalse(entries.getValue("0002 - Two.song").changes, "already set to what the pattern gives")
            assertNull(entries.getValue("Unnumbered.song").newAlias)
            assertFalse(entries.getValue("Unnumbered.song").changes)
        } finally {
            dir.deleteRecursively()
        }
    }
}
