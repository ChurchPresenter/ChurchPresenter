package converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Statement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * OpenLP's `songs.sqlite`, read directly. Fixtures are real databases built with the driver the
 * converter uses, including one shaped like an OpenLP 2.0 library — no `songs_songbooks`, no
 * `authors_songs` — because that is the version most people still migrating from are on.
 */
class OpenLpDatabaseConverterTest {

    private val temp: File = Files.createTempDirectory("converter-openlp-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun lyricsXml(vararg verses: Triple<String, String, String>): String =
        "<song version=\"1.0\"><lyrics>" +
            verses.joinToString("") { (type, label, body) ->
                "<verse type=\"$type\" label=\"$label\"><![CDATA[$body]]></verse>"
            } +
            "</lyrics></song>"

    private fun database(name: String, build: (Statement) -> Unit): File {
        val file = File(temp, name)
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use(build)
        }
        return file
    }

    private fun fullLibrary(name: String, lyrics: String, verseOrder: String = ""): File =
        database(name) { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
            statement.executeUpdate(
                "CREATE TABLE authors (id INTEGER PRIMARY KEY, first_name TEXT, last_name TEXT, display_name TEXT)"
            )
            statement.executeUpdate("CREATE TABLE authors_songs (author_id INTEGER, song_id INTEGER)")
            statement.executeUpdate("CREATE TABLE songs_songbooks (songbook_id INTEGER, song_id INTEGER, entry TEXT)")
            statement.executeUpdate(
                "INSERT INTO songs VALUES (1, 'Amazing Grace', '$lyrics', '$verseOrder', 'Public Domain', '22025')"
            )
            statement.executeUpdate("INSERT INTO authors VALUES (1, 'John', 'Newton', 'John Newton')")
            statement.executeUpdate("INSERT INTO authors_songs VALUES (1, 1)")
            statement.executeUpdate("INSERT INTO songs_songbooks VALUES (1, 1, '12')")
        }

    @Test
    fun `a song comes back with the author the bridging table points at`() {
        val file = fullLibrary("songs.sqlite", lyricsXml(Triple("v", "1", "Amazing grace")))

        val song = OpenLpDatabaseConverter.parse(file).single()

        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
        assertEquals("12", song.number)
    }

    @Test
    fun `the stored lyrics XML becomes sections, one per verse element`() {
        val lyrics = lyricsXml(
            Triple("v", "1", "Amazing grace\nhow sweet"),
            Triple("c", "1", "Praise the Lord"),
        )

        val sections = OpenLpDatabaseConverter.parse(fullLibrary("songs.sqlite", lyrics)).single().sections

        assertEquals(listOf("Verse 1", "Chorus"), sections.map { it.label })
        assertEquals(listOf("Amazing grace", "how sweet"), sections.first().lines)
    }

    @Test
    fun `verse order decides the order the sections are written in`() {
        val lyrics = lyricsXml(Triple("v", "1", "One"), Triple("c", "1", "Praise"))

        val sections = OpenLpDatabaseConverter.sectionsOf(lyrics, "c1 v1")

        assertEquals(listOf("Chorus", "Verse 1"), sections.map { it.label })
    }

    @Test
    fun `a slide split inside a verse is dropped rather than sung`() {
        val lyrics = lyricsXml(Triple("v", "1", "First half[---]Second half"))

        val sections = OpenLpDatabaseConverter.sectionsOf(lyrics, "")

        assertEquals(listOf("First half", "Second half"), sections.single().lines)
    }

    @Test
    fun `a library from before the songbook and author tables existed still imports`() {
        val file = database("old.sqlite") { statement ->
            statement.executeUpdate(
                "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                    "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
            )
            statement.executeUpdate(
                "INSERT INTO songs VALUES (1, 'Grace', '${lyricsXml(Triple("v", "1", "Line"))}', '', '', '')"
            )
        }

        val song = OpenLpDatabaseConverter.parse(file).single()

        assertEquals("Grace", song.title)
        assertEquals("", song.author)
        assertEquals("", song.number)
    }

    @Test
    fun `a database that is not OpenLP's is rejected rather than silently importing nothing`() {
        val file = database("other.sqlite") { statement ->
            statement.executeUpdate("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT)")
        }

        assertFailsWith<IllegalArgumentException> { OpenLpDatabaseConverter.parse(file) }
    }

    @Test
    fun `an XML export is not mistaken for a database`() {
        val xml = File(temp, "export.xml").apply { writeText("<song></song>", Charsets.UTF_8) }
        val database = fullLibrary("songs.sqlite", lyricsXml(Triple("v", "1", "Line")))

        assertTrue(!OpenLpDatabaseConverter.isDatabase(xml))
        assertTrue(OpenLpDatabaseConverter.isDatabase(database))
    }

    @Test
    fun `converting writes one numbered file per song`() {
        val file = fullLibrary("songs.sqlite", lyricsXml(Triple("v", "1", "Amazing grace")))
        val out = File(temp, "out")

        val result = OpenLpDatabaseConverter.convert(file, out)

        assertEquals(listOf("0012 - Amazing Grace.song"), result.outputFiles.map { it.name })
        val text = result.outputFiles.single().readText()
        assertTrue(text.contains("[Verse 1]\nAmazing grace"), text)
    }
}
