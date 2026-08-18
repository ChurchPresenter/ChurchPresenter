package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two legacy song-library importers: SoftProjector's `.sps` (text and SQLite flavours) and the
 * `.sng` format.
 *
 * These are how an existing library gets into the app, so the cases that matter are structural:
 * the delimiter-separated columns landing in the right fields, section headers surviving, and a
 * malformed row being skipped rather than taking the whole import down with it.
 */
class SongFormatConvertersTest {

    private val temp: File = Files.createTempDirectory("converter-song-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── .sps, text flavour ────────────────────────────────────────────────────

    /** `number#$#title#$#?#$#tune#$#author#$#composer#$#lyrics`, after two `##` header lines. */
    private fun spsFile(name: String, songbook: String, vararg rows: String): File {
        val file = File(temp, "$name.sps")
        file.writeText(
            buildString {
                appendLine("##SoftProjector")
                appendLine("##$songbook")
                rows.forEach { appendLine(it) }
            },
            Charsets.UTF_8,
        )
        return file
    }

    @Test
    fun `the songbook name comes from the second header line`() {
        val file = spsFile("book", "Hymns of Grace", "1#\$#A Song#\$#x#\$#tune#\$#author#\$#composer#\$#Line one")
        assertEquals("Hymns of Grace", SpsToSongConverter.parse(file).songbookName)
    }

    @Test
    fun `each delimited column lands in its own field`() {
        val file = spsFile(
            "book", "Book",
            "12#\$#Amazing Grace#\$#cat#\$#New Britain#\$#John Newton#\$#Traditional#\$#Amazing grace",
        )
        val song = SpsToSongConverter.parse(file).songs.single()
        assertEquals("12", song.number)
        assertEquals("Amazing Grace", song.title)
        assertEquals("New Britain", song.tune)
        assertEquals("John Newton", song.author)
        assertEquals("Traditional", song.composer)
        assertEquals("Book", song.songbook, "every song carries its songbook")
        assertTrue(song.lyrics.isNotEmpty())
    }

    @Test
    fun `a row with too few columns is skipped rather than aborting the import`() {
        val file = spsFile(
            "book", "Book",
            "1#\$#Good#\$#c#\$#t#\$#a#\$#comp#\$#Lyrics",
            "2#\$#Truncated",
            "3#\$#Also good#\$#c#\$#t#\$#a#\$#comp#\$#Lyrics",
        )
        assertEquals(listOf("1", "3"), SpsToSongConverter.parse(file).songs.map { it.number })
    }

    @Test
    fun `blank lines between rows are ignored`() {
        val file = File(temp, "spaced.sps")
        file.writeText(
            "##SoftProjector\n##Book\n\n1#\$#A#\$#c#\$#t#\$#a#\$#m#\$#Lyric\n\n\n2#\$#B#\$#c#\$#t#\$#a#\$#m#\$#Lyric\n",
            Charsets.UTF_8,
        )
        assertEquals(2, SpsToSongConverter.parse(file).songs.size)
    }

    @Test
    fun `a file with no songbook header falls back to its own name`() {
        val file = File(temp, "Fallback Name.sps")
        file.writeText("1#\$#A#\$#c#\$#t#\$#a#\$#m#\$#Lyric\n", Charsets.UTF_8)
        assertEquals("Fallback Name", SpsToSongConverter.parse(file).songbookName)
    }

    @Test
    fun `converting writes one numbered file per song into a songbook folder`() {
        val file = spsFile(
            "book", "Hymns",
            "7#\$#Seventh#\$#c#\$#t#\$#a#\$#m#\$#Lyric",
            "12#\$#Twelfth#\$#c#\$#t#\$#a#\$#m#\$#Lyric",
        )
        val result = SpsToSongConverter.convert(file, temp)

        assertEquals(2, result.songsConverted)
        assertTrue(result.errors.isEmpty(), "no errors: ${result.errors}")
        val written = File(result.songbookFolder).listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("0007 - Seventh.song", "0012 - Twelfth.song"), written)
        // Zero-padding is what makes a plain alphabetical listing match the hymn numbering.
        assertTrue(written.first() < written.last())
    }

    @Test
    fun `converting a file with no songs reports why rather than making an empty folder`() {
        val file = File(temp, "empty.sps")
        file.writeText("##SoftProjector\n##Book\n", Charsets.UTF_8)
        val result = SpsToSongConverter.convert(file, temp)
        assertEquals(0, result.songsConverted)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `the target folder name can be read without converting`() {
        // The UI shows the user where the import will land before they commit to it.
        val file = spsFile("book", "Hymns of Grace", "1#\$#A#\$#c#\$#t#\$#a#\$#m#\$#L")
        assertEquals("Hymns of Grace", SpsToSongConverter.getTargetFolderName(file))
    }

    @Test
    fun `an unreadable file falls back to its own name rather than throwing`() {
        val file = File(temp, "Broken Book.sps")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01))
        assertEquals("Broken Book", SpsToSongConverter.getTargetFolderName(file))
    }

    // ── .sng ──────────────────────────────────────────────────────────────────

    private fun sngFile(name: String, body: String): File =
        File(temp, "$name.sng").apply { writeText(body, Charsets.UTF_8) }

    @Test
    fun `sng headers are read into their fields`() {
        val file = sngFile(
            "song",
            """
            #Title=Amazing Grace
            #Author=John Newton
            #(c)=Public Domain
            #VerseOrder=Verse 1,Chorus
            ---
            Verse 1
            Amazing grace how sweet the sound
            """.trimIndent(),
        )
        val song = SngToSongConverter.parse(file)
        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
        assertEquals(listOf("Verse 1", "Chorus"), song.verseOrder)
    }

    @Test
    fun `sng sections are split on their separators`() {
        val file = sngFile(
            "song",
            """
            #Title=T
            ---
            Verse 1
            First verse line
            ---
            Chorus
            Chorus line
            """.trimIndent(),
        )
        val song = SngToSongConverter.parse(file)
        assertEquals(2, song.sections.size, "each separator starts a section")
        assertTrue(song.sections.any { it.text.contains("First verse line") })
        assertTrue(song.sections.any { it.text.contains("Chorus line") })
    }

    @Test
    fun `an sng file with no verse order yields an empty list rather than a blank entry`() {
        val file = sngFile("song", "#Title=T\n---\nVerse 1\nLine\n")
        assertTrue(SngToSongConverter.parse(file).verseOrder.isEmpty())
    }

    @Test
    fun `missing sng headers default to empty strings`() {
        val file = sngFile("song", "---\nVerse 1\nLine\n")
        val song = SngToSongConverter.parse(file)
        assertEquals("", song.title)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
    }

    @Test
    fun `converting an sng writes a song file carrying the lyrics`() {
        val input = sngFile("in", "#Title=Amazing Grace\n---\nVerse 1\nAmazing grace how sweet\n")
        val output = File(temp, "out.song")
        SngToSongConverter.convert(input, output)

        assertTrue(output.exists())
        val text = output.readText()
        assertTrue(text.contains("Amazing Grace"), "the title is written")
        assertTrue(text.contains("Amazing grace how sweet"), "the lyrics are written")
    }

    @Test
    fun `a batch conversion writes one output per input and reports the pairs`() {
        val a = sngFile("first", "#Title=First\n---\nVerse 1\nA line\n")
        val b = sngFile("second", "#Title=Second\n---\nVerse 1\nB line\n")
        val out = File(temp, "out").apply { mkdirs() }

        val pairs = SngToSongConverter.convertBatch(listOf(a, b), out)
        assertEquals(2, pairs.size)
        assertTrue(pairs.all { it.second.exists() }, "every reported output really exists")
    }
}
