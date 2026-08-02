package converter

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parsing the Zefania XML flavour, which is what the in-app Bible browser downloads.
 *
 * Almost everything here is about a module that is *nearly* right: an empty `biblename` with the
 * real name in `<title>`, a missing `<language>` that has to be inferred from the archive path,
 * and the Russian/Ukrainian mix-up where a Ukrainian module declares itself `RUS`. Each of those
 * ships in the real archive, and getting any of them wrong installs a translation under a name or
 * a language the user cannot find again.
 */
class ZefaniaParsingTest {

    private val temp: File = Files.createTempDirectory("converter-zefania-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun zefania(
        path: String = "module.xml",
        bibleName: String = "Test Version",
        information: String = "",
        books: String = """<BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="1">
                           <VERS vnumber="1">In the beginning</VERS></CHAPTER></BIBLEBOOK>""",
    ): File {
        val file = File(temp, path)
        file.parentFile.mkdirs()
        file.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <XMLBIBLE biblename="$bibleName">$information$books</XMLBIBLE>""".trimIndent(),
            Charsets.UTF_8,
        )
        return file
    }

    @Test
    fun `a Zefania module is parsed into books, chapters and verses`() {
        val parsed = XmlToSpbConverter.parse(zefania())
        assertEquals("Test Version", parsed.name)
        val book = parsed.books.single()
        assertEquals(1, book.number)
        assertEquals("In the beginning", book.chapters.single().verses.single().text)
    }

    @Test
    fun `INFORMATION metadata is carried onto the parsed bible`() {
        val parsed = XmlToSpbConverter.parse(
            zefania(
                information = """<INFORMATION>
                    <title>A Conservative Version</title>
                    <identifier>ACV</identifier>
                    <language>ENG</language>
                    <rights>Public Domain</rights>
                    <source>zefania.de</source>
                    <description>A literal translation</description>
                </INFORMATION>""",
            )
        )
        assertEquals("A Conservative Version", parsed.title)
        assertEquals("ACV", parsed.identifier)
        assertEquals("ENG", parsed.language)
        assertEquals("Public Domain", parsed.rights)
        assertEquals("zefania.de", parsed.source)
        assertTrue(parsed.description.contains("literal"))
    }

    @Test
    fun `an empty biblename falls back to the title rather than to Unknown`() {
        val parsed = XmlToSpbConverter.parse(
            zefania(bibleName = "", information = "<INFORMATION><title>Real Name</title></INFORMATION>")
        )
        assertEquals("Real Name", parsed.name)
    }

    @Test
    fun `a module with neither a name nor a title is labelled Unknown`() {
        assertEquals("Unknown", XmlToSpbConverter.parse(zefania(bibleName = "")).name)
    }

    @Test
    fun `a Ukrainian module mislabelled as Russian is corrected from its path`() {
        // Real archive entries do this: the XML says RUS but the module is Ukrainian. Left alone,
        // it installs with Russian book names over Ukrainian text.
        val parsed = XmlToSpbConverter.parse(
            zefania(
                path = "ukrainian/ukr-bible.xml",
                information = "<INFORMATION><language>RUS</language></INFORMATION>",
            )
        )
        assertEquals("UKR", parsed.language)
    }

    @Test
    fun `a genuinely Russian module keeps its declared language`() {
        val parsed = XmlToSpbConverter.parse(
            zefania(path = "russian/rus-bible.xml", information = "<INFORMATION><language>RUS</language></INFORMATION>")
        )
        assertEquals("RUS", parsed.language)
    }

    @Test
    fun `a module with no declared language infers one from its archive folder`() {
        // The archive lays modules out as <LANG>/<something>/<something>/file.xml, so the language
        // is four path components up.
        val parsed = XmlToSpbConverter.parse(zefania(path = "DEU/a/b/module.xml"))
        assertEquals("DEU", parsed.language)
    }

    @Test
    fun `an unrecognisable folder leaves the language unknown rather than guessing`() {
        val parsed = XmlToSpbConverter.parse(zefania(path = "ZZZ/a/b/module.xml"))
        assertTrue(parsed.language.isNullOrBlank(), "got '${parsed.language}'")
    }

    @Test
    fun `a book keeps the name declared in the file`() {
        val parsed = XmlToSpbConverter.parse(
            zefania(
                books = """<BIBLEBOOK bnumber="1" bname="Бытие"><CHAPTER cnumber="1">
                           <VERS vnumber="1">В начале</VERS></CHAPTER></BIBLEBOOK>""",
                information = "<INFORMATION><language>RUS</language></INFORMATION>",
            )
        )
        assertEquals("Бытие", parsed.books.single().name)
    }

    @Test
    fun `nested markup inside a verse is flattened to its text`() {
        // Zefania verses carry <STYLE> and <NOTE> children; the app shows plain text.
        val parsed = XmlToSpbConverter.parse(
            zefania(
                books = """<BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="1">
                           <VERS vnumber="1">In the <STYLE css="it">beginning</STYLE></VERS>
                           </CHAPTER></BIBLEBOOK>""",
            )
        )
        assertEquals("In the beginning", parsed.books.single().chapters.single().verses.single().text)
    }

    @Test
    fun `a chapter or verse with an unparseable number falls back to zero rather than failing`() {
        val parsed = XmlToSpbConverter.parse(
            zefania(
                books = """<BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="x">
                           <VERS vnumber="y">Text</VERS></CHAPTER></BIBLEBOOK>""",
            )
        )
        val chapter = parsed.books.single().chapters.single()
        assertEquals(0, chapter.number)
        assertEquals(0, chapter.verses.single().number)
    }

    @Test
    fun `converting a Zefania module end to end writes a module file`() {
        val out = File(temp, "out.spb")
        XmlToSpbConverter.convert(
            zefania(information = "<INFORMATION><language>ENG</language><rights>CC-BY</rights></INFORMATION>"),
            out,
        )
        val lines = out.readLines()
        assertTrue(lines.any { it == "##Copyright:\tCC-BY" })
        assertTrue(lines.any { it.startsWith("B001C001V001\t") })
    }

    @Test
    fun `a batch conversion reports one output per input`() {
        val a = zefania(path = "a.xml", bibleName = "First")
        val b = zefania(path = "b.xml", bibleName = "Second")
        val out = File(temp, "out").apply { mkdirs() }

        val pairs = XmlToSpbConverter.convertBatch(listOf(a, b), out)
        assertEquals(2, pairs.size)
        assertTrue(pairs.all { it.second.exists() && it.second.length() > 0 })
    }
}
