package org.churchpresenter.app.churchpresenter.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turns the plain-text scripture references in a Planning Center plan item into real local Bible
 * verses. PCO never supplies verse text, so a reference that fails to parse silently becomes an
 * inert announcement slide instead of scripture — the failure mode is a blank-looking item, not an
 * error.
 *
 * A `Bible` is a final Kotlin class backed by SQLite; MockK fakes one with a small book table so
 * the parsing and resolution logic can be tested without a `.spb` file.
 */
class PlanningCenterScriptureDetectorTest {

    /**
     * The abbreviation fallback ([BibleBookAbbreviations.resolveBookId]) resolves its tables from
     * Compose string resources, which need a graphics environment — it throws `HeadlessException`
     * in a test JVM. Stubbing it keeps these tests on the detector's own logic, and lets the
     * abbreviation branch be exercised deliberately rather than by accident.
     */
    @BeforeTest
    fun stubAbbreviations() {
        mockkObject(BibleBookAbbreviations)
        coEvery { BibleBookAbbreviations.resolveBookId(any()) } returns null
        coEvery { BibleBookAbbreviations.resolveBookId("Ps") } returns 19
        coEvery { BibleBookAbbreviations.resolveBookId("Jn") } returns 43
    }

    @AfterTest
    fun unstubAbbreviations() {
        unmockkObject(BibleBookAbbreviations)
    }

    /** A stand-in Bible exposing three books, keyed the way the real one is. */
    private fun bible(
        books: Map<Int, String> = mapOf(19 to "Psalms", 43 to "John", 1 to "Genesis"),
        verses: Map<Pair<Int, Int>, List<BibleVerse>> = emptyMap(),
    ): Bible {
        val ids = books.keys.toList()
        return mockk<Bible>().also { b ->
            every { b.getBookCount() } returns ids.size
            ids.forEachIndexed { displayIndex, bookId ->
                every { b.getBookId(displayIndex) } returns bookId
            }
            books.forEach { (id, name) -> every { b.getBookName(id) } returns name }
            every { b.getBookName(match { it !in books.keys }) } returns null
            every { b.getChapterVerses(any(), any()) } answers {
                verses[firstArg<Int>() to secondArg<Int>()] ?: emptyList()
            }
        }
    }

    private fun detect(text: String, bible: Bible = bible()) =
        runBlocking { PlanningCenterScriptureDetector.detectReferences(text, bible) }

    // ── Reference parsing ───────────────────────────────────────────────────────

    @Test
    fun `a single verse reference is detected`() {
        val refs = detect("John 3:16")
        assertEquals(1, refs.size)
        with(refs.single()) {
            assertEquals(43, bookId)
            assertEquals("John", bookName)
            assertEquals(3, chapter)
            assertEquals(16, verseStart)
            assertEquals(16, verseEnd, "a single verse ends where it starts")
        }
    }

    @Test
    fun `a verse range is detected`() {
        val ref = detect("Psalms 23:1-6").single()
        assertEquals(19, ref.bookId)
        assertEquals(23, ref.chapter)
        assertEquals(1, ref.verseStart)
        assertEquals(6, ref.verseEnd)
    }

    @Test
    fun `a dot separator works as well as a colon`() {
        // European plan text often writes "Psalms 23.1".
        val ref = detect("Psalms 23.1").single()
        assertEquals(23, ref.chapter)
        assertEquals(1, ref.verseStart)
    }

    @Test
    fun `spacing around the separators is tolerated`() {
        for (text in listOf("John 3:16", "John 3 : 16", "John  3:16", "Psalms 23:1 - 6")) {
            assertTrue(detect(text).isNotEmpty(), "failed to parse \"$text\"")
        }
    }

    @Test
    fun `several references on one line are split on commas and semicolons`() {
        val refs = detect("John 3:16, Psalms 23:1-6; Genesis 1:1")
        assertEquals(listOf("John", "Psalms", "Genesis"), refs.map { it.bookName })
    }

    @Test
    fun `one reference per line is detected across lines`() {
        val refs = detect(
            """
            John 3:16
            Psalms 23:1-6

            Genesis 1:1
            """.trimIndent(),
        )
        assertEquals(3, refs.size, "blank lines must not break the run")
    }

    @Test
    fun `book names are matched case-insensitively`() {
        assertEquals("John", detect("JOHN 3:16").single().bookName)
        assertEquals("John", detect("john 3:16").single().bookName)
    }

    @Test
    fun `a trailing period after the book name is ignored`() {
        assertEquals("John", detect("John. 3:16").single().bookName)
    }

    // ── Rejection ───────────────────────────────────────────────────────────────

    @Test
    fun `empty and blank text yields nothing`() {
        assertTrue(detect("").isEmpty())
        assertTrue(detect("   \n  \n").isEmpty())
    }

    @Test
    fun `ordinary plan text is not mistaken for a reference`() {
        val refs = detect(
            """
            Welcome and announcements
            Offering
            Sermon: walking in the light
            """.trimIndent(),
        )
        assertTrue(refs.isEmpty(), "found $refs")
    }

    @Test
    fun `a book the loaded bible does not have is skipped`() {
        // The user's Bible is Russian, or simply doesn't contain this book name.
        assertTrue(detect("Habakkuk 3:2").isEmpty())
    }

    @Test
    fun `a reference with no book name is skipped`() {
        assertTrue(detect("3:16").isEmpty(), "a bare chapter:verse has nothing to anchor to")
    }

    @Test
    fun `unparseable references are skipped without dropping the good ones`() {
        val refs = detect("John 3:16, not a reference at all, Genesis 1:1")
        assertEquals(listOf("John", "Genesis"), refs.map { it.bookName })
    }

    // ── Abbreviations ───────────────────────────────────────────────────────────

    @Test
    fun `a common abbreviation resolves to the bible's own full book name`() {
        // PCO plan text is frequently typed in English abbreviations whatever the app language,
        // and the result must carry the name the LOADED bible uses, not the abbreviation.
        val ref = detect("Ps 23:1-6").single()
        assertEquals(19, ref.bookId)
        assertEquals("Psalms", ref.bookName)
    }

    @Test
    fun `a full book name wins over the abbreviation table`() {
        val ref = detect("John 3:16").single()
        assertEquals(43, ref.bookId)
        assertEquals("John", ref.bookName)
    }

    @Test
    fun `an abbreviation for a book the loaded bible lacks is skipped`() {
        val onlyJohn = bible(books = mapOf(43 to "John"))
        assertTrue(detect("Ps 23:1", onlyJohn).isEmpty(), "resolved id 19 is not in this bible")
    }

    // ── Verse resolution ────────────────────────────────────────────────────────

    @Test
    fun `resolving a single verse returns its text`() {
        val bible = bible(
            verses = mapOf(
                (43 to 3) to listOf(BibleVerse(book = 43, chapter = 3, verseNumber = 16, verseText = "For God so loved the world")),
            ),
        )
        val ref = detect("John 3:16", bible).single()
        val resolved = assertNotNull(PlanningCenterScriptureDetector.resolveVerses(ref, bible))

        assertEquals("For God so loved the world", resolved.verseText)
        assertEquals("John", resolved.bookName)
        assertEquals(16, resolved.verseNumber)
        assertEquals("", resolved.verseRange, "a single verse has no range")
        assertEquals("John 3:16", resolved.displayReference)
    }

    @Test
    fun `resolving a range joins the verses in order`() {
        val bible = bible(
            verses = mapOf(
                (19 to 23) to listOf(
                    BibleVerse(book = 19, chapter = 23, verseNumber = 2, verseText = "second"),
                    BibleVerse(book = 19, chapter = 23, verseNumber = 1, verseText = "first"),
                    BibleVerse(book = 19, chapter = 23, verseNumber = 3, verseText = "third"),
                ),
            ),
        )
        val ref = detect("Psalms 23:1-2", bible).single()
        val resolved = assertNotNull(PlanningCenterScriptureDetector.resolveVerses(ref, bible))

        assertEquals("first second", resolved.verseText, "verses are ordered regardless of storage order")
        assertEquals("1-2", resolved.verseRange)
        assertEquals("Psalms 23:1-2", resolved.displayReference)
    }

    @Test
    fun `a missing chapter resolves to null rather than an empty slide`() {
        val bible = bible() // no verses at all
        val ref = detect("John 3:16", bible).single()
        assertNull(PlanningCenterScriptureDetector.resolveVerses(ref, bible))
    }

    @Test
    fun `a verse number outside the chapter resolves to null`() {
        val bible = bible(
            verses = mapOf(
                (43 to 3) to listOf(BibleVerse(book = 43, chapter = 3, verseNumber = 1, verseText = "only verse one")),
            ),
        )
        val ref = detect("John 3:99", bible).single()
        assertNull(PlanningCenterScriptureDetector.resolveVerses(ref, bible))
    }

    @Test
    fun `a range that only partly exists resolves to the verses present`() {
        val bible = bible(
            verses = mapOf(
                (43 to 3) to listOf(
                    BibleVerse(book = 43, chapter = 3, verseNumber = 16, verseText = "sixteen"),
                    BibleVerse(book = 43, chapter = 3, verseNumber = 17, verseText = "seventeen"),
                ),
            ),
        )
        val ref = detect("John 3:16-20", bible).single()
        val resolved = assertNotNull(PlanningCenterScriptureDetector.resolveVerses(ref, bible))
        assertEquals("sixteen seventeen", resolved.verseText, "a truncated range still shows what exists")
    }
}
