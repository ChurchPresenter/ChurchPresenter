package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenLpNullColumnsTest {

    private val temp: File = Files.createTempDirectory("openlp-nulls").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `null columns everywhere leave blank fields rather than failing`() {
        val file = File(temp, "songs.sqlite")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { s ->
                s.executeUpdate(
                    "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                        "verse_order TEXT, copyright TEXT, ccli_number TEXT)",
                )
                s.executeUpdate(
                    "CREATE TABLE authors (id INTEGER PRIMARY KEY, first_name TEXT, last_name TEXT, display_name TEXT)",
                )
                s.executeUpdate("CREATE TABLE authors_songs (author_id INTEGER, song_id INTEGER)")
                s.executeUpdate("CREATE TABLE songs_songbooks (songbook_id INTEGER, song_id INTEGER, entry TEXT)")
                s.executeUpdate("INSERT INTO songs VALUES (1, NULL, NULL, NULL, NULL, NULL)")
                s.executeUpdate("INSERT INTO authors VALUES (1, NULL, NULL, NULL)")
                s.executeUpdate("INSERT INTO authors VALUES (2, 'John', NULL, NULL)")
                s.executeUpdate("INSERT INTO authors_songs VALUES (1, 1)")
                s.executeUpdate("INSERT INTO authors_songs VALUES (2, 1)")
                s.executeUpdate("INSERT INTO songs_songbooks VALUES (1, 1, NULL)")
            }
        }

        val song = OpenLpDatabaseConverter.parse(file).single()
        assertEquals("", song.title)
        assertEquals("John", song.author)
        assertEquals("", song.copyright)
        assertEquals("", song.ccli)
        assertEquals("", song.number)
        assertTrue(song.verseOrder.isEmpty())
        assertTrue(song.sections.isEmpty())
    }
}
