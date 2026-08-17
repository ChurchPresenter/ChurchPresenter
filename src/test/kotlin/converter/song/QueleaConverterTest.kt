package converter.song

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Quelea song packs. The pack is a zip whose entries are song XML, except that Quelea names the
 * second song of a repeated title `.pdf` — so entries are parsed, not filtered by extension, and
 * whatever turns out not to be a song is reported instead of vanishing.
 */
class QueleaConverterTest {

    private val temp: File = Files.createTempDirectory("converter-quelea-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun songXml(title: String, vararg sections: Pair<String, String>): String =
        "<song><title>$title</title><author>John Newton</author><ccli>1234</ccli>" +
            "<copyright>Public Domain</copyright><year>1779</year><sequence>v1 c</sequence><lyrics>" +
            sections.joinToString("") { (name, body) ->
                "<section title=\"$name\"><lyrics>$body</lyrics></section>"
            } +
            "</lyrics></song>"

    private fun pack(name: String, entries: Map<String, String>): File =
        File(temp, name).apply {
            ZipOutputStream(outputStream()).use { zip ->
                entries.forEach { (entryName, body) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun `a pack yields every song it holds`() {
        val file = pack(
            "songs.qsp",
            mapOf(
                "1.xml" to songXml("Amazing Grace", "Verse 1" to "Amazing grace\nhow sweet"),
                "2.xml" to songXml("Be Thou My Vision", "Verse 1" to "Be thou my vision"),
            ),
        )

        assertEquals(
            listOf("Amazing Grace", "Be Thou My Vision"),
            QueleaConverter.parse(file).map { it.title },
        )
    }

    @Test
    fun `an entry Quelea misnamed pdf is still read as the song it is`() {
        val file = pack(
            "songs.qsp",
            mapOf(
                "Grace.xml" to songXml("Grace", "Verse 1" to "One"),
                "Grace.pdf" to songXml("Grace", "Verse 1" to "Two"),
            ),
        )

        val songs = QueleaConverter.parse(file)

        assertEquals(2, songs.size, songs.map { it.title }.toString())
        assertEquals(setOf("One", "Two"), songs.flatMap { it.sections.single().lines }.toSet())
    }

    @Test
    fun `an entry that is not a song is reported, not dropped silently`() {
        val file = pack(
            "songs.qsp",
            mapOf("1.xml" to songXml("Grace", "Verse 1" to "One"), "notes.txt" to "just a note"),
        )

        val result = QueleaConverter.convert(file, File(temp, "out"))

        assertEquals(listOf("Grace.song"), result.outputFiles.map { it.name })
        assertEquals(1, result.errors.size, result.errors.toString())
        assertTrue(result.errors.single().contains("notes.txt"), result.errors.toString())
    }

    @Test
    fun `section titles and their lines come through as written`() {
        val file = pack(
            "songs.qsp",
            mapOf(
                "1.xml" to songXml(
                    "Grace",
                    "Verse 1" to "Amazing grace\nhow sweet the sound",
                    "Chorus" to "Praise the Lord",
                ),
            ),
        )

        val sections = QueleaConverter.parse(file).single().sections

        assertEquals(listOf("Verse 1", "Chorus"), sections.map { it.label })
        assertEquals(listOf("Amazing grace", "how sweet the sound"), sections.first().lines)
    }

    @Test
    fun `lyrics Quelea never escaped are repaired rather than skipped`() {
        // Both of these are real: 40 of the 3,134 songs in Quelea's English pack are rejected by a
        // strict parser, for a bare & between songwriters and a literal <<>> left in the lyrics.
        val malformed = "<song><title>Crowns</title><author>M Fatkin & B Hastings</author><lyrics>" +
            "<section title=\"Verse 1\"><lyrics>Words and Music\n<<>>\nBy Hillsong</lyrics></section>" +
            "</lyrics></song>"
        val file = pack("songs.qsp", mapOf("crowns.xml" to malformed))

        val song = QueleaConverter.parse(file).single()

        assertEquals("Crowns", song.title)
        assertEquals("M Fatkin & B Hastings", song.author)
        assertEquals(listOf("Words and Music", "<<>>", "By Hillsong"), song.sections.single().lines)
    }

    @Test
    fun `a well-formed document is not touched by the repair`() {
        val xml = "<song><title>Grace &amp; Peace</title><lyrics>" +
            "<section title=\"Verse 1\"><lyrics>a &lt; b</lyrics></section></lyrics></song>"
        val file = File(temp, "ok.xml").apply { writeText(xml, Charsets.UTF_8) }

        val song = QueleaConverter.parse(file).single()

        assertEquals("Grace & Peace", song.title)
        assertEquals(listOf("a < b"), song.sections.single().lines)
    }

    @Test
    fun `a loose song file converts without being packed first`() {
        val file = File(temp, "grace.xml").apply {
            writeText(songXml("Amazing Grace", "Verse 1" to "Amazing grace"), Charsets.UTF_8)
        }

        val result = QueleaConverter.convert(file, File(temp, "out-loose"))

        val text = result.outputFiles.single().readText()
        assertTrue(text.contains("title: Amazing Grace"), text)
        assertTrue(text.contains("copyright: Public Domain 1779"), text)
    }
}
