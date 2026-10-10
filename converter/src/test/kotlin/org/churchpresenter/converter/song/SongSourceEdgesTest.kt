package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongSourceEdgesTest {

    private val temp: File = Files.createTempDirectory("converter-source-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun file(name: String, body: String): File =
        File(temp, name).apply { parentFile.mkdirs(); writeText(body, Charsets.UTF_8) }

    private val empty = SongPreviewInfo("", sectionCount = 0, songCount = 0, verseOrder = emptyList())

    @Test
    fun `an EasySlides export with no items previews as empty`() {
        assertEquals(empty, EasySlidesFormat.describe(file("empty.xml", "<EasiSlides></EasiSlides>")))
    }

    @Test
    fun `a Quelea pack with no songs in it previews as empty`() {
        val pack = File(temp, "empty.qsp").apply {
            ZipOutputStream(outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("folder/"))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("not a song".toByteArray())
                zip.closeEntry()
            }
        }
        assertEquals(empty, QueleaFormat.describe(pack))
    }

    @Test
    fun `a VideoPsalm file with no book in it previews under its file name`() {
        val info = VideoPsalmFormat.describe(file("notes.json", "not a song book"))
        assertEquals(empty.copy(title = "notes"), info)
    }

    @Test
    fun `a Quelea song without lyrics has no sections`() {
        assertEquals(emptyList(), QueleaConverter.sectionsOf(null))
    }

    @Test
    fun `a Quelea section without its own lyrics element is read from its text`() {
        val lyrics = parseXmlRoot(
            "<lyrics><section title=\"Verse 1\">Amazing grace<br/>How sweet</section>" +
                "<section title=\"Verse 2\"><lyrics>  </lyrics></section></lyrics>"
        )
        assertEquals(
            listOf(SongSection("Verse 1", listOf("Amazing grace", "How sweet"))),
            QueleaConverter.sectionsOf(lyrics),
        )
    }

    @Test
    fun `only the file has no zip mark when it is too short to carry one`() {
        assertFalse(QueleaConverter.isZip(file("tiny.xml", "<")))
        assertFalse(QueleaConverter.isZip(file("text.xml", "<song/>")))
        assertFalse(QueleaConverter.isZip(file("half.xml", "PX")))
    }

    @Test
    fun `an OpenSong song with no extension matches, and a wrong extension matches nothing`() {
        assertTrue(matchesFormat(File(temp, "Amazing Grace"), OpenSongFormat))
        assertTrue(matchesFormat(File(temp, "grace.XML"), OpenSongFormat))
        assertFalse(matchesFormat(File(temp, "grace.txt"), OpenSongFormat))
        assertFalse(matchesFormat(File(temp, "Amazing Grace"), QueleaFormat))
    }
}
