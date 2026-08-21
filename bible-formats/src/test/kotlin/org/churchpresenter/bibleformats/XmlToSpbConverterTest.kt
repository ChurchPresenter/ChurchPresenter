package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Converting a downloaded Bible into the app's `.spb` module format.
 *
 * The `.spb` file is tab-separated with a header block, so anything that can smuggle a tab or a
 * newline into a header value corrupts the structure of the whole file — that is why the header
 * flattening is pinned here. The other load-bearing piece is the `BxxxCxxxVxxx` verse code: it is
 * what lets two translations be shown side by side, so Orthodox (Septuagint) Psalm numbering has
 * to be mapped onto Hebrew numbering or the two panes drift apart partway through the Psalms.
 */
class XmlToSpbConverterTest {

    private val temp: File = Files.createTempDirectory("converter-xml-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun bible(
        name: String = "Test Version",
        language: String? = "ENG",
        rights: String = "",
        source: String = "",
        description: String = "",
        books: List<BibleBook> = listOf(
            BibleBook(1, "Genesis", listOf(BibleChapter(1, listOf(BibleVerse(1, "In the beginning")))))
        ),
    ) = ParsedBible(name, description, language, books, rights = rights, source = source)

    private fun writeAndRead(b: ParsedBible): List<String> {
        val out = File(temp, "out.spb")
        XmlToSpbConverter.write(b, out)
        return out.readLines()
    }

    // ── Header block ──────────────────────────────────────────────────────────

    @Test
    fun `the header carries the title and an abbreviation derived from it`() {
        val lines = writeAndRead(bible(name = "A Conservative Version"))
        assertTrue(lines.contains("##Title:\tA Conservative Version"))
        // Same rule the app uses when naming the installed file, so the two cannot drift apart.
        assertTrue(lines.contains("##Abbreviation:\tACV"))
    }

    @Test
    fun `a right-to-left language is flagged in the header`() {
        assertTrue(writeAndRead(bible(language = "HEB")).contains("##RightToLeft:\t1"))
        assertTrue(writeAndRead(bible(language = "ENG")).contains("##RightToLeft:\t"))
    }

    @Test
    fun `copyright and source are written only when present`() {
        val withAttribution = writeAndRead(bible(rights = "CC-BY", source = "ebible.org"))
        assertTrue(withAttribution.any { it == "##Copyright:\tCC-BY" }, "attribution travels with the file")
        assertTrue(withAttribution.any { it == "##Source:\tebible.org" })

        val without = writeAndRead(bible())
        assertTrue(without.none { it.startsWith("##Copyright:") })
        assertTrue(without.none { it.startsWith("##Source:") })
    }

    @Test
    fun `a multi-line header value is flattened so it cannot break the file structure`() {
        // A `<rights>` block spanning lines would otherwise inject raw newlines into a
        // line-oriented format and leave the rest of the file unparseable.
        val lines = writeAndRead(bible(rights = "Line one\nLine two\twith a tab", description = "A\nB"))
        val copyright = lines.single { it.startsWith("##Copyright:") }
        assertTrue(!copyright.drop("##Copyright:".length).contains('\n'))
        assertEquals(1, copyright.count { it == '\t' }, "only the key/value separator remains")
        assertTrue(lines.single { it.startsWith("##Information:") }.count { it == '\t' } == 1)
    }

    @Test
    fun `the book index lists each book with its chapter count`() {
        val lines = writeAndRead(
            bible(
                books = listOf(
                    BibleBook(
                        1, "Genesis",
                        listOf(
                            BibleChapter(1, listOf(BibleVerse(1, "a"))),
                            BibleChapter(2, listOf(BibleVerse(1, "b"))),
                        ),
                    ),
                    BibleBook(40, "Matthew", listOf(BibleChapter(1, listOf(BibleVerse(1, "c"))))),
                )
            )
        )
        assertTrue(lines.contains("1\tGenesis\t2"))
        assertTrue(lines.contains("40\tMatthew\t1"))
        assertTrue(lines.contains("-----"), "the index is separated from the verses")
    }

    // ── Verse rows ────────────────────────────────────────────────────────────

    private fun verseRows(lines: List<String>) = lines.dropWhile { it != "-----" }.drop(1)

    @Test
    fun `each verse row carries a zero-padded code and its display columns`() {
        val rows = verseRows(writeAndRead(bible()))
        assertEquals("B001C001V001\t1\t1\t1\tIn the beginning", rows.single())
    }

    @Test
    fun `the code pads every component to three digits`() {
        val rows = verseRows(
            writeAndRead(
                bible(
                    books = listOf(BibleBook(19, "Psalms", listOf(BibleChapter(119, listOf(BibleVerse(176, "x")))))),
                    language = "ENG",
                )
            )
        )
        assertTrue(rows.single().startsWith("B019C119V176"), "was ${rows.single()}")
    }

    // ── Septuagint Psalm numbering ────────────────────────────────────────────

    private fun psalmCode(language: String?, chapter: Int, verse: Int = 1, text: String = "Some verse text"): String {
        val rows = verseRows(
            writeAndRead(
                bible(
                    language = language,
                    books = listOf(
                        BibleBook(19, "Psalms", listOf(BibleChapter(chapter, listOf(BibleVerse(verse, text))))),
                    ),
                )
            )
        )
        return rows.single().substringBefore('\t')
    }

    @Test
    fun `a Hebrew-numbered translation codes Psalms as they are numbered`() {
        assertEquals("B019C023V001", psalmCode("ENG", chapter = 23))
    }

    @Test
    fun `a Septuagint-numbered translation is mapped onto Hebrew numbering`() {
        // Russian Psalm 22 is Hebrew Psalm 23 — without the mapping, a Russian and an English
        // Bible shown side by side would display different psalms from Psalm 10 onward.
        assertEquals("B019C023V001", psalmCode("RUS", chapter = 22))
        // The first eight are shared, so nothing is shifted there.
        assertEquals("B019C008V001", psalmCode("RUS", chapter = 8))
    }

    @Test
    fun `the display columns keep the translation's own numbering`() {
        val rows = verseRows(
            writeAndRead(
                bible(
                    language = "RUS",
                    books = listOf(BibleBook(19, "Psalms", listOf(BibleChapter(22, listOf(BibleVerse(1, "text")))))),
                )
            )
        )
        val columns = rows.single().split('\t')
        assertEquals("B019C023V001", columns[0], "the code is Hebrew-numbered for cross-referencing")
        assertEquals("22", columns[2], "but the reader still sees Psalm 22, as printed in their Bible")
    }

    @Test
    fun `only Psalms are renumbered, not the rest of the book list`() {
        val rows = verseRows(
            writeAndRead(
                bible(
                    language = "RUS",
                    books = listOf(BibleBook(1, "Бытие", listOf(BibleChapter(22, listOf(BibleVerse(1, "text")))))),
                )
            )
        )
        assertTrue(rows.single().startsWith("B001C022V001"), "Genesis 22 stays Genesis 22")
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @Test
    fun `a simple bible xml is parsed into books, chapters and verses`() {
        val xml = File(temp, "simple.xml")
        xml.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Russian Synodal">
              <book number="1">
                <chapter number="1">
                  <verse number="1">В начале сотворил Бог</verse>
                  <verse number="2">Земля же была безвидна</verse>
                </chapter>
              </book>
            </bible>
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val parsed = XmlToSpbConverter.parse(xml)
        assertEquals("Russian Synodal", parsed.name)
        assertEquals("RUS", parsed.language, "the language is inferred from the translation name")
        val book = parsed.books.single()
        assertEquals(1, book.number)
        assertEquals(2, book.chapters.single().verses.size)
        assertEquals("В начале сотворил Бог", book.chapters.single().verses.first().text)
    }

    @Test
    fun `book names come from the language table rather than the file`() {
        val xml = File(temp, "named.xml")
        xml.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Ukrainian Bible"><book number="1"><chapter number="1">
            <verse number="1">text</verse></chapter></book></bible>""".trimIndent(),
            Charsets.UTF_8,
        )
        assertEquals(BookNames.UKRAINIAN[1], XmlToSpbConverter.parse(xml).books.single().name)
    }

    @Test
    fun `a book with no number attribute is skipped rather than failing the whole file`() {
        val xml = File(temp, "partial.xml")
        xml.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Russian Synodal">
            <book><chapter number="1"><verse number="1">orphan</verse></chapter></book>
            <book number="2"><chapter number="1"><verse number="1">kept</verse></chapter></book>
            </bible>""".trimIndent(),
            Charsets.UTF_8,
        )
        assertEquals(listOf(2), XmlToSpbConverter.parse(xml).books.map { it.number })
    }

    @Test
    fun `converting end to end produces a readable module file`() {
        val xml = File(temp, "end2end.xml")
        xml.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <bible translation="Russian Synodal"><book number="1"><chapter number="1">
            <verse number="1">В начале</verse></chapter></book></bible>""".trimIndent(),
            Charsets.UTF_8,
        )
        val out = File(temp, "end2end.spb")
        XmlToSpbConverter.convert(xml, out)

        val lines = out.readLines()
        assertTrue(lines.first().startsWith("##spDataVersion:"), "the format marker leads the file")
        assertTrue(lines.any { it.startsWith("B001C001V001\t") })
    }

    @Test
    fun `progress is reported once per book and ends at one`() {
        val reported = mutableListOf<Float>()
        XmlToSpbConverter.write(
            bible(
                books = listOf(
                    BibleBook(1, "Genesis", listOf(BibleChapter(1, listOf(BibleVerse(1, "a"))))),
                    BibleBook(2, "Exodus", listOf(BibleChapter(1, listOf(BibleVerse(1, "b"))))),
                )
            ),
            File(temp, "progress.spb"),
        ) { reported.add(it) }

        assertEquals(2, reported.size)
        assertEquals(1f, reported.last(), 0.0001f)
        assertEquals(reported, reported.sorted(), "progress never goes backwards")
    }
}
