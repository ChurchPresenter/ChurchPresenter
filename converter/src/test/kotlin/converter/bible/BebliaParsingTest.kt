package converter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the Holy Bible XML ("Beblia") dialect.
 *
 * Two things about the source data are what this suite mostly pins. The root element spells its
 * metadata three different ways depending on who contributed the file — `translation`/`name`/
 * `language` for the title, `status`/`info`/`version` for the copyright — so a reader that knows only
 * the common spelling silently produces a Bible with no books at all. And the files carry no book
 * names and no language code whatsoever, so the language has to arrive from the caller or be guessed
 * from the title, and getting it wrong mis-numbers Psalms and picks the wrong book names.
 *
 * The byte-level cases (a BOM, CRLF) are here because several files in the archive have them and both
 * are the classic way an XML reader fails on real-world input rather than on fixtures.
 */
class BebliaParsingTest {

    private val temp: File = Files.createTempDirectory("beblia-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun write(name: String, xml: String): File =
        File(temp, name).apply { writeText(xml, Charsets.UTF_8) }

    private fun genesis(root: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        $root
          <testament name="Old">
            <book number="1">
              <chapter number="1">
                <verse number="1">In the beginning</verse>
                <verse number="2">And the earth was without form</verse>
              </chapter>
            </book>
          </testament>
        </bible>
    """.trimIndent()

    // --- the three root-attribute spellings -------------------------------------------------

    @Test
    fun `the title is read from translation, name or language, whichever the file uses`() {
        val spellings = mapOf(
            "translation" to "English KJV",
            "name" to "Greek Bible - Vamvas 1770",
            "language" to "Hebrew BSI 2017",
        )
        for ((attribute, value) in spellings) {
            val file = write("$attribute.xml", genesis("""<bible $attribute="$value">"""))
            assertEquals(value, XmlToSpbConverter.parse(file).name, "title from $attribute=")
            assertEquals(1, XmlToSpbConverter.parse(file).books.size, "books still read for $attribute=")
        }
    }

    @Test
    fun `the copyright is read from status, info or version, whichever the file uses`() {
        for (attribute in listOf("status", "info", "version")) {
            val file = write("r-$attribute.xml", genesis("""<bible translation="X" $attribute="Public Domain">"""))
            assertEquals("Public Domain", XmlToSpbConverter.parse(file).rights, "rights from $attribute=")
        }
    }

    @Test
    fun `the source url is read from link or site, whichever the file uses`() {
        for (attribute in listOf("link", "site")) {
            val file = write("s-$attribute.xml", genesis("""<bible translation="X" $attribute="https://example.org">"""))
            assertEquals("https://example.org", XmlToSpbConverter.parse(file).source, "source from $attribute=")
        }
    }

    @Test
    fun `the first non-blank spelling wins when a file carries more than one`() {
        val file = write(
            "both.xml",
            genesis("""<bible translation="Preferred" name="Ignored" status="Rights" info="Ignored">""")
        )
        val parsed = XmlToSpbConverter.parse(file)
        assertEquals("Preferred", parsed.name)
        assertEquals("Rights", parsed.rights)
    }

    // --- structure ---------------------------------------------------------------------------

    @Test
    fun `books, chapters and verses are read through the testament wrapper`() {
        val parsed = XmlToSpbConverter.parse(write("ot.xml", genesis("""<bible translation="English KJV">""")))
        val book = parsed.books.single()
        assertEquals(1, book.number)
        assertEquals(1, book.chapters.single().number)
        assertContentEquals(listOf(1, 2), book.chapters.single().verses.map { it.number })
        assertEquals("In the beginning", book.chapters.single().verses.first().text)
    }

    @Test
    fun `books directly under the root are read without a testament wrapper`() {
        val file = write(
            "flat.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="English KJV"><book number="1"><chapter number="1">
            <verse number="1">In the beginning</verse></chapter></book></bible>"""
        )
        assertEquals(listOf(1), XmlToSpbConverter.parse(file).books.map { it.number })
    }

    @Test
    fun `a new testament only edition keeps its books numbered 40 to 66`() {
        val file = write(
            "nt.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Greek Stephanus NT 1550"><testament name="New">
            <book number="40"><chapter number="1"><verse number="1">Matthew</verse></chapter></book>
            <book number="66"><chapter number="1"><verse number="1">Revelation</verse></chapter></book>
            </testament></bible>"""
        )
        val parsed = XmlToSpbConverter.parse(file)
        assertContentEquals(listOf(40, 66), parsed.books.map { it.number })
        assertEquals(BookNames.ENGLISH[40], parsed.books.first().name)
    }

    @Test
    fun `book numbers outside the canon are dropped while their siblings survive`() {
        val file = write(
            "outofrange.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="English KJV">
            <book number="0"><chapter number="1"><verse number="1">before</verse></chapter></book>
            <book number="67"><chapter number="1"><verse number="1">after</verse></chapter></book>
            <book number="nonsense"><chapter number="1"><verse number="1">junk</verse></chapter></book>
            <book><chapter number="1"><verse number="1">unnumbered</verse></chapter></book>
            <book number="5"><chapter number="1"><verse number="1">kept</verse></chapter></book>
            </bible>"""
        )
        assertContentEquals(listOf(5), XmlToSpbConverter.parse(file).books.map { it.number })
    }

    @Test
    fun `an empty bible yields no books rather than throwing`() {
        val file = write("empty.xml", """<?xml version="1.0" encoding="UTF-8"?><bible translation="Nothing"/>""")
        val parsed = XmlToSpbConverter.parse(file)
        assertTrue(parsed.books.isEmpty())
        assertEquals("Nothing", parsed.name)
    }

    // --- byte-level input the archive really contains ------------------------------------------

    @Test
    fun `a file with a utf-8 byte order mark is read`() {
        val file = File(temp, "bom.xml")
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        file.writeBytes(bom + genesis("""<bible translation="English KJV">""").toByteArray(Charsets.UTF_8))

        val parsed = XmlToSpbConverter.parse(file)
        assertEquals("English KJV", parsed.name)
        assertEquals(2, parsed.books.single().chapters.single().verses.size)
    }

    @Test
    fun `a file with windows line endings is read`() {
        val file = write("crlf.xml", genesis("""<bible translation="English KJV">""").replace("\n", "\r\n"))
        assertEquals(2, XmlToSpbConverter.parse(file).books.single().chapters.single().verses.size)
    }

    @Test
    fun `non-ascii verse text survives the parse`() {
        val file = write(
            "amharic.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Amharic 2024"><book number="1"><chapter number="1">
            <verse number="1">በመጀመሪያ እግዜአብሔር ሰማይና ምድርን ፈጠረ።</verse></chapter></book></bible>"""
        )
        assertEquals(
            "በመጀመሪያ እግዜአብሔር ሰማይና ምድርን ፈጠረ።",
            XmlToSpbConverter.parse(file).books.single().chapters.single().verses.single().text
        )
    }

    // --- language and book names ----------------------------------------------------------------

    @Test
    fun `the caller's language beats what the title suggests`() {
        val file = write("lang.xml", genesis("""<bible translation="Russian Synodal">"""))
        val parsed = XmlToSpbConverter.parseBeblia(file, language = "ukr")
        assertEquals("UKR", parsed.language, "the caller's value wins and is normalised to upper case")
        assertEquals(BookNames.UKRAINIAN[1], parsed.books.single().name)
    }

    @Test
    fun `a blank caller language falls back to the title`() {
        val file = write("blank.xml", genesis("""<bible translation="Russian Synodal">"""))
        assertEquals("RUS", XmlToSpbConverter.parseBeblia(file, language = "  ").language)
    }

    @Test
    fun `an unrecognised title yields no language rather than defaulting to Russian`() {
        val file = write("shan.xml", genesis("""<bible translation="Shan Common Language">"""))
        val parsed = XmlToSpbConverter.parse(file)
        assertNull(parsed.language, "guessing a language is worse than admitting to none")
        assertEquals(BookNames.ENGLISH[1], parsed.books.single().name, "book names fall back to English")
    }

    @Test
    fun `book names come from the language table where one exists`() {
        val file = write("de.xml", genesis("""<bible translation="German Luther 1912">"""))
        assertEquals(BookNames.GERMAN[1], XmlToSpbConverter.parse(file).books.single().name)
    }

    // --- caller-supplied metadata -----------------------------------------------------------------

    @Test
    fun `caller metadata wins over the file and blanks fall back to it`() {
        val file = write("meta.xml", genesis("""<bible translation="File Title" status="File Rights" link="file://x">"""))
        val parsed = XmlToSpbConverter.parseBeblia(
            file,
            language = "ENG",
            name = "Catalogue Title",
            rights = "",
            source = "https://catalogue",
            identifier = "KJ",
        )
        assertEquals("Catalogue Title", parsed.name)
        assertEquals("Catalogue Title", parsed.title)
        assertEquals("File Rights", parsed.rights, "a blank from the caller falls back to the file")
        assertEquals("https://catalogue", parsed.source)
        assertEquals("KJ", parsed.identifier)
    }

    @Test
    fun `a file with no title at all is named Unknown rather than left blank`() {
        val file = write(
            "untitled.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible status="Public Domain"><book number="1"><chapter number="1">
            <verse number="1">text</verse></chapter></book></bible>"""
        )
        assertEquals("Unknown", XmlToSpbConverter.parse(file).name)
    }

    // --- progress and end to end --------------------------------------------------------------------

    @Test
    fun `progress never goes backwards and ends at one`() {
        val file = write("progress.xml", genesis("""<bible translation="English KJV">"""))
        val seen = mutableListOf<Float>()
        XmlToSpbConverter.parseBeblia(file, onProgress = { seen.add(it) })

        assertEquals(seen.sorted(), seen, "progress only ever moves forward")
        assertEquals(1f, seen.last())
    }

    @Test
    fun `converting end to end produces a readable module carrying its copyright and source`() {
        val file = write(
            "e2e.xml",
            genesis("""<bible translation="English KJV" status="Public Domain" link="https://example.org">""")
        )
        val out = File(temp, "e2e.spb")
        XmlToSpbConverter.convert(file, out)

        val lines = out.readLines()
        assertTrue(lines.first().startsWith("##spDataVersion:"), "the format marker leads the file")
        assertTrue(lines.any { it == "##Copyright:\tPublic Domain" })
        assertTrue(lines.any { it == "##Source:\thttps://example.org" })
        assertTrue(lines.any { it.startsWith("B001C001V001\t") })
    }

    // --- dispatch ------------------------------------------------------------------------------------

    @Test
    fun `a zefania file is still routed to the zefania parser`() {
        val file = write(
            "zefania.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <XMLBIBLE biblename="Zefania Module"><INFORMATION><language>ENG</language></INFORMATION>
            <BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="1">
            <VERS vnumber="1">In the beginning</VERS></CHAPTER></BIBLEBOOK></XMLBIBLE>"""
        )
        val parsed = XmlToSpbConverter.parse(file)
        assertEquals("Zefania Module", parsed.name)
        assertEquals("Genesis", parsed.books.single().name)
    }

    @Test
    fun `a bible root with no recognised title attribute is not claimed by this parser`() {
        val file = write(
            "unclaimed.xml",
            """<?xml version="1.0" encoding="UTF-8"?><bible revision="1"><book number="1"/></bible>"""
        )
        // Falls through to the Zefania parser, which finds no BIBLEBOOK elements.
        assertTrue(XmlToSpbConverter.parse(file).books.isEmpty())
    }
}
