package org.churchpresenter.app.churchpresenter.data

import converter.bible.UsfxToSpbConverter
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading eBible.org's USFX into the shape the `.spb` writer expects.
 *
 * USFX puts verses on *milestones* — `<v id="1"/>` is empty and the text simply follows until the
 * next marker — so unlike the Zefania path there is no verse element to read. Everything here is
 * about where that running text starts and stops: a footnote sits inside the verse it annotates and
 * must not be spliced into the middle of it, a section heading between two verses belongs to
 * neither, and word-level `<w>` tags wrap ordinary words that must survive.
 *
 * Two behaviours are deliberate and easy to mistake for bugs, so both are pinned here. Verse
 * markers can be nested inside an element that is otherwise skipped — the Berean Standard Bible
 * wraps Zechariah 12:1 in a descriptor — and dropping the wrapper would silently lose the verse.
 * And a marker with no text at all is dropped rather than written empty, because translations
 * publish the disputed verses that way; the NET Bible does it seventeen times.
 *
 * Fixtures are written inline: real modules are megabytes, and every rule here is expressible in a
 * few hundred bytes.
 */
class UsfxConversionTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-usfx-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun usfx(body: String): File =
        File(dir, "module_usfx.xml").apply {
            writeText("""<?xml version="1.0" encoding="utf-8"?><usfx><languageCode>eng</languageCode>$body</usfx>""")
        }

    private fun bookNames(vararg entries: Pair<String, String>): File =
        File(dir, "BookNames.xml").apply {
            val books = entries.joinToString("") { (code, name) ->
                """<book code="$code" abbr="$name" short="$name" long="$name"/>"""
            }
            writeText("""<?xml version="1.0" encoding="utf-8"?><BookNames>$books</BookNames>""")
        }

    private fun parse(body: String, names: File? = null, language: String? = "ENG") =
        UsfxToSpbConverter.parse(usfx(body), names, "Test Bible", language, "Public Domain")

    private fun firstVerseText(body: String): String =
        parse(body).books.first().chapters.first().verses.first().text

    // --- book codes ---

    @Test
    fun `usfm book codes map onto the canonical order`() {
        assertEquals(1, UsfxToSpbConverter.bookNumberFor("GEN"))
        assertEquals(19, UsfxToSpbConverter.bookNumberFor("PSA"))
        assertEquals(40, UsfxToSpbConverter.bookNumberFor("MAT"))
        assertEquals(66, UsfxToSpbConverter.bookNumberFor("REV"))
        assertEquals(1, UsfxToSpbConverter.bookNumberFor("gen"), "codes are matched case-insensitively")
    }

    @Test
    fun `books outside the canon are left out rather than mis-numbered`() {
        // Deuterocanonical books have no slot in the 66-book numbering the app uses.
        assertNull(UsfxToSpbConverter.bookNumberFor("TOB"))
        assertNull(UsfxToSpbConverter.bookNumberFor("SIR"))

        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>In the beginning<ve/></book>""" +
                """<book id="TOB"><c id="1"/><v id="1"/>Tobit text<ve/></book>"""
        )

        assertEquals(listOf(1), bible.books.map { it.number })
    }

    // --- where verse text starts and stops ---

    @Test
    fun `verse text runs from its marker to the end marker`() {
        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>First verse<ve/><v id="2"/>Second verse<ve/></book>"""
        )
        val verses = bible.books.first().chapters.first().verses

        assertEquals(listOf(1 to "First verse", 2 to "Second verse"), verses.map { it.number to it.text })
    }

    @Test
    fun `a footnote inside a verse is not spliced into the text`() {
        val text = firstVerseText(
            """<book id="GEN"><c id="1"/><v id="1"/>And God said, “Let there be light,”""" +
                """<f caller="+"><fr>1:3</fr><ft>Cited in 2 Corinthians 4:6</ft></f> and there was light.<ve/></book>"""
        )

        assertEquals("And God said, “Let there be light,” and there was light.", text)
        assertFalse(text.contains("Corinthians"), "the annotation must not end up on screen mid-verse")
    }

    @Test
    fun `a section heading between verses belongs to neither`() {
        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>First<ve/>""" +
                """<s style="s1">The Creation</s><v id="2"/>Second<ve/></book>"""
        )
        val verses = bible.books.first().chapters.first().verses

        assertEquals(listOf("First", "Second"), verses.map { it.text })
    }

    @Test
    fun `word level tags keep their text`() {
        val text = firstVerseText(
            """<book id="GEN"><c id="1"/><v id="1"/><w s="H8064">In</w> <w s="H1254">the</w> beginning.<ve/></book>"""
        )

        assertEquals("In the beginning.", text)
    }

    @Test
    fun `line wrapping in the source does not survive into the verse`() {
        val text =
            firstVerseText("<book id=\"GEN\"><c id=\"1\"/><v id=\"1\"/>In the\n  beginning\tGod\ncreated.<ve/></book>")

        assertEquals("In the beginning God created.", text)
    }

    @Test
    fun `a verse ends at the next marker even without an end marker`() {
        val verses = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>First<v id="2"/>Second<c id="2"/><v id="1"/>Third</book>"""
        ).books.first().chapters

        assertEquals(listOf("First", "Second"), verses.first { it.number == 1 }.verses.map { it.text })
        assertEquals(listOf("Third"), verses.first { it.number == 2 }.verses.map { it.text })
    }

    @Test
    fun `a verse range is stored under its first number`() {
        val verses = parse(
            """<book id="GEN"><c id="1"/><v id="17-18"/>Joined verses<ve/></book>"""
        ).books.first().chapters.first().verses

        assertEquals(17, verses.single().number)
    }

    // --- the two deliberate behaviours ---

    @Test
    fun `a verse marker nested inside a skipped element is still read`() {
        // The Berean Standard Bible wraps Zechariah 12:1 in a descriptor; skipping the wrapper
        // wholesale silently loses the verse.
        val verses = parse(
            """<book id="ZEC"><c id="12"/><s style="s1">The Coming Deliverance</s>""" +
                """<d style="d"><v id="1"/>This is the burden of the word of the LORD.</d>""" +
                """<v id="2"/>Behold, I will make Jerusalem a cup.<ve/></book>"""
        ).books.first().chapters.first().verses

        assertEquals(listOf(1, 2), verses.map { it.number }, "the descriptor carries verse 1")
        assertEquals("This is the burden of the word of the LORD.", verses.first().text)
    }

    @Test
    fun `a verse whose text is only a footnote is dropped rather than written blank`() {
        // How the NET Bible publishes Matthew 17:21 and the other disputed verses.
        val verses = parse(
            """<book id="MAT"><c id="17"/><v id="20"/>Because of your little faith.<ve/>""" +
                """<v id="21"/><f caller="+"><fr>17:21</fr><ft>[[EMPTY]]</ft></f><ve/>""" +
                """<v id="22"/>When they gathered in Galilee.<ve/></book>"""
        ).books.first().chapters.first().verses

        assertEquals(listOf(20, 22), verses.map { it.number }, "an empty verse would project as a blank slide")
    }

    // --- book names ---

    @Test
    fun `book names come from the translation's own list`() {
        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>text<ve/></book>""",
            names = bookNames("GEN" to "Бытие")
        )

        assertEquals("Бытие", bible.books.single().name, "eBible ships names in the translation's language")
    }

    @Test
    fun `a missing book list falls back to a curated table`() {
        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>text<ve/></book>""",
            names = null,
            language = "RUS"
        )

        assertEquals("Бытие", bible.books.single().name)
    }

    @Test
    fun `an unknown language with no book list falls back to English`() {
        val bible = parse(
            """<book id="GEN"><c id="1"/><v id="1"/>text<ve/></book>""",
            names = null,
            language = "SWA"
        )

        assertEquals("Genesis", bible.books.single().name)
    }

    // --- metadata ---

    @Test
    fun `the copyright is carried through for the header`() {
        val bible = parse("""<book id="GEN"><c id="1"/><v id="1"/>text<ve/></book>""")

        assertEquals("Public Domain", bible.rights)
        assertEquals("Test Bible", bible.name)
        assertTrue(bible.books.isNotEmpty())
    }
}
