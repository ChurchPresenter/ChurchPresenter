package converter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Converting eBible.org's USFX into the app's module format.
 *
 * The hard part is deciding what counts as scripture. Footnotes and cross-references are nested
 * *inside* the verse they annotate, so anything not deliberately dropped gets spliced into the
 * middle of that verse on screen — which is what makes the skip list load-bearing rather than
 * cosmetic. Psalm superscriptions are the mirror image: they look editorial but are scripture, and
 * dropping them loses text.
 */
class UsfxToSpbConverterTest {

    private val temp: File = Files.createTempDirectory("converter-usfx-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun usfx(body: String): File =
        File(temp, "book.usfx").apply {
            writeText("""<?xml version="1.0" encoding="UTF-8"?><usfx>$body</usfx>""", Charsets.UTF_8)
        }

    private fun parse(file: File, names: File? = null) =
        UsfxToSpbConverter.parse(file, names, name = "Test", language = "ENG")

    private fun verseText(bible: ParsedBible, book: Int = 1, chapter: Int = 1, verse: Int = 1): String? =
        bible.books.firstOrNull { it.number == book }
            ?.chapters?.firstOrNull { it.number == chapter }
            ?.verses?.firstOrNull { it.number == verse }?.text

    // ── Book codes ────────────────────────────────────────────────────────────

    @Test
    fun `USFM book codes map onto canonical numbers`() {
        assertEquals(1, UsfxToSpbConverter.bookNumberFor("GEN"))
        assertEquals(39, UsfxToSpbConverter.bookNumberFor("MAL"), "the Old Testament ends at 39")
        assertEquals(40, UsfxToSpbConverter.bookNumberFor("MAT"), "the New Testament starts at 40")
        assertEquals(66, UsfxToSpbConverter.bookNumberFor("REV"))
    }

    @Test
    fun `book codes are matched regardless of case and padding`() {
        assertEquals(43, UsfxToSpbConverter.bookNumberFor(" jhn "))
    }

    @Test
    fun `a deuterocanonical or unknown code has no number`() {
        // The app's canon is 66 books; anything else must be skipped, not guessed at.
        assertNull(UsfxToSpbConverter.bookNumberFor("TOB"))
        assertNull(UsfxToSpbConverter.bookNumberFor(""))
    }

    // ── Verse extraction ──────────────────────────────────────────────────────

    @Test
    fun `verses are collected under their book and chapter`() {
        val bible = parse(
            usfx(
                """<book id="GEN"><c id="1"/><v id="1"/>In the beginning<ve/>
                   <v id="2"/>And the earth was without form<ve/></book>"""
            )
        )
        val chapter = bible.books.single().chapters.single()
        assertEquals(1, bible.books.single().number)
        assertEquals(listOf(1, 2), chapter.verses.map { it.number })
        assertEquals("In the beginning", chapter.verses.first().text)
    }

    @Test
    fun `a footnote inside a verse is dropped rather than spliced into it`() {
        val bible = parse(
            usfx(
                """<book id="GEN"><c id="1"/><v id="1"/>In the beginning""" +
                    """<f caller="+">A note</f> God created<ve/></book>"""
            )
        )
        val text = verseText(bible)!!
        assertTrue(!text.contains("A note"), "the note is gone: '$text'")
        assertTrue(text.contains("In the beginning") && text.contains("God created"))
    }

    @Test
    fun `a cross reference inside a verse is dropped too`() {
        val bible = parse(
            usfx("""<book id="GEN"><c id="1"/><v id="1"/>Text before<x>See John 1:1</x> text after<ve/></book>""")
        )
        val text = verseText(bible)!!
        assertTrue(!text.contains("See John"), "'$text'")
    }

    @Test
    fun `section headings between verses are not treated as scripture`() {
        val bible = parse(
            usfx(
                """<book id="GEN"><c id="1"/><v id="1"/>First verse<ve/>
                   <s>The Creation</s><v id="2"/>Second verse<ve/></book>"""
            )
        )
        assertTrue(bible.books.single().chapters.single().verses.none { it.text.contains("The Creation") })
    }

    @Test
    fun `a Psalm superscription is kept, because it is scripture rather than editorial`() {
        // `d` is deliberately absent from the skip list — some translations even wrap the verse
        // marker itself in it, so dropping `d` would lose the verse.
        val bible = parse(
            usfx(
                """<book id="PSA"><c id="3"/><d>A Psalm of David</d>""" +
                    """<v id="1"/>Lord, how many are my foes<ve/></book>"""
            )
        )
        val texts = bible.books.single().chapters.single().verses.map { it.text }
        assertTrue(
            texts.any { it.contains("A Psalm of David") } || texts.any { it.contains("Lord, how many") },
            "the superscription is not silently discarded: $texts"
        )
    }

    @Test
    fun `a book outside the 66-book canon is skipped`() {
        val bible = parse(
            usfx(
                """<book id="TOB"><c id="1"/><v id="1"/>Apocryphal text<ve/></book>
                   <book id="GEN"><c id="1"/><v id="1"/>In the beginning<ve/></book>"""
            )
        )
        assertEquals(listOf(1), bible.books.map { it.number }, "only the canonical book is kept")
    }

    @Test
    fun `whitespace inside a verse is normalised to single spaces`() {
        val bible = parse(
            usfx("<book id=\"GEN\"><c id=\"1\"/><v id=\"1\"/>In   the\n  beginning<ve/></book>")
        )
        assertEquals("In the beginning", verseText(bible))
    }

    @Test
    fun `metadata passed in is carried onto the parsed bible`() {
        val bible = UsfxToSpbConverter.parse(
            usfx("""<book id="GEN"><c id="1"/><v id="1"/>Text<ve/></book>"""),
            null, name = "World English Bible", language = "ENG",
            rights = "Public Domain", source = "ebible.org",
        )
        assertEquals("World English Bible", bible.name)
        assertEquals("ENG", bible.language)
        assertEquals("Public Domain", bible.rights, "attribution has to survive into the module")
        assertEquals("ebible.org", bible.source)
    }

    // ── Book names file ───────────────────────────────────────────────────────

    @Test
    fun `book names are read from the companion file, preferring the short form`() {
        val names = File(temp, "names.xml")
        names.writeText(
            """<?xml version="1.0" encoding="UTF-8"?><bookNames>
               <book code="GEN" abbr="Gn" short="Genesis" long="The First Book of Moses"/>
               </bookNames>""".trimIndent(),
            Charsets.UTF_8,
        )
        assertEquals(mapOf("GEN" to "Genesis"), UsfxToSpbConverter.parseBookNames(names))
    }

    @Test
    fun `an entry with no short form falls back to abbr then long`() {
        val names = File(temp, "names.xml")
        names.writeText(
            """<?xml version="1.0" encoding="UTF-8"?><bookNames>
               <book code="EXO" abbr="Ex" long="The Second Book of Moses"/>
               <book code="LEV" long="The Third Book of Moses"/>
               </bookNames>""".trimIndent(),
            Charsets.UTF_8,
        )
        val parsed = UsfxToSpbConverter.parseBookNames(names)
        assertEquals("Ex", parsed["EXO"])
        assertEquals("The Third Book of Moses", parsed["LEV"])
    }

    @Test
    fun `entries with no code or no usable label are ignored`() {
        val names = File(temp, "names.xml")
        names.writeText(
            """<?xml version="1.0" encoding="UTF-8"?><bookNames>
               <book abbr="Orphan"/>
               <book code="NUM"/>
               <book code="DEU" short="Deuteronomy"/>
               </bookNames>""".trimIndent(),
            Charsets.UTF_8,
        )
        assertEquals(mapOf("DEU" to "Deuteronomy"), UsfxToSpbConverter.parseBookNames(names))
    }

    @Test
    fun `a supplied book name is used in place of the built-in table`() {
        val names = File(temp, "names.xml")
        names.writeText(
            """<?xml version="1.0" encoding="UTF-8"?><bookNames>
               <book code="GEN" short="Beresheet"/></bookNames>""".trimIndent(),
            Charsets.UTF_8,
        )
        val bible = parse(usfx("""<book id="GEN"><c id="1"/><v id="1"/>Text<ve/></book>"""), names)
        assertEquals("Beresheet", bible.books.single().name, "the translation's own naming wins")
    }

    @Test
    fun `a missing book names file is not an error`() {
        val bible = UsfxToSpbConverter.parse(
            usfx("""<book id="GEN"><c id="1"/><v id="1"/>Text<ve/></book>"""),
            File(temp, "does-not-exist.xml"), name = "T", language = "ENG",
        )
        assertEquals(1, bible.books.size, "it falls back to the built-in names")
        assertTrue(bible.books.single().name.isNotBlank())
    }
}
