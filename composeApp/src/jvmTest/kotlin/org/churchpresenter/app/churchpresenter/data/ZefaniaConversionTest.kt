package org.churchpresenter.app.churchpresenter.data

import converter.bible.XmlToSpbConverter
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Converting a Zefania module into an `.spb`, as the download path does it.
 *
 * The book-name rule carries the most weight here. Modules in this archive routinely ship book
 * names in the wrong language — the Swahili and Thai ones both carry German (`Matthäus`, `1 Mose`),
 * and the Afrikaans one carries English — so `bname` is trusted only for English, where it is the
 * module's own wording, and every other language is served from the curated tables or falls back to
 * English. Those are the assertions that would catch someone "fixing" the converter to prefer
 * `bname` and quietly shipping a Thai Bible with German book names.
 *
 * The rest guards the file format itself: header values are tab-separated single lines, so a
 * copyright notice wrapped across several lines in the source XML must not be written through
 * verbatim and split the file.
 */
class ZefaniaConversionTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-zefania-convert-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** [bookName] populates `bname`, as most archive modules do. */
    private fun module(
        biblename: String = "A Conservative Version",
        language: String = "ENG",
        bookName: String? = null,
        rights: String = "",
        title: String = "A Conservative Version",
        identifier: String = "ACV",
    ): File {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<XMLBIBLE biblename="$biblename">""")
            append("<INFORMATION>")
            append("<title>$title</title><identifier>$identifier</identifier>")
            append("<language>$language</language><rights>$rights</rights>")
            append("<source>https://example.invalid/x</source>")
            append("</INFORMATION>")
            val bname = bookName?.let { """ bname="$it"""" } ?: ""
            append("""<BIBLEBOOK bnumber="1"$bname>""")
            append("""<CHAPTER cnumber="1"><VERS vnumber="1">In the beginning</VERS></CHAPTER>""")
            append("</BIBLEBOOK>")
            append("</XMLBIBLE>")
        }
        return File(dir, "module.xml").apply { writeText(xml) }
    }

    private fun convert(source: File): String {
        val out = File(dir, "out.spb")
        XmlToSpbConverter.convert(source, out)
        return out.readText()
    }

    /** The book-header line for book 1: `1<tab>Name<tab>chapters`. */
    private fun bookNameIn(spb: String): String =
        spb.lineSequence().first { it.startsWith("1\t") }.split("\t")[1]

    @Test
    fun `an English module uses its own book names`() {
        val spb = convert(module(language = "ENG", bookName = "The First Book of Moses"))

        assertEquals("The First Book of Moses", bookNameIn(spb))
    }

    @Test
    fun `a German book name on a Thai module is not used`() {
        // The real THA module in the archive ships "1 Mose" for Genesis.
        val spb = convert(module(language = "THA", bookName = "1 Mose"))

        assertFalse(bookNameIn(spb).contains("Mose"), "a Thai Bible must not end up with German book names")
        assertEquals("Genesis", bookNameIn(spb), "with no Thai table, English is the honest fallback")
    }

    @Test
    fun `a German book name on a Swahili module is not used`() {
        // The real SWA module ships "Matthäus" for Matthew.
        val spb = convert(module(language = "SWA", bookName = "Matthäus"))

        assertFalse(bookNameIn(spb).contains("Matth"), "a Swahili Bible must not end up with German book names")
    }

    @Test
    fun `a curated language uses its own table rather than the module's names`() {
        val spb = convert(module(language = "RUS", bookName = "1 Mose"))

        assertEquals("Бытие", bookNameIn(spb), "Russian has a curated table, which wins over the module")
    }

    @Test
    fun `bibliographic language codes resolve to their book names`() {
        // The archive's directories use bibliographic codes — GER, not DEU.
        val spb = convert(module(language = "GER", bookName = "Genesis"))

        assertEquals("1. Mose", bookNameIn(spb))
    }

    @Test
    fun `module metadata is carried into the header`() {
        val spb = convert(module(rights = "© 2009 Example Society", identifier = "ACV"))

        assertTrue(spb.contains("##Title:\tA Conservative Version"))
        assertTrue(spb.contains("##Copyright:\t© 2009 Example Society"), "attribution travels with the file")
        assertTrue(spb.contains("##Source:\thttps://example.invalid/x"))
    }

    @Test
    fun `a multi-line copyright cannot break the header format`() {
        val spb = convert(module(rights = "Copyright\n2009\tExample"))

        val copyrightLine = spb.lineSequence().first { it.startsWith("##Copyright:") }
        assertEquals("##Copyright:\tCopyright 2009 Example", copyrightLine)
    }

    @Test
    fun `a module with no biblename falls back to its title`() {
        val spb = convert(module(biblename = "", title = "Fallback Title"))

        assertTrue(spb.contains("##Title:\tFallback Title"), "an empty biblename must not become \"Unknown\"")
    }

    @Test
    fun `a right-to-left language is marked as such`() {
        val hebrew = convert(module(language = "HEB"))
        val english = convert(module(language = "ENG"))

        assertTrue(hebrew.contains("##RightToLeft:\t1"), "Hebrew must render right-to-left")
        assertTrue(english.contains("##RightToLeft:\t\n") || english.contains("##RightToLeft:\t\r\n"))
    }

    @Test
    fun `writing reports progress that ends complete`() {
        val parsed = XmlToSpbConverter.parse(module())
        val reported = mutableListOf<Float>()

        XmlToSpbConverter.write(parsed, File(dir, "progress.spb")) { reported.add(it) }

        assertTrue(reported.isNotEmpty())
        assertEquals(1f, reported.last())
        assertEquals(reported.sorted(), reported, "progress must never go backwards")
    }
}
