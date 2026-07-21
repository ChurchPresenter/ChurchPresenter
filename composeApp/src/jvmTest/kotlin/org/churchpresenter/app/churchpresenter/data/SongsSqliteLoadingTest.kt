package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.sql.Connection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reading a `.sps` songbook that is really a SQLite database.
 *
 * The Mac build of SongPresenter saves its songbooks as SQLite under the same `.sps` extension the
 * Windows build uses for a delimited text file, so the loader cannot go by the name — it sniffs the
 * first sixteen bytes for the `SQLite format 3` magic and takes a different path entirely. Both
 * paths are reached by the same call from the songs tab, and the failure when the sniff is wrong is
 * not an error message: the file parses as text, no line has enough fields, and the songbook loads
 * as empty.
 *
 * The lyrics differ too. The text format packs a whole song into one field with `@$`/`@%` markers
 * and the loader rebuilds a running order from it (see [SongsTest]); the SQLite format already has
 * real newlines, so lines are kept exactly as written and only the section headings are wrapped —
 * which means the chorus is NOT lifted out and repeated here. That difference is pinned below.
 */
class SongsSqliteLoadingTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-songs-sqlite-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    /** One row of the Mac `Songs` table. */
    private data class Row(
        val number: String,
        val title: String,
        val category: String = "1",
        val tune: String = "",
        val words: String = "",
        val music: String = "",
        val songText: String = "",
    )

    /**
     * Writes a real SQLite database in the shape the Mac app uses, and loads it as a `.sps` file.
     * A null [songbook] leaves the `SongBook` table out entirely, as some exported files do.
     */
    private fun loadSqlite(vararg rows: Row, songbook: String? = "Hymnal", name: String = "library.sps"): Songs {
        val file = File(dir, name)
        JdbcDatabase.openConnection(file.absolutePath).use { c: Connection ->
            c.createStatement().use { st ->
                if (songbook != null) {
                    st.executeUpdate("CREATE TABLE SongBook (title TEXT)")
                    st.executeUpdate("INSERT INTO SongBook VALUES ('$songbook')")
                }
                st.executeUpdate(
                    "CREATE TABLE Songs (number TEXT, title TEXT, category TEXT, tune TEXT, " +
                        "words TEXT, music TEXT, song_text TEXT)",
                )
            }
            rows.forEach { row ->
                c.prepareStatement("INSERT INTO Songs VALUES (?, ?, ?, ?, ?, ?, ?)").use { st ->
                    listOf(row.number, row.title, row.category, row.tune, row.words, row.music, row.songText)
                        .forEachIndexed { i, value -> st.setString(i + 1, value) }
                    st.executeUpdate()
                }
            }
        }
        return Songs().also { it.loadFromSps(file.absolutePath) }
    }

    // ── Telling the two formats apart ───────────────────────────────────────────

    @Test
    fun `a sps file that is really a database is read as one`() {
        val songs = loadSqlite(Row(number = "1", title = "Amazing Grace"))

        assertEquals(
            listOf("Amazing Grace"),
            songs.getSongs().map { it.title },
            "read as text this file has no line with enough fields, and the songbook would load empty",
        )
    }

    @Test
    fun `a file too short to hold the magic is still read as text`() {
        // The sniff reads sixteen bytes and has to cope with a file shorter than that.
        val file = File(dir, "tiny.sps").also { it.writeText("##SP\n", Charsets.UTF_8) }

        val songs = Songs().also { it.loadFromSps(file.absolutePath) }

        assertEquals(0, songs.getSongCount(), "a short file has no songs, but must not throw either")
    }

    // ── What comes out of a row ─────────────────────────────────────────────────

    @Test
    fun `a song is read with all its details`() {
        val songs = loadSqlite(
            Row(number = "1", title = "Amazing Grace", tune = "NEW BRITAIN", words = "Newton", music = "Excell"),
        )

        val song = songs.getSongs().single()
        assertEquals("1", song.number)
        assertEquals("Amazing Grace", song.title)
        assertEquals("NEW BRITAIN", song.tune, "the Mac column is `tune`, not `key`")
        assertEquals("Newton", song.author, "`words` is the author")
        assertEquals("Excell", song.composer, "`music` is the composer")
    }

    @Test
    fun `padding around a stored value is stripped`() {
        val songs = loadSqlite(
            Row(number = " 1 ", title = "  Amazing Grace  ", tune = " G ", words = " Newton ", music = " Excell "),
        )

        val song = songs.getSongs().single()
        assertEquals("1", song.number, "a padded number would never match a search or a schedule entry")
        assertEquals("Amazing Grace", song.title)
        assertEquals("G", song.tune)
        assertEquals("Newton", song.author)
        assertEquals("Excell", song.composer)
    }

    @Test
    fun `songs come out in number order`() {
        val songs = loadSqlite(
            Row(number = "3", title = "Third"),
            Row(number = "1", title = "First"),
            Row(number = "2", title = "Second"),
        )

        assertEquals(
            listOf("First", "Second", "Third"),
            songs.getSongs().map { it.title },
            "the list is presented in the order it loads, and a songbook is browsed by number",
        )
    }

    @Test
    fun `every song in the database is read`() {
        val songs = loadSqlite(*(1..25).map { Row(number = "$it", title = "Song $it") }.toTypedArray())

        assertEquals(25, songs.getSongCount())
    }

    // ── Which songbook the songs belong to ──────────────────────────────────────

    @Test
    fun `the songbook name comes from the database`() {
        val songs = loadSqlite(Row(number = "1", title = "A"), songbook = "Songs of Praise")

        assertEquals("Songs of Praise", songs.getSongs().single().songbook)
        assertEquals(1, songs.getSongsBySongbook("Songs of Praise").size, "the filter has to find it under that name")
    }

    @Test
    fun `a database with no songbook table falls back to the file name`() {
        val songs = loadSqlite(Row(number = "1", title = "A"), songbook = null, name = "Grace Hymns.sps")

        assertEquals(
            "Grace Hymns",
            songs.getSongs().single().songbook,
            "an unnamed songbook must still be tellable apart from the others in the picker",
        )
    }

    @Test
    fun `a blank songbook name falls back to the file name`() {
        val songs = loadSqlite(Row(number = "1", title = "A"), songbook = "", name = "Grace Hymns.sps")

        assertEquals("Grace Hymns", songs.getSongs().single().songbook, "an empty title would leave a blank filter entry")
    }

    // ── Lyrics ──────────────────────────────────────────────────────────────────

    @Test
    fun `lyrics are kept line for line`() {
        val songs = loadSqlite(
            Row(number = "1", title = "Simple", songText = "Verse 1\nAmazing grace\nhow sweet the sound"),
        )

        assertEquals(
            listOf("[Verse 1]", "Amazing grace", "how sweet the sound"),
            songs.getSongs().single().lyrics,
        )
    }

    @Test
    fun `blank lines between sections are kept as they separate the slides`() {
        val songs = loadSqlite(
            Row(number = "1", title = "Two verses", songText = "Verse 1\nFirst line\n\nVerse 2\nSecond line"),
        )

        assertEquals(
            listOf("[Verse 1]", "First line", "", "[Verse 2]", "Second line"),
            songs.getSongs().single().lyrics,
            "unlike the text format, this one is already in running order — the blank line is the break",
        )
    }

    @Test
    fun `the chorus is left where it was written`() {
        // Deliberately unlike the text format, which lifts the chorus out and repeats it after every
        // verse. Here the stored text is already the running order, so repeating it would double it.
        val songs = loadSqlite(
            Row(
                number = "1", title = "With chorus",
                songText = "Verse 1\nFirst verse\n\nChorus\nHallelujah\n\nVerse 2\nSecond verse",
            ),
        )

        val text = songs.getSongs().single().lyrics
        assertEquals(1, text.count { it == "Hallelujah" }, "the chorus is sung where the file puts it, once")
        assertEquals(
            listOf("[Verse 1]", "First verse", "", "{Chorus}", "Hallelujah", "", "[Verse 2]", "Second verse"),
            text,
        )
    }

    /**
     * Documents a KNOWN GAP: a heading is recognised by prefix alone, so a sung line that happens to
     * begin with one of the heading words is wrapped as though it were a heading.
     *
     * `"Chorus of angels sing"` becomes `"{Chorus of angels sing}"`, and the braces are then shown
     * on screen as part of the lyric. Any English line opening with "Verse", "Chorus", "Refrain" or
     * "Bridge" is affected, and the same applies to the text format (both share `wrapSectionHeader`).
     * The fix is to require the rest of the line to be a section number or nothing at all; this
     * expectation then becomes the line left untouched.
     */
    @Test
    fun `a lyric line that opens with a heading word is wrapped as a heading -- known gap`() {
        val songs = loadSqlite(
            Row(number = "1", title = "Unlucky", songText = "Verse 1\nChorus of angels sing"),
        )

        assertEquals(
            listOf("[Verse 1]", "{Chorus of angels sing}"),
            songs.getSongs().single().lyrics,
            "current behaviour: the braces reach the screen as part of the projected line",
        )
    }

    @Test
    fun `section headings are wrapped in either language`() {
        val songs = loadSqlite(
            Row(number = "1", title = "Mixed", songText = "Куплет 1\nстрока\n\nПрипев\nприпев\n\nBridge\nline"),
        )

        val text = songs.getSongs().single().lyrics
        assertTrue("[Куплет 1]" in text, "$text")
        assertTrue("{Припев}" in text, "$text")
        assertTrue("[Bridge]" in text, "$text")
    }

    @Test
    fun `a heading the file already wrapped is left alone`() {
        val songs = loadSqlite(Row(number = "1", title = "Wrapped", songText = "[Verse 1]\nline"))

        assertEquals(listOf("[Verse 1]", "line"), songs.getSongs().single().lyrics, "no double wrapping")
    }

    @Test
    fun `windows line endings do not leave a stray carriage return`() {
        val songs = loadSqlite(Row(number = "1", title = "CRLF", songText = "Verse 1\r\nAmazing grace\r\nhow sweet"))

        assertEquals(
            listOf("[Verse 1]", "Amazing grace", "how sweet"),
            songs.getSongs().single().lyrics,
            "a trailing \\r would defeat the heading match and show up in the projected line",
        )
    }

    @Test
    fun `trailing blank lines are dropped`() {
        val songs = loadSqlite(Row(number = "1", title = "Padded", songText = "Verse 1\nOnly line\n\n\n"))

        assertEquals(
            listOf("[Verse 1]", "Only line"),
            songs.getSongs().single().lyrics,
            "a trailing blank would step the operator onto an empty slide at the end of the song",
        )
    }

    @Test
    fun `a song with no lyrics still loads`() {
        val songs = loadSqlite(Row(number = "1", title = "Titles only", songText = ""))

        assertTrue(songs.getSongs().single().lyrics.isEmpty())
        assertEquals("Titles only", songs.getSongs().single().title, "the song is still in the songbook")
    }

    // ── Loading more than one songbook ──────────────────────────────────────────

    @Test
    fun `a database songbook can be appended to a text one`() {
        val text = File(dir, "text.sps").also {
            it.writeText("##SongPresenter\n##Text Book\n1#\$#From text#\$#1#\$#G#\$##\$##\$#", Charsets.UTF_8)
        }
        val songs = Songs().also { it.loadFromSps(text.absolutePath) }

        val database = File(dir, "database.sps")
        JdbcDatabase.openConnection(database.absolutePath).use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE SongBook (title TEXT)")
                st.executeUpdate("INSERT INTO SongBook VALUES ('Database Book')")
                st.executeUpdate(
                    "CREATE TABLE Songs (number TEXT, title TEXT, category TEXT, tune TEXT, " +
                        "words TEXT, music TEXT, song_text TEXT)",
                )
                st.executeUpdate("INSERT INTO Songs VALUES ('1', 'From database', '1', '', '', '', '')")
            }
        }
        songs.loadFromSpsAppend(database.absolutePath)

        assertEquals(listOf("From text", "From database"), songs.getSongs().map { it.title })
        assertEquals(
            listOf("Text Book", "Database Book"),
            songs.getSongs().map { it.songbook },
            "a library mixing both formats has to keep each songbook's own name",
        )
    }
}
