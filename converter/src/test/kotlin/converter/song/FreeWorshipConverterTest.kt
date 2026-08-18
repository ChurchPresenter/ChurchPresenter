package converter.song

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Free Worship's OpenLyrics export.
 *
 * Two things make it awkward and both are pinned here. It writes **UTF-16 with a byte-order mark**,
 * so reading it as UTF-8 yields NUL-separated gibberish that no XML parser accepts. And it puts
 * **one displayed line in each `<verse>`**, distinguishing them by a trailing lowercase letter —
 * `Ca`/`Cb`/`Cc` are the three lines of one chorus, not three choruses — so the sub-slides have to
 * be folded back into a section per base name before anything downstream sees them.
 */
class FreeWorshipConverterTest {

    private val temp: File = Files.createTempDirectory("converter-freeworship-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun write(name: String, xml: String, charset: Charset, bom: ByteArray): File =
        File(temp, name).apply { writeBytes(bom + xml.toByteArray(charset)) }

    private fun utf16(name: String, xml: String): File =
        write(name, xml, Charset.forName("UTF-16LE"), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))

    private fun utf8(name: String, xml: String): File =
        write(name, xml, Charsets.UTF_8, byteArrayOf())

    private fun song(properties: String, verses: String) = """
        <?xml version="1.0" encoding="utf-16"?>
        <song version="0.8" createdIn="FreeWorship 3.2411.290.0" xmlns="http://openlyrics.info/namespace/2009/song">
          <properties>$properties</properties>
          <lyrics>$verses</lyrics>
        </song>
    """.trimIndent()

    private fun verse(name: String, line: String) = """<verse name="$name" lang=""><lines>$line</lines></verse>"""

    private fun sectionsOf(text: String): List<String> =
        Regex("""^\[(.+)]$""", RegexOption.MULTILINE).findAll(text)
            .map { it.groupValues[1] }
            .filter { it != "Primary" }
            .toList()

    @Test
    fun `reads a UTF-16 export`() {
        val file = utf16(
            "song.xml",
            song("<titles><title>YOUR LOVE NEVER FAILS</title></titles>", verse("C", "YOUR LOVE NEVER FAILS"))
        )
        assertEquals("YOUR LOVE NEVER FAILS", FreeWorshipConverter.parse(file).title)
    }

    @Test
    fun `reads a plain UTF-8 export too`() {
        val file = utf8("song.xml", song("<titles><title>Plain</title></titles>", verse("C", "line")))
        assertEquals("Plain", FreeWorshipConverter.parse(file).title)
    }

    @Test
    fun `folds per-line sub-slides back into one section each`() {
        val file = utf16(
            "song.xml",
            song(
                "<titles><title>T</title></titles><verseOrder>C V1</verseOrder>",
                verse("Ca", "chorus one") + verse("Cb", "chorus two") +
                    verse("V1a", "verse one") + verse("V1b", "verse two") + verse("V1c", "verse three")
            )
        )

        val parsed = FreeWorshipConverter.parse(file)

        assertEquals(listOf("Chorus", "Verse 1"), parsed.sections.map { it.label })
        assertEquals("chorus one\nchorus two", parsed.sections[0].text)
        assertEquals("verse one\nverse two\nverse three", parsed.sections[1].text)
    }

    @Test
    fun `orders sections by verseOrder, then anything it left out`() {
        val file = utf16(
            "song.xml",
            song(
                "<titles><title>T</title></titles><verseOrder>B C</verseOrder>",
                verse("Ca", "c") + verse("Ba", "b") + verse("V1a", "v")
            )
        )

        assertEquals(listOf("Bridge", "Chorus", "Verse 1"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `repeats named by verseOrder are written once, not once per mention`() {
        val file = utf16(
            "song.xml",
            song(
                "<titles><title>T</title></titles><verseOrder>C V1 C V2 C</verseOrder>",
                verse("Ca", "c") + verse("V1a", "one") + verse("V2a", "two")
            )
        )

        assertEquals(listOf("Chorus", "Verse 1", "Verse 2"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a lowercase standard OpenLyrics name is not mistaken for a sub-slide`() {
        // "v1" ends in a digit and "c" is a whole name, so neither may lose a trailing letter the
        // way FreeWorship's "V1a" does. Were the rule looser, "pre" would collapse into "pr".
        val file = utf16(
            "song.xml",
            song("<titles><title>T</title></titles>", verse("v1", "a") + verse("c", "b") + verse("b", "c"))
        )

        assertEquals(listOf("Verse 1", "Chorus", "Bridge"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `br elements split a verse into lines`() {
        val file = utf16(
            "song.xml",
            song("<titles><title>T</title></titles>", """<verse name="v1"><lines>one<br/>two</lines></verse>""")
        )

        assertEquals("one\ntwo", FreeWorshipConverter.parse(file).sections.single().text)
    }

    @Test
    fun `blank verses are dropped rather than becoming empty sections`() {
        val file = utf16(
            "song.xml",
            song("<titles><title>T</title></titles>", verse("v1", "kept") + verse("v2", "   "))
        )

        assertEquals(listOf("Verse 1"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `author and copyright reach the frontmatter, and are omitted when absent`() {
        val withCredits = utf16(
            "credited.xml",
            song(
                "<titles><title>T</title></titles><authors><author>A Writer</author></authors>" +
                    "<copyright>2026 Someone</copyright>",
                verse("v1", "x")
            )
        )
        val text = FreeWorshipConverter.buildSongContent(FreeWorshipConverter.parse(withCredits))
        assertTrue(text.contains("author: A Writer"), text)
        assertTrue(text.contains("copyright: 2026 Someone"), text)

        val bare = utf16("bare.xml", song("<titles><title>T</title></titles>", verse("v1", "x")))
        val bareText = FreeWorshipConverter.buildSongContent(FreeWorshipConverter.parse(bare))
        assertTrue(!bareText.contains("author:"), bareText)
        assertTrue(!bareText.contains("copyright:"), bareText)
    }

    @Test
    fun `an empty author element does not produce a blank author line`() {
        // The real exports carry <author></author> when the field was never filled in.
        val file = utf16(
            "song.xml",
            song("<titles><title>T</title></titles><authors><author></author></authors>", verse("v1", "x"))
        )

        val text = FreeWorshipConverter.buildSongContent(FreeWorshipConverter.parse(file))
        assertTrue(!text.contains("author:"), text)
    }

    @Test
    fun `the empty parens Free Worship appends to a blank songbook are stripped from the output name`() {
        val exported = File("YOUR LOVE NEVER FAILS ().xml")
        assertEquals("YOUR LOVE NEVER FAILS.song", FreeWorshipConverter.outputNameFor(exported))
        assertEquals("Plain Name.song", FreeWorshipConverter.outputNameFor(File("Plain Name.xml")))
    }

    @Test
    fun `a non-OpenLyrics XML file is rejected by name rather than parsed into nonsense`() {
        val bible = utf8("bible.xml", "<?xml version=\"1.0\"?><XMLBIBLE><BIBLEBOOK/></XMLBIBLE>")

        val error = assertFailsWith<IllegalArgumentException> { FreeWorshipConverter.parse(bible) }
        assertTrue(error.message.orEmpty().contains("XMLBIBLE"), error.message.orEmpty())
    }

    @Test
    fun `converting writes the sections under the song title`() {
        val file = utf16(
            "song.xml",
            song(
                "<titles><title>Amazing</title></titles><verseOrder>C V1</verseOrder>",
                verse("Ca", "how sweet") + verse("V1a", "the sound")
            )
        )
        val out = File(temp, "out.song")

        FreeWorshipConverter.convert(file, out)

        val text = out.readText()
        assertTrue(text.contains("title: Amazing"), text)
        assertEquals(listOf("Chorus", "Verse 1"), sectionsOf(text))
    }

    @Test
    fun `verse names map onto the app's section labels`() {
        assertEquals("Verse 2", FreeWorshipConverter.sectionLabel("V2"))
        assertEquals("Chorus", FreeWorshipConverter.sectionLabel("C"))
        assertEquals("Bridge", FreeWorshipConverter.sectionLabel("b"))
        assertEquals("Pre-Chorus", FreeWorshipConverter.sectionLabel("P"))
        assertEquals("Ending", FreeWorshipConverter.sectionLabel("e"))
        assertEquals("Intro", FreeWorshipConverter.sectionLabel("I"))
        assertEquals("Outro", FreeWorshipConverter.sectionLabel("O"))
        assertEquals("Tag", FreeWorshipConverter.sectionLabel("T"))
    }

    @Test
    fun `an unrecognised verse name is kept as written instead of being dropped`() {
        assertEquals("Refrain", FreeWorshipConverter.sectionLabel("Refrain"))
        assertEquals("misc-7", FreeWorshipConverter.sectionLabel("misc-7"))
    }
}
