package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The older `.spb` layouts, and what the loader makes of a module that does not describe itself.
 *
 * [BibleTest] covers the current one-line-per-verse layout. Modules in the wild are older than
 * that: the original format put the verse code on a line of its own and the text on the lines
 * after it, so a verse can run over several lines and is only complete once the NEXT code appears
 * — which means the last verse of the file exists solely in a buffer until the reader runs out of
 * lines. Losing it is a one-verse error at the end of a translation that nobody notices until
 * somebody reads Revelation 22:21 aloud.
 *
 * These modules also carry less: no display numbering (the code is the numbering), sometimes no
 * book header at all, and sometimes a book in the text that the header never declared. What the
 * loader has to do then is produce a Bible that is still navigable — every book named and
 * selectable — rather than an empty list, which is what the app shows when a translation "fails to
 * load" with no error anywhere.
 */
class BibleLegacyModuleTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-legacy-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun bible(content: String, bookNames: List<String> = emptyList()): Bible {
        val file = File(dir, "legacy.spb").also { it.writeText(content, Charsets.UTF_8) }
        return Bible().also { it.loadFromSpb(file.absolutePath, bookNames) }
    }

    private fun code(book: Int, chapter: Int, verse: Int) = "B%03dC%03dV%03d".format(book, chapter, verse)

    // ── The code-on-its-own-line layout ─────────────────────────────────────────

    @Test
    fun `a verse whose text is on the following line is read`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            1 Genesis 1
            -----
            ${code(1, 1, 1)}
            In the beginning God created the heaven and the earth.
            ${code(1, 1, 2)}
            And the earth was without form, and void.
            """.trimIndent(),
        )

        assertEquals(2, b.getVerseCount())
        assertEquals(
            listOf("1. In the beginning God created the heaven and the earth.", "2. And the earth was without form, and void."),
            b.getChapter(1, 1).verses,
        )
    }

    @Test
    fun `the last verse in the file is not left in the buffer`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            1 Genesis 1
            -----
            ${code(1, 1, 1)}
            First verse.
            ${code(1, 1, 2)}
            The very last verse of the module.
            """.trimIndent(),
        )

        assertEquals(
            "2. The very last verse of the module.",
            b.getChapter(1, 1).verses.last(),
            "it is only flushed when the reader runs out of lines — the one verse most easily dropped",
        )
    }

    @Test
    fun `a verse running over several lines is joined into one`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            1 Genesis 1
            -----
            ${code(1, 1, 1)}
            In the beginning
            God created
            the heaven and the earth.
            """.trimIndent(),
        )

        val verse = b.getChapter(1, 1).verses.single()
        assertTrue("In the beginning" in verse, verse)
        assertTrue("the heaven and the earth." in verse, "a wrapped verse must not be truncated at the first line")
        assertEquals(1, b.getVerseCount(), "the continuation lines are one verse, not three")
    }

    @Test
    fun `the code is the numbering when there is no display reference`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            43 John 3
            -----
            ${code(43, 3, 16)}
            For God so loved the world.
            """.trimIndent(),
        )

        assertEquals(listOf("16. For God so loved the world."), b.getChapter(43, 3).verses)
        assertEquals("John", b.getVerseDetails(43, 3, 16)?.first)
    }

    @Test
    fun `text appearing before any code is ignored`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            1 Genesis 1
            -----
            some stray line belonging to no verse
            ${code(1, 1, 1)}
            In the beginning.
            """.trimIndent(),
        )

        assertEquals(1, b.getVerseCount(), "a line with no verse to attach to cannot become one")
        assertEquals(listOf("1. In the beginning."), b.getChapter(1, 1).verses)
    }

    @Test
    fun `a module with no separator line still loads its verses`() {
        val b = bible(
            """
            ##Title: Legacy Bible
            ${code(1, 1, 1)}
            In the beginning.
            """.trimIndent(),
        )

        assertEquals(1, b.getVerseCount(), "the first code line ends the header just as the separator does")
    }

    @Test
    fun `a separator appearing again later in the file is not read as a verse`() {
        // Some modules repeat the rule between books.
        val b = bible(
            """
            ##Title: Sectioned
            1 Genesis 1
            43 John 3
            -----
            B001C001V001 1 1 1 In the beginning.
            -----
            B043C003V016 43 3 16 For God so loved the world.
            """.trimIndent(),
        )

        assertEquals(2, b.getVerseCount(), "the second rule must not swallow the verse after it")
        assertEquals(listOf("16. For God so loved the world."), b.getChapter(43, 3).verses)
    }

    // ── Books the header never declared ─────────────────────────────────────────

    @Test
    fun `a book found only in the verses is still added`() {
        val b = bible(
            """
            ##Title: Partial Header
            1 Genesis 1
            -----
            B001C001V001 1 1 1 In the beginning.
            B043C003V016 43 3 16 For God so loved the world.
            """.trimIndent(),
        )

        assertEquals(2, b.getBookCount(), "an undeclared book would otherwise be unreachable in the book list")
        assertEquals(listOf("1", "43"), (0 until b.getBookCount()).map { b.getBookId(it).toString() })
    }

    @Test
    fun `an undeclared book is named from the list the app supplies`() {
        val names = listOf("Genesis", "Exodus", "Leviticus")
        val b = bible(
            """
            ##Title: Partial Header
            1 Genesis 1
            -----
            B001C001V001 1 1 1 In the beginning.
            B003C001V001 3 1 1 And the LORD called unto Moses.
            """.trimIndent(),
            bookNames = names,
        )

        assertEquals("Leviticus", b.getBookName(3), "the app's own book names stand in for the missing header line")
    }

    @Test
    fun `an undeclared book with no name available is still selectable`() {
        val b = bible(
            """
            ##Title: Partial Header
            1 Genesis 1
            -----
            B001C001V001 1 1 1 In the beginning.
            B003C001V001 3 1 1 And the LORD called unto Moses.
            """.trimIndent(),
        )

        assertEquals("Book 3", b.getBookName(3), "a blank row in the book list could not be clicked")
        assertEquals(1, b.getChapterCount(b.getDisplayIndexForBookId(3)))
    }

    @Test
    fun `declared books come first and in header order`() {
        val b = bible(
            """
            ##Title: Ordered
            43 John 1
            1 Genesis 1
            -----
            B043C003V016 43 3 16 For God so loved the world.
            B001C001V001 1 1 1 In the beginning.
            B019C023V001 19 23 1 The LORD is my shepherd.
            """.trimIndent(),
        )

        assertEquals(
            listOf("John", "Genesis", "Book 19"),
            (0 until b.getBookCount()).mapNotNull { b.getBookName(b.getBookId(it)) },
            "the module's own order is the order the operator picked, with strays appended",
        )
    }

    // ── Two verses sharing a number ─────────────────────────────────────────────

    @Test
    fun `a verse split across two lines of the module is shown as one verse`() {
        // Some modules store a long verse as two rows carrying the same display number.
        val b = bible(
            """
            ##Title: Split Verse
            19 Psalms 1
            -----
            B019C023V001 19 23 1 The LORD is my shepherd;
            B019C023V001 19 23 1 I shall not want.
            """.trimIndent(),
        )

        val chapter = b.getChapter(19, 23)
        assertEquals(
            listOf("1. The LORD is my shepherd; I shall not want."),
            chapter.verses,
            "two rows for verse 1 must read as one verse, not as '1.' twice",
        )
    }

    @Test
    fun `both halves of a split verse are reachable by id`() {
        val b = bible(
            """
            ##Title: Split Verse
            19 Psalms 1
            -----
            B019C023V001 19 23 1 The LORD is my shepherd;
            B019C023V001 19 23 1 I shall not want.
            """.trimIndent(),
        )

        assertEquals(
            listOf("B019C023V001,B019C023V001"),
            b.getChapter(19, 23).previewIds,
            "the merged row keeps both ids, so going live sends the whole verse rather than half of it",
        )
    }

    @Test
    fun `a split verse does not disturb the verses around it`() {
        val b = bible(
            """
            ##Title: Split Verse
            19 Psalms 1
            -----
            B019C023V001 19 23 1 The LORD is my shepherd;
            B019C023V001 19 23 1 I shall not want.
            B019C023V002 19 23 2 He maketh me to lie down.
            """.trimIndent(),
        )

        val chapter = b.getChapter(19, 23)
        assertEquals(2, chapter.verses.size)
        assertEquals("2. He maketh me to lie down.", chapter.verses.last())
    }

    /**
     * Documents a KNOWN GAP: `getVerseCountForChapter` counts stored rows, while `getChapter` merges
     * rows sharing a display number — so a chapter holding a split verse reports one more verse than
     * the list actually shows.
     *
     * The count is what callers use to bound a verse range, so the last number it offers has no row
     * to select in such a chapter. Only reachable in modules that split a verse across two rows. The
     * fix is to count distinct display numbers; this expectation then becomes the two the list shows.
     */
    @Test
    fun `a chapter with a split verse is counted a verse longer than it reads -- known gap`() {
        val b = bible(
            """
            ##Title: Split Verse
            19 Psalms 1
            -----
            B019C023V001 19 23 1 The LORD is my shepherd;
            B019C023V001 19 23 1 I shall not want.
            B019C023V002 19 23 2 He maketh me to lie down.
            """.trimIndent(),
        )

        assertEquals(2, b.getChapter(19, 23).verses.size, "what the operator sees")
        assertEquals(3, b.getVerseCountForChapter(19, 23), "current behaviour: the stored row count, not the shown one")
    }

    // ── Reading just the book list ──────────────────────────────────────────────

    @Test
    fun `the book list can be read without loading the verses`() {
        // The tab shows the book list first and loads the text behind it — on a 4 MB module the
        // difference is the difference between an instant list and a visible stall.
        val file = File(dir, "books.spb").also {
            it.writeText(
                """
                ##Title: Header Only
                1 Genesis 50
                19 Psalms 150
                43 John 21
                -----
                B001C001V001 1 1 1 In the beginning.
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }

        val b = Bible().also { it.loadBooksOnly(file.absolutePath) }

        assertEquals(listOf("Genesis", "Psalms", "John"), b.getBooks())
        assertEquals(150, b.getChapterCount(1), "the header's declared count is all there is to go on here")
        assertEquals(0, b.getVerseCount(), "no verse text is read on this path")
    }

    @Test
    fun `reading the book list of a module that is not there leaves it empty`() {
        val b = Bible().also { it.loadBooksOnly(File(dir, "absent.spb").absolutePath) }

        assertTrue(b.getBooks().isEmpty(), "a missing module must not throw on the startup path")
    }

    @Test
    fun `loading the verses afterwards replaces the header-only book list`() {
        val file = File(dir, "both.spb").also {
            it.writeText(
                """
                ##Title: Both
                1 Genesis 50
                -----
                B001C001V001 1 1 1 In the beginning.
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val b = Bible().also { it.loadBooksOnly(file.absolutePath) }
        assertEquals(50, b.getChapterCount(0))

        b.loadFromSpb(file.absolutePath)

        assertEquals(
            1,
            b.getChapterCount(0),
            "once the text is loaded the count is what is actually there, not what the header claimed",
        )
        assertEquals(listOf("Genesis"), b.getBooks(), "and the book is listed once, not twice")
    }

    // ── How the module names itself ─────────────────────────────────────────────

    @Test
    fun `a titled module is abbreviated from its title`() {
        val b = bible(
            """
            ##Title: King James Version
            1 Genesis 1
            -----
            B001C001V001 1 1 1 In the beginning.
            """.trimIndent(),
        )

        assertEquals("King James Version", b.getBibleTitle())
        assertEquals("KJV", b.getBibleAbbreviation(), "the abbreviation labels the verse on screen and in the schedule")
    }

    @Test
    fun `a module whose title is already an abbreviation keeps it`() {
        val b = bible(
            """
            ##Title: RST77
            1 Genesis 1
            -----
            B001C001V001 1 1 1 In the beginning.
            """.trimIndent(),
        )

        assertEquals("RST77", b.getBibleAbbreviation(), "abbreviating an abbreviation would give 'R'")
    }

    @Test
    fun `a module with no title falls back to its file name`() {
        val file = File(dir, "ru_RST77.spb").also {
            it.writeText("1 Genesis 1\n-----\nB001C001V001 1 1 1 In the beginning.", Charsets.UTF_8)
        }

        val b = Bible().also { it.loadFromSpb(file.absolutePath) }

        assertEquals("ru_RST77", b.getBibleTitle(), "the picker has to show something to tell two modules apart")
        assertEquals("ru_RST77", b.getBibleAbbreviation())
    }
}
