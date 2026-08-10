package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.CrashReportSweep
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loading and querying a `.spb` Bible module, driven by a small synthetic fixture
 * (see [SpbFixture]) rather than a real 4 MB translation.
 *
 * The subtle part of this format is that every verse carries TWO references: the canonical code
 * (`BxxxCyyyVzzz`, Hebrew numbering) and the module's own display numbering. Russian translations
 * follow the LXX for Psalms, so the two genuinely differ — the UI navigates by display numbers
 * while cross-Bible lookups (Instance Link, the secondary translation) go through the code.
 */
class BibleTest {

    private lateinit var dir: File

    /** A failed load reports itself; these tests must not leave the report behind. */
    private val sweep = CrashReportSweep()

    @BeforeTest
    fun createDir() {
        sweep.mark()
        dir = Files.createTempDirectory("cp-bible-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
        sweep.sweep()
    }

    private fun bible() = SpbFixture.loadedBible(dir)

    // ── Book abbreviations ──────────────────────────────────────────────────────

    /**
     * The short form shown in the cross-reference column, derived from the module's own book name
     * so it is always in the module's language rather than the app's.
     */
    @Test
    fun `a one-word book shortens to its opening letters`() {
        val b = bible()
        assertEquals("Gen", b.generateAbbreviation("Genesis"))
        assertEquals("Job", b.generateAbbreviation("Job"), "three letters or fewer stay whole")
        assertEquals("Ruth", b.generateAbbreviation("Ruth"), "four letters stay whole")
        assertEquals("Быт", b.generateAbbreviation("Бытие"), "the rule is not English-specific")
    }

    @Test
    fun `a numbered book keeps its number`() {
        val b = bible()
        // Initials would give "1C" and "1К" — unreadable, and 1 and 2 Corinthians look identical
        // once the eye is scanning a column of them.
        assertEquals("1 Cor", b.generateAbbreviation("1 Corinthians"))
        assertEquals("2 Sam", b.generateAbbreviation("2 Samuel"))
        assertEquals("1 Кор", b.generateAbbreviation("1 Коринфянам"))
        assertEquals("3 John", b.generateAbbreviation("3 John"))
    }

    @Test
    fun `a multi-word book takes its significant word`() {
        val b = bible()
        assertEquals("Song", b.generateAbbreviation("Song of Solomon"), "four letters stay whole")
        assertEquals("Пес", b.generateAbbreviation("Песнь Песней"), "five are cut to three")
    }

    @Test
    fun `a name with nothing worth shortening falls back to initials`() {
        val b = bible()
        assertEquals("AAB", b.generateAbbreviation("A a b"))
        assertEquals("", b.generateAbbreviation("   "), "a blank name has no abbreviation")
    }

    @Test
    fun `the module exposes an abbreviation per book`() {
        val b = bible()
        assertEquals("Gen", b.getBookAbbreviation(1))
        assertEquals("Psa", b.getBookAbbreviation(19))
        assertEquals("John", b.getBookAbbreviation(43))
        assertNull(b.getBookAbbreviation(35), "a book this module lacks has none")
    }

    // ── Loading ─────────────────────────────────────────────────────────────────

    @Test
    fun `books load in the order declared by the module header`() {
        val b = bible()
        assertEquals(3, b.getBookCount())
        assertEquals("Genesis", b.getBookName(1))
        assertEquals("Psalms", b.getBookName(19))
        assertEquals("John", b.getBookName(43))
    }

    @Test
    fun `display index maps to the module's own book ids`() {
        // The books are 1, 19 and 43 — not 1, 2, 3 — so a naive index+1 would be wrong.
        val b = bible()
        assertEquals(1, b.getBookId(0))
        assertEquals(19, b.getBookId(1))
        assertEquals(43, b.getBookId(2))
    }

    @Test
    fun `a book can be found by name`() {
        val b = bible()
        assertEquals(43, b.getBookIdByName("John"))
        assertEquals(19, b.getBookIdByName("Psalms"))
        assertNull(b.getBookIdByName("Habakkuk"), "a book not in this module has no id")
    }

    @Test
    fun `an unknown book id has no name`() {
        assertNull(bible().getBookName(66))
    }

    /**
     * The chapter count is derived from the HIGHEST chapter number present in the verse data —
     * the count declared on the module's header line is parsed but never used. For a complete
     * translation the two agree, so this only shows up on a partial module: the fixture's John
     * declares 1 chapter in its header but contains only chapter 3, and reports 3.
     *
     * That also means a module missing its final chapters silently under-reports, and one
     * containing a single late chapter over-reports — the chapter picker is sized off this.
     */
    @Test
    fun `chapter count is the highest chapter present, not the declared header count`() {
        val b = bible()
        assertEquals(2, b.getChapterCount(0), "Genesis has verses in chapters 1 and 2")
        assertEquals(3, b.getChapterCount(2), "John's only verses are in chapter 3, and 3 is reported")
        assertEquals(0, b.getChapterCount(99), "an out-of-range index has no chapters")
    }

    @Test
    fun `a book with no verses reports no chapters whatever its header says`() {
        val content = SpbFixture.buildContent(
            title = "T",
            books = listOf(SpbFixture.Book(1, "Genesis", 50)),
            verses = emptyList(),
        )
        assertEquals(0, SpbFixture.loadedBible(dir, content).getChapterCount(0))
    }

    /**
     * A missing module must not throw: a folder of translations is loaded together and one that
     * has been moved or deleted cannot take the others down with it. It is still reported —
     * `Bible.loadError` carries the reason, and `BibleLoadErrorTest` covers that side.
     */
    @Test
    fun `a missing module loads as an empty bible instead of throwing`() {
        val b = Bible()
        b.loadFromSpb(File(dir, "does-not-exist.spb").absolutePath) // must not throw
        assertEquals(0, b.getBookCount())
        assertEquals(0, b.getVerseCount())
    }

    @Test
    fun `a malformed module also degrades to an empty bible`() {
        val junk = File(dir, "junk.spb").also { it.writeText("this is not a bible module at all") }
        val b = Bible()
        b.loadFromSpb(junk.absolutePath)
        assertEquals(0, b.getVerseCount())
    }

    // ── Loading from the classpath ─────────────────────────────────────────────

    /**
     * Every other test in this suite loads from a real file on disk — [Files.exists] is only ever
     * reached once [Thread.currentThread].contextClassLoader.getResourceAsStream returns null. The
     * bundled sample Bibles ship as classpath resources, not loose files, so that first branch needs
     * its own coverage: `bible-fixtures/classpath-sample.spb` is a real resource under
     * `jvmTest/resources`, not a generated fixture.
     */
    @Test
    fun `a module bundled on the classpath loads without touching the filesystem`() {
        val b = Bible().also { it.loadFromSpb("bible-fixtures/classpath-sample.spb") }

        assertEquals("Classpath Fixture", b.getBibleTitle())
        assertEquals(listOf("Genesis"), b.getBooks())
        assertEquals(2, b.getVerseCount())
        assertEquals("Now the earth was formless and void.", b.getVerseDetails(1, 1, 2)?.second)
    }

    @Test
    fun `the book list can be read from a classpath module without loading its verses`() {
        val b = Bible().also { it.loadBooksOnly("bible-fixtures/classpath-sample.spb") }

        assertEquals(listOf("Genesis"), b.getBooks())
        assertEquals(0, b.getVerseCount(), "loadBooksOnly must not read past the header on this path either")
    }

    // ── Verse lookup ────────────────────────────────────────────────────────────

    @Test
    fun `a chapter returns its verses`() {
        val verses = bible().getChapterVerses(1, 1)
        assertEquals(3, verses.size)
        assertEquals(listOf(1, 2, 3), verses.map { it.verseNumber })
        assertTrue(verses.first().verseText.startsWith("In the beginning"))
    }

    @Test
    fun `a missing chapter returns an empty list rather than null`() {
        val b = bible()
        assertTrue(b.getChapterVerses(1, 99).isEmpty())
        assertTrue(b.getChapterVerses(66, 1).isEmpty())
    }

    @Test
    fun `verse counts per chapter are correct`() {
        val b = bible()
        assertEquals(3, b.getVerseCountForChapter(1, 1))
        assertEquals(1, b.getVerseCountForChapter(1, 2))
        assertEquals(2, b.getVerseCountForChapter(19, 23))
        assertEquals(0, b.getVerseCountForChapter(1, 99))
    }

    @Test
    fun `verse details return the book name, text and code`() {
        val details = assertNotNull(bible().getVerseDetails(43, 3, 16))
        val (bookName, text, code) = details
        assertEquals("John", bookName)
        assertEquals("For God so loved the world.", text)
        assertEquals("B043C003V016", code, "the canonical code is what crosses translations")
    }

    @Test
    fun `verse details are null for a verse that does not exist`() {
        val b = bible()
        assertNull(b.getVerseDetails(43, 3, 99), "verse past the end of the chapter")
        assertNull(b.getVerseDetails(43, 99, 1), "chapter past the end of the book")
        assertNull(b.getVerseDetails(66, 1, 1), "book not in this module")
    }

    @Test
    fun `the total verse count covers every book`() {
        assertEquals(8, bible().getVerseCount())
    }

    // ── Divergent numbering ─────────────────────────────────────────────────────

    @Test
    fun `display numbering is what chapter lookups use`() {
        // A Russian-style module where Psalm 23 is stored under the LXX display number 22 while
        // its canonical code stays at 23 — the UI must find it at 22.
        val content = SpbFixture.buildContent(
            title = "LXX-numbered",
            books = listOf(SpbFixture.Book(19, "Псалтирь", 1)),
            verses = listOf(
                SpbFixture.Verse(
                    book = 19, chapter = 22, verse = 1, text = "Господь — Пастырь мой",
                    codeBook = 19, codeChapter = 23, codeVerse = 1,
                ),
            ),
        )
        val b = SpbFixture.loadedBible(dir, content)

        assertEquals(1, b.getChapterVerses(19, 22).size, "the UI navigates by display numbering")
        assertTrue(b.getChapterVerses(19, 23).isEmpty(), "the code numbering is not a lookup key here")

        val (_, text, code) = assertNotNull(b.getVerseDetails(19, 22, 1))
        assertEquals("Господь — Пастырь мой", text)
        assertEquals("B019C023V001", code, "the code keeps the canonical chapter for cross-referencing")
    }

    // ── Content handling ────────────────────────────────────────────────────────

    @Test
    fun `non-ascii verse text survives loading`() {
        val content = SpbFixture.buildContent(
            title = "Русская Библия",
            books = listOf(SpbFixture.Book(43, "Иоанна", 1)),
            verses = listOf(SpbFixture.Verse(43, 3, 16, "Ибо так возлюбил Бог мир")),
        )
        val b = SpbFixture.loadedBible(dir, content)
        assertEquals("Ибо так возлюбил Бог мир", b.getVerseDetails(43, 3, 16)?.second)
    }

    @Test
    fun `verse text containing digits and punctuation is not truncated`() {
        // The line format is whitespace-delimited up to the text, so a verse starting with a
        // number must not be re-parsed as part of the reference.
        val content = SpbFixture.buildContent(
            title = "T",
            books = listOf(SpbFixture.Book(1, "Genesis", 1)),
            verses = listOf(SpbFixture.Verse(1, 5, 5, "930 years: and he died.")),
        )
        val b = SpbFixture.loadedBible(dir, content)
        assertEquals("930 years: and he died.", b.getVerseDetails(1, 5, 5)?.second)
    }

    @Test
    fun `metadata lines are not treated as content`() {
        val content = SpbFixture.buildContent(
            title = "T",
            books = listOf(SpbFixture.Book(1, "Genesis", 1)),
            verses = listOf(SpbFixture.Verse(1, 1, 1, "text")),
        )
        val b = SpbFixture.loadedBible(dir, content)
        assertEquals(1, b.getVerseCount(), "##Copyright and the ----- separator must not become verses")
        assertEquals(1, b.getBookCount())
    }

    @Test
    fun `a module with a header but no verses loads its books`() {
        val content = SpbFixture.buildContent(
            title = "Empty",
            books = listOf(SpbFixture.Book(1, "Genesis", 50)),
            verses = emptyList(),
        )
        val b = SpbFixture.loadedBible(dir, content)
        assertEquals(0, b.getVerseCount())
        assertEquals("Genesis", b.getBookName(1), "the book list still comes from the header")
    }

    @Test
    fun `reloading replaces the previous module rather than merging`() {
        val b = bible()
        assertEquals(8, b.getVerseCount())

        val other = SpbFixture.buildContent(
            title = "Other",
            books = listOf(SpbFixture.Book(1, "Genesis", 1)),
            verses = listOf(SpbFixture.Verse(1, 1, 1, "only verse")),
        )
        b.loadFromSpb(SpbFixture.spbFile(dir, name = "other.spb", content = other).absolutePath)

        assertEquals(1, b.getVerseCount(), "verses from the previous translation must not linger")
        assertEquals(1, b.getBookCount())
    }
}
