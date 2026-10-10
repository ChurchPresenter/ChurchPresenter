package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Statement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpsBranchesTest {

    private val temp: File = Files.createTempDirectory("sps-branches").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sqlite(name: String, build: (Statement) -> Unit): File {
        val file = File(temp, name)
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c -> c.createStatement().use(build) }
        return file
    }

    private fun songsTable(statement: Statement) {
        statement.executeUpdate(
            "CREATE TABLE Songs (number TEXT, title TEXT, category TEXT, tune TEXT, " +
                "words TEXT, music TEXT, song_text TEXT)",
        )
        statement.executeUpdate("INSERT INTO Songs VALUES ('1', 'Hymn', '', '', '', '', 'line')")
    }

    private fun text(name: String, vararg rows: String): File =
        File(temp, name).apply { writeText("##SoftProjector\n##Book\n" + rows.joinToString("\n"), Charsets.UTF_8) }

    @Test
    fun `a SQLite library without a SongBook table is named after its file`() {
        val file = sqlite("untitled.sps") { songsTable(it) }
        assertEquals("untitled", SpsToSongConverter.parse(file).songbookName)
    }

    @Test
    fun `a SQLite library with an empty SongBook table is named after its file`() {
        val file = sqlite("emptybook.sps") {
            it.executeUpdate("CREATE TABLE SongBook (title TEXT)")
            songsTable(it)
        }
        val parsed = SpsToSongConverter.parse(file)
        assertEquals("emptybook", parsed.songbookName)
        assertEquals(listOf("Hymn"), parsed.songs.map { it.title })
    }

    @Test
    fun `blank sections and blank lines inside a section are dropped`() {
        val file = text("blanks.sps", "1#\$#Hymn#\$#x#\$##\$##\$##\$#@\$  @%  @\$Verse 1@%  @%first@\$@\$second@%")
        assertEquals(listOf("[Verse 1]", "first", "", "second"), SpsToSongConverter.parse(file).songs.single().lyrics)
    }

    @Test
    fun `a row with too few columns is skipped`() {
        val file = text("short.sps", "1#\$#Only#\$#three", "2#\$#Full#\$#x#\$#t#\$#a#\$#c#\$#line")
        assertEquals(listOf("Full"), SpsToSongConverter.parse(file).songs.map { it.title })
    }

    @Test
    fun `frontmatter is written for a composer or a tune alone`() {
        val file = text(
            "credits.sps",
            "1#\$#Composed#\$#x#\$##\$##\$#Bach#\$#line",
            "2#\$#Tuned#\$#x#\$#ST ANNE#\$##\$##\$#line",
        )
        val result = SpsToSongConverter.convert(file, temp)
        assertEquals(2, result.songsConverted)
        val written = File(result.songbookFolder).listFiles()!!.associate { it.name to it.readText() }
        val composed = written.getValue("0001 - Composed.song")
        assertTrue(composed.startsWith("---\ncomposer: Bach\n---"), composed)
        val tuned = written.getValue("0002 - Tuned.song")
        assertTrue(tuned.startsWith("---\ntune: ST ANNE\n---"), tuned)
    }

    @Test
    fun `a song that cannot be written is reported while the rest convert`() {
        val file = text("blocked.sps", "1#\$#Blocked#\$#x#\$##\$##\$##\$#line", "2#\$#Fine#\$#x#\$##\$##\$##\$#line")
        File(temp, "Book/0001 - Blocked.song").mkdirs()

        val result = SpsToSongConverter.convert(file, temp)
        assertEquals(1, result.songsConverted)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().startsWith("Error converting song 1 - Blocked"), result.errors.single())
    }

    @Test
    fun `a file with no songs converts to a reported empty result`() {
        val result = SpsToSongConverter.convert(text("none.sps"), temp)
        assertEquals(0, result.songsConverted)
        assertEquals(listOf("No songs found in file"), result.errors)
    }
}
