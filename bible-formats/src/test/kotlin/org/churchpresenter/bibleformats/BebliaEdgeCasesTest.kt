package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Holy Bible XML files as the archive actually ships them, rather than as the format describes them.
 *
 * The root's attributes are spelled three different ways depending on who contributed the file, the
 * book numbers are bare integers that sometimes fall outside the canon, and several files carry a
 * byte-order mark. None of it may stop the parse: the alternative is a translation the user
 * downloaded and cannot install.
 */
class BebliaEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("converter-beblia-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun bible(name: String, rootAttributes: String, body: String, bom: Boolean = false): File =
        File(temp, name).apply {
            val xml = """<?xml version="1.0" encoding="utf-8"?><bible $rootAttributes>$body</bible>"""
            val prefix = if (bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) else byteArrayOf()
            writeBytes(prefix + xml.toByteArray(Charsets.UTF_8))
        }

    private val genesis =
        """<testament name="Old"><book number="1"><chapter number="1">
           <verse number="1">In the beginning</verse></chapter></book></testament>"""

    // ── Recognising the dialect ───────────────────────────────────────────────

    @Test
    fun `a root with none of the title attributes is not this dialect`() {
        assertFalse(BebliaParser.looksLikeBeblia(bible("untitled.xml", """status="Public Domain"""", genesis)))
    }

    @Test
    fun `a Zefania module is not mistaken for this dialect`() {
        val zefania = File(temp, "zefania.xml").apply {
            writeText("""<?xml version="1.0"?><XMLBIBLE biblename="Test"/>""", Charsets.UTF_8)
        }
        assertFalse(BebliaParser.looksLikeBeblia(zefania))
    }

    @Test
    fun `a file that is not XML at all is not this dialect`() {
        val junk = File(temp, "junk.xml").apply { writeText("not xml <<<", Charsets.UTF_8) }
        assertFalse(BebliaParser.looksLikeBeblia(junk))
    }

    @Test
    fun `a file carrying a byte order mark is still read`() {
        val file = bible("bom.xml", """translation="English KJV"""", genesis, bom = true)
        assertTrue(BebliaParser.looksLikeBeblia(file))
        assertEquals("English KJV", XmlToSpbConverter.parse(file).name)
    }

    // ── Root metadata, three spellings each ───────────────────────────────────

    @Test
    fun `the title is read from whichever attribute the file uses`() {
        assertEquals("First", XmlToSpbConverter.parse(bible("a.xml", """translation="First"""", genesis)).name)
        assertEquals("Second", XmlToSpbConverter.parse(bible("b.xml", """name="Second"""", genesis)).name)
        assertEquals("Third", XmlToSpbConverter.parse(bible("c.xml", """language="Third"""", genesis)).name)
    }

    @Test
    fun `the copyright and the source are read the same way`() {
        val first = XmlToSpbConverter.parse(
            bible("rights1.xml", """translation="T" status="Public Domain" link="example.org"""", genesis)
        )
        assertEquals("Public Domain", first.rights)
        assertEquals("example.org", first.source)

        val second = XmlToSpbConverter.parse(
            bible("rights2.xml", """translation="T" info="CC-BY" site="example.net"""", genesis)
        )
        assertEquals("CC-BY", second.rights)
        assertEquals("example.net", second.source)
    }

    @Test
    fun `a blank attribute is passed over for the next spelling`() {
        val parsed = XmlToSpbConverter.parse(
            bible("blank.xml", """translation="  " name="Real Name" status="  " info="CC-BY"""", genesis)
        )
        assertEquals("Real Name", parsed.name)
        assertEquals("CC-BY", parsed.rights)
    }

    @Test
    fun `a file with a root but no title anywhere is labelled Unknown`() {
        val parsed = XmlToSpbConverter.parseBeblia(bible("noname.xml", """status="Public Domain"""", genesis))
        assertEquals("Unknown", parsed.name)
    }

    // ── What the caller knows wins ────────────────────────────────────────────

    @Test
    fun `the catalogue's own metadata overrides what the file says`() {
        val parsed = XmlToSpbConverter.parseBeblia(
            bible("override.xml", """translation="File Name" status="File Rights" link="file.example"""", genesis),
            language = "rus",
            name = "Catalogue Name",
            rights = "Catalogue Rights",
            source = "catalogue.example",
            identifier = "CAT",
        )
        assertEquals("Catalogue Name", parsed.name)
        assertEquals("Catalogue Rights", parsed.rights)
        assertEquals("catalogue.example", parsed.source)
        assertEquals("CAT", parsed.identifier)
        assertEquals("RUS", parsed.language)
        assertEquals("Бытие", parsed.books.single().name)
    }

    @Test
    fun `a blank language from the caller falls back to the title`() {
        val parsed = XmlToSpbConverter.parseBeblia(
            bible("bytitle.xml", """translation="Russian Synodal"""", genesis),
            language = "   ",
        )
        assertEquals("RUS", parsed.language)
    }

    @Test
    fun `a title naming no language leaves the books in English`() {
        val parsed = XmlToSpbConverter.parseBeblia(bible("unknownlang.xml", """translation="Some Version"""", genesis))
        assertEquals(null, parsed.language)
        assertEquals("Genesis", parsed.books.single().name)
    }

    // ── Books, chapters and verses ────────────────────────────────────────────

    @Test
    fun `a book outside the canon is dropped rather than failing the file`() {
        val body = """<testament name="Old">
            <book number="1"><chapter number="1"><verse number="1">kept</verse></chapter></book>
            <book number="0"><chapter number="1"><verse number="1">dropped</verse></chapter></book>
            <book number="67"><chapter number="1"><verse number="1">dropped</verse></chapter></book>
            </testament>"""
        val parsed = XmlToSpbConverter.parse(bible("canon.xml", """translation="T"""", body))
        assertEquals(listOf(1), parsed.books.map { it.number })
    }

    @Test
    fun `a book beyond the canon still gets a name when the language has no table`() {
        val body = """<book number="66"><chapter number="1"><verse number="1">text</verse></chapter></book>"""
        val parsed = XmlToSpbConverter.parseBeblia(bible("last.xml", """translation="Some Version"""", body))
        assertEquals("Revelation", parsed.books.single().name)
    }

    @Test
    fun `numbers that are not numbers fall back to zero rather than failing`() {
        val body = """<book number="1"><chapter number="x"><verse number="y">text</verse></chapter></book>"""
        val parsed = XmlToSpbConverter.parse(bible("nan.xml", """translation="T"""", body))
        val chapter = parsed.books.single().chapters.single()
        assertEquals(0, chapter.number)
        assertEquals(0, chapter.verses.single().number)
    }

    @Test
    fun `a CDATA verse body is read as its text`() {
        val body = """<book number="1"><chapter number="1">
            <verse number="1"><![CDATA[In the beginning]]></verse></chapter></book>"""
        val parsed = XmlToSpbConverter.parse(bible("cdata.xml", """translation="T"""", body))
        assertEquals("In the beginning", parsed.books.single().chapters.single().verses.single().text)
    }

    @Test
    fun `progress is reported and always ends at one`() {
        val reported = mutableListOf<Float>()
        XmlToSpbConverter.parseBeblia(
            bible("progress.xml", """translation="T"""", genesis),
            onProgress = { reported.add(it) },
        )
        assertEquals(1f, reported.last())
    }

    // ── The language table behind the book names ──────────────────────────────

    @Test
    fun `a language named in the title is recognised`() {
        assertEquals("RUS", BebliaParser.languageFromTitle("Russian Synodal Translation"))
        assertEquals("UKR", BebliaParser.languageFromTitle("Ukrainian Bible"))
        assertEquals(null, BebliaParser.languageFromTitle("Some Version With No Language"))
    }
}
