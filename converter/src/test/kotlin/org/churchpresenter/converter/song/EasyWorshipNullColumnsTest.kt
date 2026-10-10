package org.churchpresenter.converter.song

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Statement
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EasyWorshipNullColumnsTest {

    private val temp: File = Files.createTempDirectory("easyworship-nulls").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sqlite(file: File, build: (Statement) -> Unit): File {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c -> c.createStatement().use(build) }
        return file
    }

    private fun zip(file: File, vararg entries: Pair<String, ByteArray>): File {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for ((name, data) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        return file.apply { writeBytes(bytes) }
    }

    @Test
    fun `a library row with every column null comes back as a blank song`() {
        val folder = File(temp, "Data").apply { mkdirs() }
        sqlite(File(folder, "Songs.db")) {
            it.executeUpdate("CREATE TABLE song (title TEXT, author TEXT, copyright TEXT, vendor_id TEXT)")
            it.executeUpdate("INSERT INTO song VALUES (NULL, NULL, NULL, NULL)")
        }
        sqlite(File(folder, "SongWords.db")) {
            it.executeUpdate("CREATE TABLE word (song_id INTEGER, words TEXT)")
            it.executeUpdate("INSERT INTO word VALUES (1, NULL)")
        }

        val song = EasyWorshipConverter.parse(folder).single()
        assertEquals("", song.title)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
        assertEquals("", song.ccli)
        assertTrue(song.sections.isEmpty())
    }

    @Test
    fun `a folder with no Songs database is refused, even when a folder carries that name`() {
        val folder = File(temp, "Empty").apply { mkdirs() }
        File(folder, "Songs.db").mkdirs()
        val error = assertFailsWith<IllegalArgumentException> { EasyWorshipConverter.parse(folder) }
        assertTrue(error.message!!.contains("No Songs.db"), error.message!!)
    }

    @Test
    fun `a service with null columns and a null slide reads as a blank song`() {
        val database = sqlite(File(temp, "main.db")) { statement ->
            statement.executeUpdate(
                "CREATE TABLE presentation (title TEXT, author TEXT, copyright TEXT, " +
                    "reference_number TEXT, presentation_type INTEGER)",
            )
            statement.executeUpdate("CREATE TABLE slide (presentation_id INTEGER, order_index INTEGER)")
            statement.executeUpdate(
                "CREATE TABLE element (slide_id INTEGER, foreground_resource_id INTEGER, " +
                    "element_type INTEGER, element_style_type INTEGER)",
            )
            statement.executeUpdate("CREATE TABLE resource_text (resource_id INTEGER, rtf TEXT)")
            statement.executeUpdate("INSERT INTO presentation VALUES (NULL, NULL, NULL, NULL, 6)")
            statement.executeUpdate("INSERT INTO slide (rowid, presentation_id, order_index) VALUES (1, 1, 0)")
            statement.executeUpdate("INSERT INTO element VALUES (1, 1, 6, 4)")
            statement.executeUpdate("INSERT INTO resource_text VALUES (1, NULL)")
        }
        val service = zip(File(temp, "nulls.ewsx"), "Service/MAIN.DB" to database.readBytes())

        val song = EasyWorshipConverter.parse(service).single()
        assertEquals("", song.title)
        assertEquals("", song.author)
        assertEquals("", song.ccli)
        assertTrue(song.sections.isEmpty())
    }

    @Test
    fun `a service archive without its database is reported`() {
        val service = zip(File(temp, "nodb.ewsx"), "other.txt" to "x".toByteArray())
        val error = assertFailsWith<IllegalArgumentException> { EasyWorshipConverter.parse(service) }
        assertTrue(error.message!!.contains("No main.db"), error.message!!)
    }

    @Test
    fun `reading a zip entry finds it by name at the root and returns null when it is absent`() {
        val archive = zip(File(temp, "a.zip"), "skip.bin" to byteArrayOf(9), "main.db" to byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), EasyWorshipConverter.readZipEntryIgnoringChecksum(archive, "MAIN.db"))
        assertNull(EasyWorshipConverter.readZipEntryIgnoringChecksum(archive, "absent.db"))
    }

    @Test
    fun `only a file at least as long as the SQLite header can be SQLite`() {
        assertFalse(EasyWorshipConverter.isSqlite(temp))
        assertFalse(EasyWorshipConverter.isSqlite(File(temp, "short.db").apply { writeBytes(ByteArray(4)) }))
        val notSqlite = File(temp, "long.db").apply { writeText("Not SQLite at all, really") }
        assertFalse(EasyWorshipConverter.isSqlite(notSqlite))
    }

    @Test
    fun `a library with no songs converts to a reported empty result`() {
        val folder = File(temp, "Data").apply { mkdirs() }
        sqlite(File(folder, "Songs.db")) {
            it.executeUpdate("CREATE TABLE song (title TEXT, author TEXT, copyright TEXT, vendor_id TEXT)")
        }
        sqlite(File(folder, "SongWords.db")) { it.executeUpdate("CREATE TABLE word (song_id INTEGER, words TEXT)") }

        val result = EasyWorshipConverter.convert(folder, File(temp, "out"))
        assertTrue(result.outputFiles.isEmpty())
        assertEquals(listOf("No songs in Data"), result.errors)
    }
}
