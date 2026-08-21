package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The SQLite flavour of `.sps`, which newer SoftProjector libraries use.
 *
 * The format is chosen by sniffing the file header rather than the extension, because both
 * flavours share it — so the detection itself is part of what these tests defend. Fixtures are
 * real SQLite databases built with the driver the converter uses.
 */
class SpsSqliteTest {

    private val temp: File = Files.createTempDirectory("converter-sqlite-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** Builds a `.sps` SQLite library with the schema the converter reads. */
    private fun sqliteSps(
        name: String = "library",
        songbookTitle: String? = "Hymns of Grace",
        songs: List<Array<String?>> = listOf(
            arrayOf("1", "Amazing Grace", "cat", "New Britain", "John Newton", "Traditional", "Amazing grace how sweet")
        ),
    ): File {
        val file = File(temp, "$name.sps")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use { s ->
                s.executeUpdate("CREATE TABLE SongBook (title TEXT)")
                s.executeUpdate(
                    "CREATE TABLE Songs (number TEXT, title TEXT, category TEXT, tune TEXT, " +
                        "words TEXT, music TEXT, song_text TEXT)"
                )
            }
            if (songbookTitle != null) {
                c.prepareStatement("INSERT INTO SongBook (title) VALUES (?)").use {
                    it.setString(1, songbookTitle); it.executeUpdate()
                }
            }
            c.prepareStatement("INSERT INTO Songs VALUES (?,?,?,?,?,?,?)").use { st ->
                for (row in songs) {
                    row.forEachIndexed { i, v -> st.setString(i + 1, v) }
                    st.executeUpdate()
                }
            }
        }
        return file
    }

    @Test
    fun `a SQLite library is detected by its file header, not its extension`() {
        val parsed = SpsToSongConverter.parse(sqliteSps())
        assertEquals("Hymns of Grace", parsed.songbookName)
        assertEquals(1, parsed.songs.size, "the SQLite path was taken, not the text one")
    }

    @Test
    fun `each column maps onto the song's own field`() {
        val song = SpsToSongConverter.parse(sqliteSps()).songs.single()
        assertEquals("1", song.number)
        assertEquals("Amazing Grace", song.title)
        assertEquals("New Britain", song.tune)
        assertEquals("John Newton", song.author, "the `words` column is the author")
        assertEquals("Traditional", song.composer, "the `music` column is the composer")
        assertEquals("Hymns of Grace", song.songbook)
        assertTrue(song.lyrics.isNotEmpty())
    }

    @Test
    fun `songs come back in number order`() {
        val parsed = SpsToSongConverter.parse(
            sqliteSps(
                songs = listOf(
                    arrayOf("3", "Third", "c", "t", "a", "m", "Lyric"),
                    arrayOf("1", "First", "c", "t", "a", "m", "Lyric"),
                    arrayOf("2", "Second", "c", "t", "a", "m", "Lyric"),
                )
            )
        )
        assertEquals(listOf("1", "2", "3"), parsed.songs.map { it.number })
    }

    @Test
    fun `null columns become empty strings rather than the literal null`() {
        val song = SpsToSongConverter.parse(
            sqliteSps(songs = listOf(arrayOf("1", "Title", null, null, null, null, null)))
        ).songs.single()
        assertEquals("", song.tune)
        assertEquals("", song.author)
        assertEquals("", song.composer)
        assertTrue(song.lyrics.isEmpty(), "no song text means no lyrics")
    }

    @Test
    fun `surrounding whitespace is trimmed off every field`() {
        val song = SpsToSongConverter.parse(
            sqliteSps(
                songs = listOf(arrayOf("  7  ", "  Padded  ", "c", "  tune  ", "  author  ", "  music  ", "Lyric"))
            )
        ).songs.single()
        assertEquals("7", song.number)
        assertEquals("Padded", song.title)
        assertEquals("tune", song.tune)
    }

    @Test
    fun `a library with no songbook title falls back to the file name`() {
        assertEquals("named", SpsToSongConverter.parse(sqliteSps(name = "named", songbookTitle = null)).songbookName)
    }

    @Test
    fun `an empty songbook title falls back to the file name too`() {
        assertEquals("blank", SpsToSongConverter.parse(sqliteSps(name = "blank", songbookTitle = "")).songbookName)
    }

    @Test
    fun `converting a SQLite library writes numbered song files`() {
        val file = sqliteSps(
            songs = listOf(
                arrayOf("1", "First", "c", "t", "a", "m", "Lyric one"),
                arrayOf("25", "Twenty Fifth", "c", "t", "a", "m", "Lyric two"),
            )
        )
        val result = SpsToSongConverter.convert(file, temp)
        assertEquals(2, result.songsConverted)
        val names = File(result.songbookFolder).listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("0001 - First.song", "0025 - Twenty Fifth.song"), names)
    }

    @Test
    fun `control characters in stored lyrics are cleaned up on the way out`() {
        // SoftProjector wrote vertical tabs where newlines belong; they render as junk if kept.
        val song = SpsToSongConverter.parse(
            sqliteSps(songs = listOf(arrayOf("1", "T", "c", "t", "a", "m", "line one\u000Bline two")))
        ).songs.single()
        assertTrue(song.lyrics.none { it.contains('\u000B') }, "got ${song.lyrics}")
        assertTrue(song.lyrics.size >= 2, "the vertical tab became a line break: ${song.lyrics}")
    }
}
