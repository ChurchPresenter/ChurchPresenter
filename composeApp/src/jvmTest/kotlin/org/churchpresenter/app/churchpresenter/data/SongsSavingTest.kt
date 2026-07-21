package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Writing an edited song back to the songbook it came from.
 *
 * The song editor hands [Songs.saveSongToFile] the song as it was and as it now is, and the file it
 * belongs to has to be found again from those two alone. A song from a `.song` file knows its own
 * path; a song from a legacy `.sps` songbook does not, so every `.sps` file in the library folder is
 * opened in turn and searched for a line whose number and title still match the ORIGINAL — which is
 * why the original is passed at all, and why renaming and renumbering in one edit still lands.
 *
 * Getting this wrong is quiet: the editor closes, the library shows the edit, and the file on disk
 * is unchanged — so the song reverts at the next restart, usually noticed during a service. The
 * other direction is worse, since the rewrite replaces the whole line: a lyric that does not
 * survive the round trip back into the `@$`/`@%` markers is lost with no way back.
 */
class SongsSavingTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-songs-saving-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun row(
        number: String,
        title: String,
        lyrics: String = "",
        category: String = "1",
        key: String = "G",
        author: String = "",
        composer: String = "",
    ) = listOf(number, title, category, key, author, composer, lyrics).joinToString("#\$#")

    private fun writeSongbook(vararg rows: String, name: String = "library.sps", songbook: String = "Hymnal"): File =
        File(dir, name).also {
            it.writeText("##SongPresenter\n##$songbook\n" + rows.joinToString("\n"), Charsets.UTF_8)
        }

    private fun load(file: File): Songs = Songs().also { it.loadFromSps(file.absolutePath) }

    /** The stored fields of the one line in [file] whose title matches, split on the `.sps` delimiter. */
    private fun storedRow(file: File, title: String): List<String> =
        file.readLines().first { !it.startsWith("##") && it.split("#\$#").getOrNull(1) == title }.split("#\$#")

    // ── Finding the file again ──────────────────────────────────────────────────

    @Test
    fun `an edited song is written back to the songbook it came from`() {
        val file = writeSongbook(row("1", "Amazing Grace", author = "Newton"))
        val songs = load(file)
        val original = songs.getSongs().single()

        val saved = songs.saveSongToFile(original, original.copy(author = "John Newton"), dir.absolutePath)

        assertTrue(saved)
        assertEquals("John Newton", storedRow(file, "Amazing Grace")[4], "the edit has to outlive the restart")
    }

    @Test
    fun `the right songbook is found among several`() {
        val first = writeSongbook(row("1", "In the first book"), name = "first.sps")
        val second = writeSongbook(row("1", "In the second book"), name = "second.sps", songbook = "Other")
        val songs = load(second)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(tune = "D"), dir.absolutePath))

        assertEquals("D", storedRow(second, "In the second book")[3])
        assertEquals(
            "In the first book",
            storedRow(first, "In the first book")[1],
            "a song sharing a number with one in another book must not overwrite it",
        )
    }

    @Test
    fun `other songs in the same file are left untouched`() {
        val file = writeSongbook(
            row("1", "First", lyrics = "Verse 1@%first line"),
            row("2", "Second", lyrics = "Verse 1@%second line"),
            row("3", "Third", lyrics = "Verse 1@%third line"),
        )
        val songs = load(file)
        val original = songs.getSongs().first { it.number == "2" }

        assertTrue(songs.saveSongToFile(original, original.copy(title = "Second (revised)"), dir.absolutePath))

        val lines = file.readLines()
        assertEquals(5, lines.size, "the two header lines and three songs — no line added or dropped")
        assertTrue(lines[2].startsWith("1#\$#First"), "the song before it is byte for byte as it was")
        assertTrue(lines[4].startsWith("3#\$#Third"), "and so is the one after")
    }

    @Test
    fun `a song can be renumbered and renamed in one edit`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(number = "101", title = "Amazing Grace (new)"), dir.absolutePath))

        val stored = storedRow(file, "Amazing Grace (new)")
        assertEquals("101", stored[0])
        assertEquals("Amazing Grace (new)", stored[1], "the line is matched on the original, then rewritten wholesale")
    }

    @Test
    fun `the songbook id on the line is kept`() {
        val file = writeSongbook(row("1", "Amazing Grace", category = "7"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(title = "Renamed"), dir.absolutePath))

        assertEquals("7", storedRow(file, "Renamed")[2], "the category id is not the app's to invent or drop")
    }

    @Test
    fun `a song that is in no file is reported as unsaved`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val ghost = SongItem(number = "999", title = "Never in the file", songbook = "Hymnal")

        assertFalse(
            songs.saveSongToFile(ghost, ghost.copy(title = "Still not"), dir.absolutePath),
            "the editor has to be told, or it closes as though the edit had been kept",
        )
        assertTrue(file.readLines().none { it.contains("Still not") })
    }

    @Test
    fun `saving with no library folder set fails rather than guessing one`() {
        val songs = load(writeSongbook(row("1", "Amazing Grace")))
        val original = songs.getSongs().single()

        assertFalse(songs.saveSongToFile(original, original.copy(title = "Renamed"), ""))
    }

    @Test
    fun `saving to a folder that is not there fails`() {
        val songs = load(writeSongbook(row("1", "Amazing Grace")))
        val original = songs.getSongs().single()

        assertFalse(songs.saveSongToFile(original, original, File(dir, "no-such-folder").absolutePath))
    }

    @Test
    fun `saving to a path that is a file rather than a folder fails`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertFalse(songs.saveSongToFile(original, original, file.absolutePath))
    }

    @Test
    fun `a folder holding no songbooks reports nothing saved`() {
        val songs = load(writeSongbook(row("1", "Amazing Grace")))
        val original = songs.getSongs().single()
        val empty = File(dir, "empty").also { it.mkdirs() }
        File(empty, "notes.txt").writeText("not a songbook")

        assertFalse(songs.saveSongToFile(original, original, empty.absolutePath))
    }

    // ── Songs that know their own file ──────────────────────────────────────────

    @Test
    fun `a song from a song file is written straight back to it`() {
        val songFile = File(dir, "amazing.song")
        SongFileParser().writeSongFile(
            SongItem(number = "1", title = "Amazing Grace", author = "Newton", lyrics = listOf("[Verse 1]", "line")),
            songFile.absolutePath,
        )
        val songs = Songs()
        val original = SongItem(
            number = "1", title = "Amazing Grace", author = "Newton",
            lyrics = listOf("[Verse 1]", "line"), sourceFile = songFile.absolutePath,
        )

        assertTrue(songs.saveSongToFile(original, original.copy(author = "John Newton"), dir.absolutePath))

        assertTrue(songFile.readText().contains("John Newton"), "its own file is the one place it can go")
    }

    @Test
    fun `a song whose file is only known from the original is still written back`() {
        // The editor builds the updated song from the form, which does not carry the source path.
        val songFile = File(dir, "amazing.song")
        SongFileParser().writeSongFile(SongItem(number = "1", title = "Amazing Grace"), songFile.absolutePath)
        val songs = Songs()
        val original = SongItem(number = "1", title = "Amazing Grace", sourceFile = songFile.absolutePath)

        assertTrue(songs.saveSongToFile(original, SongItem(number = "1", title = "Renamed"), dir.absolutePath))

        val reloaded = SongFileParser().parseSongFile(songFile.absolutePath)
        assertEquals("Renamed", reloaded?.title)
        assertEquals(
            songFile.absolutePath,
            reloaded?.sourceFile,
            "the path has to be carried onto the updated song, or the next edit loses it",
        )
    }

    // ── Lyrics surviving the round trip ─────────────────────────────────────────

    @Test
    fun `edited lyrics come back the same on the next load`() {
        val file = writeSongbook(row("1", "Amazing Grace", lyrics = "Verse 1@%old line"))
        val songs = load(file)
        val original = songs.getSongs().single()
        val edited = listOf("[Verse 1]", "Amazing grace", "how sweet the sound")

        assertTrue(songs.saveSongToFile(original, original.copy(lyrics = edited), dir.absolutePath))

        assertEquals(edited, load(file).getSongs().single().lyrics, "what the operator typed is what reloads")
    }

    @Test
    fun `a chorus survives the round trip`() {
        val file = writeSongbook(row("1", "With chorus", lyrics = "Verse 1@%old"))
        val songs = load(file)
        val original = songs.getSongs().single()
        // The layout the editor shows: the chorus already repeated after each verse.
        val edited = listOf(
            "[Verse 1]", "first verse",
            "", "{Chorus}", "chorus line",
            "", "[Verse 2]", "second verse",
            "", "{Chorus}", "chorus line",
        )

        assertTrue(songs.saveSongToFile(original, original.copy(lyrics = edited), dir.absolutePath))

        assertEquals(
            edited,
            load(file).getSongs().single().lyrics,
            "the chorus is stored once and re-expanded on load — it must not come back doubled or missing",
        )
    }

    @Test
    fun `the stored lyrics use the markers the format defines`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(
            songs.saveSongToFile(
                original,
                original.copy(lyrics = listOf("[Verse 1]", "first line", "second line", "", "[Verse 2]", "third line")),
                dir.absolutePath,
            ),
        )

        assertEquals(
            "Verse 1@%first line@%second line@\$Verse 2@%third line",
            storedRow(file, "Amazing Grace")[6],
            "another SongPresenter build reads this file — @\$ separates sections and @% separates lines",
        )
    }

    @Test
    fun `the brackets around a heading are not stored`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(lyrics = listOf("{Припев}", "строка")), dir.absolutePath))

        val stored = storedRow(file, "Amazing Grace")[6]
        assertEquals("Припев@%строка", stored, "the braces are this app's own display marker, not part of the format")
    }

    @Test
    fun `a song can be emptied of lyrics`() {
        val file = writeSongbook(row("1", "Amazing Grace", lyrics = "Verse 1@%line"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(lyrics = emptyList()), dir.absolutePath))

        assertEquals("", storedRow(file, "Amazing Grace")[6])
        assertTrue(load(file).getSongs().single().lyrics.isEmpty())
    }

    @Test
    fun `blank lines the operator left in are not stored as empty slides`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(
            songs.saveSongToFile(
                original,
                original.copy(lyrics = listOf("[Verse 1]", "   ", "first line", "", "", "second line")),
                dir.absolutePath,
            ),
        )

        assertEquals("Verse 1@%first line@%second line", storedRow(file, "Amazing Grace")[6])
    }

    @Test
    fun `lyrics with no heading at all are still stored`() {
        val file = writeSongbook(row("1", "Spoken"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(lyrics = listOf("one", "two")), dir.absolutePath))

        assertEquals("one@%two", storedRow(file, "Spoken")[6], "not every song is laid out in verses")
    }

    @Test
    fun `the header lines of the songbook are left alone`() {
        val file = writeSongbook(row("1", "Amazing Grace"))
        val songs = load(file)
        val original = songs.getSongs().single()

        assertTrue(songs.saveSongToFile(original, original.copy(title = "Renamed"), dir.absolutePath))

        assertEquals(
            listOf("##SongPresenter", "##Hymnal"),
            file.readLines().take(2),
            "the second header line names the songbook — losing it renames every song's songbook",
        )
        assertEquals("Hymnal", load(file).getSongs().single().songbook)
    }
}
