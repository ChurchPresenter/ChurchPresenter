package converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Statement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two library formats that are whole databases rather than one song per file — OpenLP's
 * `songs.sqlite` and SoftProjector's `.sps` — read at the edges of what they store.
 *
 * Both are shipped by every version of their app going back years, so the columns and tables a
 * current export has are not the ones an old one has. What matters is that a library missing the
 * author table, the songbook table or a verse order still imports every song it does hold: a song
 * short an author is a nuisance, a library that refuses to open is a migration abandoned.
 */
class LibraryDatabaseEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("converter-library-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun database(name: String, build: (Statement) -> Unit): File {
        val file = File(temp, name)
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use(build)
        }
        return file
    }

    private fun songsOnly(name: String, lyrics: String, verseOrder: String = ""): File =
        database(name) { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
            statement.executeUpdate(
                "INSERT INTO songs VALUES (1, 'Amazing Grace', '$lyrics', '$verseOrder', '', '')"
            )
        }

    private fun verse(type: String, label: String, body: String) =
        "<verse type=\"$type\" label=\"$label\"><![CDATA[$body]]></verse>"

    private fun lyricsXml(vararg verses: String) =
        "<song version=\"1.0\"><lyrics>" + verses.joinToString("") + "</lyrics></song>"

    // ── Is it even an OpenLP database ─────────────────────────────────────────

    @Test
    fun `a file too small to hold the SQLite header is not a database`() {
        assertFalse(OpenLpDatabaseConverter.isDatabase(File(temp, "tiny.sqlite").apply { writeBytes(ByteArray(4)) }))
    }

    @Test
    fun `a folder is not a database`() {
        assertFalse(OpenLpDatabaseConverter.isDatabase(File(temp, "folder.sqlite").apply { mkdirs() }))
    }

    @Test
    fun `an XML export is not mistaken for the database`() {
        val export = File(temp, "export.xml").apply { writeText("<song version=\"0.8\"/>", Charsets.UTF_8) }
        assertFalse(OpenLpDatabaseConverter.isDatabase(export))
    }

    // ── Libraries missing their side tables ───────────────────────────────────

    @Test
    fun `a library with no author or songbook table still imports its songs`() {
        val file = songsOnly("bare.sqlite", lyricsXml(verse("v", "1", "Amazing grace")))

        val song = OpenLpDatabaseConverter.parse(file).single()
        assertEquals("Amazing Grace", song.title)
        assertEquals("", song.author)
        assertEquals("", song.number)
    }

    @Test
    fun `an author with no display name is assembled from the two name columns`() {
        val file = database("names.sqlite") { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
            statement.executeUpdate(
                "CREATE TABLE authors (id INTEGER PRIMARY KEY, first_name TEXT, last_name TEXT, display_name TEXT)"
            )
            statement.executeUpdate("CREATE TABLE authors_songs (author_id INTEGER, song_id INTEGER)")
            statement.executeUpdate(
                "INSERT INTO songs VALUES (1, 'Amazing Grace', '${lyricsXml(verse("v", "1", "line"))}', '', '', '')"
            )
            statement.executeUpdate("INSERT INTO authors VALUES (1, 'John', 'Newton', '')")
            statement.executeUpdate("INSERT INTO authors VALUES (2, '', '', '')")
            statement.executeUpdate("INSERT INTO authors VALUES (3, '', '', 'Chris Tomlin')")
            statement.executeUpdate("INSERT INTO authors_songs VALUES (1, 1), (2, 1), (3, 1)")
        }

        assertEquals("John Newton, Chris Tomlin", OpenLpDatabaseConverter.parse(file).single().author)
    }

    @Test
    fun `a songbook entry that is blank leaves the song unnumbered`() {
        val file = database("numbers.sqlite") { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
            statement.executeUpdate("CREATE TABLE songs_songbooks (songbook_id INTEGER, song_id INTEGER, entry TEXT)")
            statement.executeUpdate(
                "INSERT INTO songs VALUES (1, 'Amazing Grace', '${lyricsXml(verse("v", "1", "line"))}', '', '', '')"
            )
            statement.executeUpdate("INSERT INTO songs_songbooks VALUES (1, 1, '   ')")
            statement.executeUpdate("INSERT INTO songs_songbooks VALUES (2, 1, '12')")
        }

        assertEquals("12", OpenLpDatabaseConverter.parse(file).single().number)
    }

    // ── The stored lyrics XML ─────────────────────────────────────────────────

    @Test
    fun `a song with no lyrics stored has no sections`() {
        assertTrue(OpenLpDatabaseConverter.sectionsOf("", "").isEmpty())
        assertTrue(OpenLpDatabaseConverter.sectionsOf("   ", "").isEmpty())
    }

    @Test
    fun `lyrics that are not XML at all leave the song without sections rather than failing`() {
        assertTrue(OpenLpDatabaseConverter.sectionsOf("Amazing grace, how sweet", "").isEmpty())
    }

    @Test
    fun `a verse with no type is a verse`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml("<verse label=\"1\"><![CDATA[Amazing grace]]></verse>"),
            "",
        )
        assertEquals(listOf("Verse 1"), sections.map { it.label })
    }

    @Test
    fun `an empty verse is left out`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml(verse("v", "1", "   "), verse("c", "1", "Praise the Lord")),
            "",
        )
        assertEquals(listOf("Chorus"), sections.map { it.label })
    }

    @Test
    fun `a slide break inside a verse is a line break, not a new section`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml(verse("v", "1", "first half[---]second half")),
            "",
        )
        assertEquals(listOf("first half", "second half"), sections.single().lines)
    }

    @Test
    fun `two verses stored under the same key are one section`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml(verse("v", "1", "first"), verse("v", "1", "second")),
            "",
        )
        assertEquals(listOf("first", "second"), sections.single().lines)
    }

    @Test
    fun `the verse order decides the order, and what it leaves out still follows`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml(verse("v", "1", "verse one"), verse("c", "1", "chorus"), verse("v", "2", "verse two")),
            "c1 v1",
        )
        assertEquals(listOf("Chorus", "Verse 1", "Verse 2"), sections.map { it.label })
    }

    @Test
    fun `an order naming a section that is not there is ignored`() {
        val sections = OpenLpDatabaseConverter.sectionsOf(
            lyricsXml(verse("v", "1", "verse one")),
            "b1  v1",
        )
        assertEquals(listOf("Verse 1"), sections.map { it.label })
    }

    @Test
    fun `a library holding no songs reports that rather than writing an empty folder`() {
        val file = database("empty.sqlite") { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
        }
        val result = OpenLpDatabaseConverter.convert(file, File(temp, "out"))
        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    // ── SoftProjector .sps ────────────────────────────────────────────────────

    private fun sps(name: String, vararg rows: String): File =
        File(temp, name).apply {
            writeText(
                buildString {
                    appendLine("##SoftProjector")
                    appendLine("##Hymns of Grace")
                    rows.forEach { appendLine(it) }
                },
                Charsets.UTF_8,
            )
        }

    private fun row(
        number: String,
        title: String,
        lyrics: String,
        author: String = "",
        composer: String = "",
        tune: String = "",
    ) =
        "$number#\$#$title#\$#x#\$#$tune#\$#$author#\$#$composer#\$#$lyrics"

    @Test
    fun `a chorus marker and a verse marker each open their own kind of section`() {
        val file = sps(
            "markers.sps",
            // `@$` separates sections and `@%` separates lines: the header of each is a bare
            // word that the writer wraps in the brackets ChurchPresenter reads.
            row("1", "Grace", "Куплет 1@%Amazing grace@\$Припев@%Praise the Lord"),
        )
        val out = File(temp, "sps-out").apply { mkdirs() }
        SpsToSongConverter.convert(file, out)

        val written = File(out, "Hymns of Grace").listFiles()!!.single().readText()
        assertTrue(written.contains("[Куплет 1]"), written)
        assertTrue(written.contains("{Припев}"), written)
    }

    @Test
    fun `a song with no lyrics at all is written with none rather than skipped`() {
        val file = sps("nolyrics.sps", row("1", "Silent", ""))
        val result = SpsToSongConverter.parse(file)
        assertEquals(listOf("Silent"), result.songs.map { it.title })
        assertTrue(result.songs.single().lyrics.isEmpty())
    }

    @Test
    fun `a row with no lyrics column is read for its metadata`() {
        val file = sps("short.sps", "1#\$#Short Row#\$#x#\$#tune#\$#author#\$#composer")
        assertEquals(listOf("Short Row"), SpsToSongConverter.parse(file).songs.map { it.title })
    }

    @Test
    fun `frontmatter is written only for the credits the song carries`() {
        val bare = sps("bare.sps", row("1", "Bare", "[V1]\nA line"))
        val credited = sps(
            "credited.sps",
            row("1", "Credited", "[V1]\nA line", author = "John Newton", tune = "ST ANNE"),
        )

        val out = File(temp, "credits-out").apply { mkdirs() }
        SpsToSongConverter.convert(bare, out)
        SpsToSongConverter.convert(credited, out)

        val files = File(out, "Hymns of Grace").listFiles()!!.associateBy { it.name.substringAfter("- ") }
        val bareText = files.values.first { it.readText().contains("Bare") }.readText()
        val creditedText = files.values.first { it.readText().contains("Credited") }.readText()

        assertFalse(bareText.contains("author:"), bareText)
        assertTrue(creditedText.contains("author: John Newton"), creditedText)
        assertTrue(creditedText.contains("tune: ST ANNE"), creditedText)
    }

    @Test
    fun `the songbook folder name is available before anything is converted`() {
        val book = sps("named.sps", row("1", "A", "[V1]\nline"))
        assertEquals("Hymns of Grace", SpsToSongConverter.getTargetFolderName(book))
    }
}
