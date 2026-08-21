package org.churchpresenter.converter.song

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The OpenLyrics documents Free Worship, OpenLP and Quelea all export, in the shapes that differ
 * between them: which encoding the file is in, which properties it bothers to write, and how a
 * verse too long for one slide is split.
 *
 * `verseOrder` is the part worth guarding. It names sections that may not exist, and it may leave
 * out sections that do — presenting the song in the wrong order, or dropping a verse entirely, are
 * both silent failures the person only sees on the screen during the service.
 */
class FreeWorshipEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("freeworship-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun song(properties: String, verses: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <song version="0.8" xmlns="http://openlyrics.info/namespace/2009/song">
          <properties>$properties</properties>
          <lyrics>$verses</lyrics>
        </song>
    """.trimIndent()

    private fun utf8(name: String, xml: String): File =
        File(temp, name).apply { writeText(xml, Charsets.UTF_8) }

    private fun withBom(name: String, xml: String, charset: Charset, bom: ByteArray): File =
        File(temp, name).apply { writeBytes(bom + xml.toByteArray(charset)) }

    private fun verse(name: String, line: String) = """<verse name="$name"><lines>$line</lines></verse>"""

    // ── Encodings ─────────────────────────────────────────────────────────────

    @Test
    fun `a big-endian UTF-16 export is read`() {
        val file = withBom(
            "be.xml",
            song("<titles><title>Слава</title></titles>", verse("v1", "Слава Богу")),
            Charset.forName("UTF-16BE"),
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
        )
        assertEquals("Слава", FreeWorshipConverter.parse(file).title)
    }

    @Test
    fun `a UTF-8 export with a byte order mark is read`() {
        val file = withBom(
            "bom.xml",
            song("<titles><title>Grace</title></titles>", verse("v1", "line")),
            Charsets.UTF_8,
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
        )
        assertEquals("Grace", FreeWorshipConverter.parse(file).title)
    }

    // ── What the file is ──────────────────────────────────────────────────────

    @Test
    fun `an XML file that is not a song is refused by its root element`() {
        val file = utf8("notasong.xml", """<?xml version="1.0"?><playlist><item>x</item></playlist>""")
        val error = assertFailsWith<IllegalArgumentException> { FreeWorshipConverter.parse(file) }
        assertTrue(error.message!!.contains("playlist"), "got '${error.message}'")
    }

    @Test
    fun `a song with no properties block at all still reads its lyrics`() {
        val file = utf8(
            "bare.xml",
            """<?xml version="1.0"?><song><lyrics>${verse("v1", "Amazing grace")}</lyrics></song>""",
        )
        val parsed = FreeWorshipConverter.parse(file)
        assertEquals("", parsed.title)
        assertEquals("", parsed.author)
        assertEquals("", parsed.copyright)
        assertTrue(parsed.verseOrder.isEmpty())
        assertEquals(listOf("Verse 1"), parsed.sections.map { it.label })
    }

    @Test
    fun `a song with no lyrics element has no sections`() {
        val file = utf8("nolyrics.xml", """<?xml version="1.0"?><song><properties/></song>""")
        assertTrue(FreeWorshipConverter.parse(file).sections.isEmpty())
    }

    // ── Properties ────────────────────────────────────────────────────────────

    @Test
    fun `the first title with anything in it is the one used`() {
        val file = utf8(
            "titles.xml",
            song(
                "<titles><title>   </title><title>Amazing Grace</title><title>Ignored</title></titles>",
                verse("v1", "line"),
            ),
        )
        assertEquals("Amazing Grace", FreeWorshipConverter.parse(file).title)
    }

    @Test
    fun `several authors are joined into one line`() {
        val file = utf8(
            "authors.xml",
            song(
                "<authors><author>John Newton</author><author>  </author><author>Chris Tomlin</author></authors>",
                verse("v1", "line"),
            ),
        )
        assertEquals("John Newton, Chris Tomlin", FreeWorshipConverter.parse(file).author)
    }

    @Test
    fun `a namespaced element is matched on its local name`() {
        val file = utf8(
            "ns.xml",
            """<?xml version="1.0"?><song xmlns:ol="http://openlyrics.info/namespace/2009/song">
               <ol:properties><ol:titles><ol:title>Namespaced</ol:title></ol:titles></ol:properties>
               <ol:lyrics><ol:verse name="v1"><ol:lines>A line</ol:lines></ol:verse></ol:lyrics>
               </song>""",
        )
        val parsed = FreeWorshipConverter.parse(file)
        assertEquals("Namespaced", parsed.title)
        assertEquals(listOf("A line"), parsed.sections.single().text.lines())
    }

    // ── Verse order ───────────────────────────────────────────────────────────

    @Test
    fun `the stated order decides the order the sections come out in`() {
        val file = utf8(
            "order.xml",
            song(
                "<verseOrder>c v1</verseOrder>",
                verse("v1", "verse line") + verse("c", "chorus line"),
            ),
        )
        assertEquals(listOf("Chorus", "Verse 1"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a section the order forgets is still written, after the ones it names`() {
        val file = utf8(
            "forgotten.xml",
            song(
                "<verseOrder>c</verseOrder>",
                verse("v1", "verse line") + verse("c", "chorus line"),
            ),
        )
        assertEquals(listOf("Chorus", "Verse 1"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `an order naming a section that does not exist does not invent one`() {
        val file = utf8(
            "ghost.xml",
            song("<verseOrder>v1 b2 c</verseOrder>", verse("v1", "verse line") + verse("c", "chorus line")),
        )
        val parsed = FreeWorshipConverter.parse(file)
        assertEquals(listOf("Verse 1", "Chorus"), parsed.sections.map { it.label })
        assertEquals(listOf("v1", "b2", "c"), parsed.verseOrder)
    }

    // ── Verses ────────────────────────────────────────────────────────────────

    @Test
    fun `a verse split across slides is one section again`() {
        // Free Worship writes the overflow of a long verse as `v1b`, and presenting it as its own
        // section would show the second half of verse one as if it were a different verse.
        val file = utf8(
            "split.xml",
            song("", verse("V1a", "first half") + verse("V1b", "second half")),
        )
        val section = FreeWorshipConverter.parse(file).sections.single()
        assertEquals("Verse 1", section.label)
        assertEquals("first half\nsecond half", section.text)
    }

    @Test
    fun `an empty verse is left out rather than written blank`() {
        val file = utf8(
            "empty.xml",
            song("", verse("v1", "   ") + verse("c", "chorus line")),
        )
        assertEquals(listOf("Chorus"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a break element ends the line and a comment is not sung`() {
        val file = utf8(
            "markup.xml",
            song(
                "",
                """<verse name="v1"><lines>first<br/>second<comment>not sung</comment></lines></verse>""",
            ),
        )
        assertEquals("first\nsecond", FreeWorshipConverter.parse(file).sections.single().text)
    }

    @Test
    fun `chords are unwrapped rather than dropped with the words inside them`() {
        val file = utf8(
            "chords.xml",
            song("", """<verse name="v1"><lines>A<chord name="G">mazing</chord> grace</lines></verse>"""),
        )
        assertEquals("Amazing grace", FreeWorshipConverter.parse(file).sections.single().text)
    }

    @Test
    fun `a CDATA verse body is read as its text`() {
        val file = utf8(
            "cdata.xml",
            song("", """<verse name="v1"><lines><![CDATA[Amazing grace]]></lines></verse>"""),
        )
        assertEquals("Amazing grace", FreeWorshipConverter.parse(file).sections.single().text)
    }

    @Test
    fun `several lines elements in one verse are kept apart`() {
        val file = utf8(
            "twolines.xml",
            song("", """<verse name="v1"><lines>first</lines><lines>second</lines></verse>"""),
        )
        assertEquals("first\nsecond", FreeWorshipConverter.parse(file).sections.single().text)
    }

    // ── Section names ─────────────────────────────────────────────────────────

    @Test
    fun `every OpenLyrics section letter is spelled out`() {
        val names = listOf("v1", "c", "b", "p", "e", "i", "o", "t")
        val file = utf8("letters.xml", song("", names.joinToString("") { verse(it, "line for $it") }))

        assertEquals(
            listOf("Verse 1", "Chorus", "Bridge", "Pre-Chorus", "Ending", "Intro", "Outro", "Tag"),
            FreeWorshipConverter.parse(file).sections.map { it.label },
        )
    }

    @Test
    fun `a section name the mapping does not know is kept as it stands`() {
        val file = utf8("unknown.xml", song("", verse("antiphon", "line") + verse("x9", "line")))
        assertEquals(listOf("antiphon", "x9"), FreeWorshipConverter.parse(file).sections.map { it.label })
    }

    // ── What is written out ───────────────────────────────────────────────────

    @Test
    fun `an empty pair of brackets in the file name does not reach the song name`() {
        // Free Worship names its exports "Title ()" when the song has no author.
        assertEquals("Amazing Grace.song", FreeWorshipConverter.outputNameFor(File(temp, "Amazing Grace ().xml")))
        assertEquals("Amazing Grace.song", FreeWorshipConverter.outputNameFor(File(temp, "Amazing Grace.xml")))
    }

    @Test
    fun `frontmatter is written only for the metadata the song actually has`() {
        val bare = utf8("bare-meta.xml", song("<titles><title>Bare</title></titles>", verse("v1", "line")))
        val content = FreeWorshipConverter.buildSongContent(FreeWorshipConverter.parse(bare))
        assertFalse(content.contains("author:"), content)
        assertFalse(content.contains("copyright:"), content)
        assertTrue(content.contains("title: Bare"))

        val full = utf8(
            "full-meta.xml",
            song(
                "<titles><title>Full</title></titles><authors><author>John Newton</author></authors>" +
                    "<copyright>Public Domain</copyright>",
                verse("v1", "line"),
            ),
        )
        val fullContent = FreeWorshipConverter.buildSongContent(FreeWorshipConverter.parse(full))
        assertTrue(fullContent.contains("author: John Newton"))
        assertTrue(fullContent.contains("copyright: Public Domain"))
    }
}
