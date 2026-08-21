package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The SQLite flavours are built here with the same driver the converter reads them with, which is a
 * truer fixture than a copied file. The `.ews` schedules are real files: the format is a fixed
 * binary layout, so a hand-made one would only prove the test agrees with itself.
 */
class EasyWorshipConverterTest {

    private val temp: File = Files.createTempDirectory("easyworship-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sample(name: String): File =
        File(javaClass.classLoader.getResource("easyworship/$name")!!.toURI())

    private fun rtf(body: String) =
        "{\\rtf1\\ansi\\ansicpg1252{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\pard $body}"

    /** An EasyWorship 6 data folder: the two databases the product keeps side by side. */
    private fun library(
        songs: List<Array<String>> = listOf(
            arrayOf("Amazing Grace", "John Newton", "Public Domain", "22025"),
        ),
        words: List<String> = listOf(
            rtf("Verse 1\\par Amazing grace how sweet the sound\\par\\par Chorus\\par Praise the Lord\\par"),
        ),
    ): File {
        val folder = File(temp, "Data").apply { mkdirs() }
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${File(folder, "Songs.db").absolutePath}").use { c ->
            c.createStatement().use {
                it.executeUpdate("CREATE TABLE song (title TEXT, author TEXT, copyright TEXT, vendor_id TEXT)")
            }
            c.prepareStatement("INSERT INTO song VALUES (?,?,?,?)").use { statement ->
                for (row in songs) {
                    row.forEachIndexed { i, v -> statement.setString(i + 1, v) }
                    statement.executeUpdate()
                }
            }
        }
        DriverManager.getConnection("jdbc:sqlite:${File(folder, "SongWords.db").absolutePath}").use { c ->
            c.createStatement().use { it.executeUpdate("CREATE TABLE word (song_id INTEGER, words TEXT)") }
            c.prepareStatement("INSERT INTO word VALUES (?,?)").use { statement ->
                words.forEachIndexed { index, text ->
                    statement.setInt(1, index + 1); statement.setString(2, text); statement.executeUpdate()
                }
            }
        }
        return folder
    }

    @Test
    fun `a version 6 library reads its titles from one database and its words from the other`() {
        val songs = EasyWorshipConverter.parse(library())

        val song = songs.single()
        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
        assertEquals("22025", song.ccli)
        assertEquals(listOf("Verse 1", "Chorus"), song.sections.map { it.label })
        assertEquals(listOf("Amazing grace how sweet the sound"), song.sections.first().lines)
    }

    @Test
    fun `several authors in one field are separated however the person typing separated them`() {
        val songs = EasyWorshipConverter.parse(
            library(songs = listOf(arrayOf("Hymn", "John Newton/Edwin Excell", "", "")))
        )
        assertEquals("John Newton, Edwin Excell", songs.single().author)
    }

    @Test
    fun `a library missing its words database is reported rather than read as empty songs`() {
        val folder = library()
        File(folder, "SongWords.db").delete()

        val failure = assertFailsWith<IllegalArgumentException> { EasyWorshipConverter.parse(folder) }
        assertTrue(failure.message!!.contains("SongWords.db"), failure.message!!)
    }

    @Test
    fun `pointing at the Songs database itself works as well as pointing at its folder`() {
        val folder = library()
        assertEquals(
            EasyWorshipConverter.parse(folder).map { it.title },
            EasyWorshipConverter.parse(File(folder, "Songs.db")).map { it.title },
        )
    }

    @Test
    fun `converting a library writes one song file per song`() {
        val out = File(temp, "out")
        val result = EasyWorshipConverter.convert(library(), out)

        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertEquals(listOf("Amazing Grace.song"), result.outputFiles.map { it.name })
        assertTrue(result.outputFiles.single().readText().contains("Amazing grace how sweet the sound"))
    }

    // --- .ewsx ---

    /**
     * EasyWorship stores `main.db` with a checksum that does not match its own bytes, so the reader
     * has to accept a mismatch. The fixture reproduces that deliberately: written with a correct
     * CRC it would pass whether or not the reader coped.
     */
    private fun serviceFile(): File {
        val database = File(temp, "main.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { c ->
            c.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE presentation (title TEXT, author TEXT, copyright TEXT, " +
                        "reference_number TEXT, presentation_type INTEGER)"
                )
                statement.executeUpdate("CREATE TABLE slide (presentation_id INTEGER, order_index INTEGER)")
                statement.executeUpdate(
                    "CREATE TABLE element (slide_id INTEGER, foreground_resource_id INTEGER, " +
                        "element_type INTEGER, element_style_type INTEGER)"
                )
                statement.executeUpdate("CREATE TABLE resource_text (resource_id INTEGER, rtf TEXT)")
                statement.executeUpdate(
                    "INSERT INTO presentation VALUES ('Be Thou My Vision', 'Dallan Forgaill', '', '30639', 6)"
                )
                statement.executeUpdate("INSERT INTO slide (rowid, presentation_id, order_index) VALUES (1, 1, 0)")
                statement.executeUpdate("INSERT INTO slide (rowid, presentation_id, order_index) VALUES (2, 1, 1)")
                statement.executeUpdate("INSERT INTO element VALUES (1, 1, 6, 4)")
                statement.executeUpdate("INSERT INTO element VALUES (2, 2, 6, 4)")
                statement.executeUpdate(
                    "INSERT INTO resource_text VALUES (1, '${rtf("Be Thou my vision\\par O Lord of my heart")}')"
                )
                statement.executeUpdate("INSERT INTO resource_text VALUES (2, '${rtf("Naught be all else to me")}')")
            }
        }

        val archive = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("main.db"))
                zip.write(database.readBytes())
                zip.closeEntry()
            }
        }.toByteArray()

        // ZipOutputStream will not write a checksum that disagrees with the bytes, so a correct
        // archive is written and then corrupted — in both the local header and the central
        // directory, since a reader may check either.
        corruptChecksum(archive, signature = byteArrayOf(0x50, 0x4b, 0x03, 0x04), crcOffset = 14)
        corruptChecksum(archive, signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02), crcOffset = 16)

        return File(temp, "service.ewsx").apply { writeBytes(archive) }
    }

    private fun corruptChecksum(archive: ByteArray, signature: ByteArray, crcOffset: Int) {
        val at = (0..archive.size - signature.size).first { start ->
            signature.indices.all { archive[start + it] == signature[it] }
        }
        repeat(Int.SIZE_BYTES) { archive[at + crcOffset + it] = (archive[at + crcOffset + it] + 1).toByte() }
    }

    @Test
    fun `an ewsx schedule is read despite the wrong checksum on its database`() {
        val songs = EasyWorshipConverter.parse(serviceFile())

        val song = songs.single()
        assertEquals("Be Thou My Vision", song.title)
        assertEquals("Dallan Forgaill", song.author)
        assertEquals("30639", song.ccli)
        assertEquals(2, song.sections.size)
        assertEquals(listOf("Be Thou my vision", "O Lord of my heart"), song.sections.first().lines)
        assertEquals(listOf("Naught be all else to me"), song.sections[1].lines)
    }

    // --- .ews ---

    @Test
    fun `a version 5 schedule reads every song in it`() {
        val songs = EasyWorshipSchedule.parse(sample("schedule-v5.ews"))

        assertEquals(listOf("Leeg", "Psalm 001"), songs.map { it.title })
        assertEquals(
            listOf("Gezegend hij, die in der bozen raad", "niet wandelt, noch met goddelozen gaat,"),
            songs[1].sections.first().lines.take(2),
        )
    }

    @Test
    fun `a slide continuing the section above it keeps that section's name`() {
        // Psalm 001 names three verses and spreads each over two slides; the second slide of each
        // carries no name of its own and is part of the verse before it, not a fourth verse.
        val song = EasyWorshipSchedule.parse(sample("schedule-v5.ews"))[1]

        assertEquals(
            listOf("Verse 1", "Verse 1", "Verse 2", "Verse 2", "Verse 3", "Verse 3"),
            song.sections.map { it.label },
        )
    }

    @Test
    fun `a schedule whose sections are all unnamed numbers them instead`() {
        val song = EasyWorshipSchedule.parse(sample("special-chars.ews")).single()
        assertEquals(listOf("Verse 1", "Verse 2"), song.sections.map { it.label })
    }

    @Test
    fun `a file too small to hold a schedule is rejected with a reason`() {
        val truncated = File(temp, "truncated.ews").apply { writeBytes(ByteArray(64)) }
        assertFailsWith<IllegalArgumentException> { EasyWorshipSchedule.parse(truncated) }
    }

    @Test
    fun `an unknown schedule version is rejected rather than read as gibberish`() {
        val wrongVersion = File(temp, "future.ews").apply {
            writeBytes("EasyWorship Schedule File Version    9".toByteArray().copyOf(1024))
        }
        assertFailsWith<IllegalArgumentException> { EasyWorshipSchedule.parse(wrongVersion) }
    }

    @Test
    fun `converting a schedule reports the failure instead of throwing at the UI`() {
        val broken = File(temp, "broken.ews").apply { writeBytes(ByteArray(1024)) }
        val result = EasyWorshipConverter.convert(broken, File(temp, "out"))

        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }
}
