package org.churchpresenter.app.churchpresenter.data

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
 * Searching a Bible, and translating a reference between two Bibles that number verses differently.
 *
 * [BibleTest] covers loading a module and reading it back. This covers the two things built on top:
 * the search box, and the code↔display bridge.
 *
 * The bridge is the subtle one. A `.spb` module carries two numberings for every verse — a
 * canonical code (`BxxxCxxxVxxx`) and the module's own display numbering — and they genuinely
 * differ: the Russian Synodal Psalms follow the Septuagint, so what a Russian congregation calls
 * Psalm 22 an English one calls Psalm 23. Everything that has to name the same verse across two
 * Bibles goes through this bridge: the secondary-Bible column, a follower instance mirroring a
 * primary in another language, and the Bible engine's detections. Get it wrong and each side shows
 * a plausible verse that is not the one being read aloud.
 */
class BibleSearchAndNumberingTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-search-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    /** A module whose display numbering matches its codes — the ordinary case. */
    private fun plainBible(): Bible = SpbFixture.loadedBible(dir)

    /**
     * A module numbered like the Russian Synodal Psalms: the canonical code says Psalm 23 while the
     * module itself calls it Psalm 22, exactly as [SpbFixture] describes.
     */
    private fun renumberedBible(): Bible = Bible().also {
        it.loadFromSpb(
            SpbFixture.spbFile(
                dir,
                name = "synodal.spb",
                content = SpbFixture.buildContent(
                    title = "Synodal",
                    books = listOf(SpbFixture.Book(19, "Псалтирь", 2)),
                    verses = listOf(
                        // display 22:1 carries the canonical code for 23:1
                        SpbFixture.Verse(19, 22, 1, "Господь — Пастырь мой", codeChapter = 23),
                        SpbFixture.Verse(19, 22, 2, "Он покоит меня", codeChapter = 23, codeVerse = 2),
                    ),
                ),
            ).absolutePath
        )
    }

    private fun search(bible: Bible, word: String) =
        bible.searchBible(false, Regex(word, RegexOption.IGNORE_CASE))

    // ── Searching ───────────────────────────────────────────────────────────────

    @Test
    fun `a word is found wherever it appears`() {
        val results = search(plainBible(), "God")

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "god" in it.verseText.lowercase() }, results.map { it.verseText }.toString())
    }

    @Test
    fun `a result names the verse it came from`() {
        val result = search(plainBible(), "shepherd").single()

        assertEquals("Psalms", result.book)
        assertEquals("23", result.chapter)
        assertEquals("1", result.verse)
        assertTrue(
            result.verseText.startsWith("Psalms 23:1 "),
            "the result line carries its own reference so the list reads without a second lookup: ${result.verseText}",
        )
    }

    @Test
    fun `searching is case-insensitive when asked to be`() {
        assertEquals(
            search(plainBible(), "GOD").size,
            search(plainBible(), "god").size,
            "an operator types in whatever case is quickest",
        )
    }

    @Test
    fun `a word that appears nowhere finds nothing`() {
        assertTrue(search(plainBible(), "aardvark").isEmpty())
    }

    @Test
    fun `a search can be limited to one book`() {
        val bible = plainBible()

        val everywhere = search(bible, "the").size
        val psalmsOnly = bible.searchBible(false, Regex("the", RegexOption.IGNORE_CASE), book = 19)

        assertTrue(psalmsOnly.isNotEmpty())
        assertTrue(psalmsOnly.all { it.book == "Psalms" }, psalmsOnly.map { it.book }.toString())
        assertTrue(psalmsOnly.size < everywhere, "narrowing to a book must actually narrow it")
    }

    @Test
    fun `a search can be limited to one chapter`() {
        val bible = plainBible()

        val wholeBook = bible.searchBible(false, Regex("the", RegexOption.IGNORE_CASE), book = 1)
        val firstChapter = bible.searchBible(false, Regex("the", RegexOption.IGNORE_CASE), book = 1, chapter = 1)

        assertTrue(firstChapter.all { it.chapter == "1" })
        assertTrue(firstChapter.size <= wholeBook.size)
    }

    @Test
    fun `a book with no match returns nothing rather than falling back to the whole bible`() {
        val bible = plainBible()

        assertTrue(bible.searchBible(false, Regex("shepherd", RegexOption.IGNORE_CASE), book = 1).isEmpty())
    }

    @Test
    fun `requiring every word finds only verses carrying all of them`() {
        val bible = plainBible()
        val either = Regex("\\b(heaven|earth)\\b", RegexOption.IGNORE_CASE)

        val anyWord = bible.searchBible(false, either)
        val allWords = bible.searchBible(true, either)

        assertTrue(anyWord.size >= allWords.size, "requiring both words cannot match more verses")
        assertTrue(
            allWords.all { "heaven" in it.verseText.lowercase() && "earth" in it.verseText.lowercase() },
            allWords.map { it.verseText }.toString(),
        )
    }

    @Test
    fun `searching an empty bible finds nothing rather than failing`() {
        assertTrue(search(Bible(), "God").isEmpty())
    }

    // ── Reading a reference out as a code ───────────────────────────────────────

    @Test
    fun `a verse code is parsed into its three numbers`() {
        assertEquals(Triple(43, 3, 16), plainBible().parseVerseCode("B043C003V016"))
    }

    @Test
    fun `something that is not a verse code is refused`() {
        val bible = plainBible()

        assertNull(bible.parseVerseCode("John 3:16"))
        assertNull(bible.parseVerseCode("B43C3V16"), "the code is fixed-width; a short one is not a code")
        assertNull(bible.parseVerseCode(""))
    }

    @Test
    fun `a displayed reference can be read out as its canonical code`() {
        assertEquals(Triple(43, 3, 16), plainBible().getCodeReference(43, 3, 16))
    }

    @Test
    fun `a reference that is not in the bible has no code`() {
        assertNull(plainBible().getCodeReference(43, 99, 1))
    }

    // ── Looking a verse up by code ──────────────────────────────────────────────

    @Test
    fun `a code finds the verse in an ordinarily numbered bible`() {
        val found = assertNotNull(plainBible().getVerseDetailsByCode(43, 3, 16))

        assertEquals("John", found.bookName)
        assertEquals("For God so loved the world.", found.verseText)
        assertEquals(3, found.displayChapter)
        assertEquals(16, found.displayVerse)
    }

    @Test
    fun `a code that is in no bible finds nothing`() {
        assertNull(plainBible().getVerseDetailsByCode(43, 99, 99))
    }

    // ── The case the bridge exists for ──────────────────────────────────────────

    @Test
    fun `a renumbered bible reports its own numbering, not the incoming code`() {
        val synodal = renumberedBible()

        val found = assertNotNull(
            synodal.getVerseDetailsByCode(19, 23, 1),
            "the canonical code is Psalm 23:1, which this module stores as its own Psalm 22:1",
        )

        assertEquals("Господь — Пастырь мой", found.verseText, "it is the right verse")
        assertEquals(
            22,
            found.displayChapter,
            "and it must be shown as this Bible's own Psalm 22 — showing 23 would name a psalm the reader is not looking at",
        )
        assertEquals(1, found.displayVerse)
    }

    @Test
    fun `a renumbered bible reads its own reference out as the canonical code`() {
        val synodal = renumberedBible()

        assertEquals(
            Triple(19, 23, 1),
            synodal.getCodeReference(19, 22, 1),
            "its own Psalm 22:1 has to travel to another Bible as the canonical Psalm 23:1",
        )
    }

    @Test
    fun `a verse survives the round trip between two differently numbered bibles`() {
        // What the secondary column and a follower instance both do: read the reference out of one
        // Bible as a code, then look that code up in the other.
        val english = plainBible()
        val synodal = renumberedBible()

        val code = assertNotNull(english.getCodeReference(19, 23, 1), "Psalms 23:1 in the English module")
        val inSynodal = assertNotNull(synodal.getVerseDetailsByCode(code.first, code.second, code.third))

        assertEquals("Господь — Пастырь мой", inSynodal.verseText)
        assertEquals(22, inSynodal.displayChapter, "each side names the psalm the way its own readers do")
    }

    @Test
    fun `every verse of a renumbered chapter maps across`() {
        val synodal = renumberedBible()

        assertEquals("Он покоит меня", assertNotNull(synodal.getVerseDetailsByCode(19, 23, 2)).verseText)
        assertEquals(22, assertNotNull(synodal.getVerseDetailsByCode(19, 23, 2)).displayChapter)
    }

    /**
     * A module where the two numberings disagree about the VERSE, not just the chapter — Psalms
     * carrying a Hebrew superscription shift every verse by one. The exact code id is then absent
     * from the module, so the lookup falls back to translating the chapter and taking the verse
     * number as it stands. That fallback is what stops the secondary column going blank whenever
     * the primary sends a verse whose code this module never stored.
     */
    private fun shiftedBible(): Bible = Bible().also {
        it.loadFromSpb(
            SpbFixture.spbFile(
                dir,
                name = "shifted.spb",
                content = SpbFixture.buildContent(
                    title = "Shifted",
                    books = listOf(SpbFixture.Book(19, "Псалтирь", 1)),
                    verses = listOf(
                        // display 50:1 stores the code for 51:2 — no B019C051V001 exists anywhere
                        SpbFixture.Verse(19, 50, 1, "Помилуй меня, Боже", codeChapter = 51, codeVerse = 2),
                    ),
                ),
            ).absolutePath,
        )
    }

    @Test
    fun `a code with no exact match falls back to this bible's own chapter`() {
        val shifted = shiftedBible()

        val found = assertNotNull(
            shifted.getVerseDetailsByCode(19, 51, 1),
            "no verse carries this exact code, but the chapter it belongs to is known",
        )

        assertEquals("Помилуй меня, Боже", found.verseText)
        assertEquals(50, found.displayChapter, "the chapter is translated even though the verse number is not")
        assertEquals(1, found.displayVerse)
    }

    @Test
    fun `a code for a chapter this bible has never seen finds nothing`() {
        assertNull(
            shiftedBible().getVerseDetailsByCode(19, 99, 1),
            "guessing at an unmapped chapter would put a plausible wrong verse on screen",
        )
    }
}
