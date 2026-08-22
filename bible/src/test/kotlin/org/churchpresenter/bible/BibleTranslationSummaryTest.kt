package org.churchpresenter.bible

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
 * [Bible.readTranslationSummary] — the cheap header-only read behind the projection settings list
 * and the translation-name lookup.
 *
 * It exists so a folder of a dozen modules can be listed without parsing tens of megabytes of verse
 * text, which means it has to decide where the header stops from the first few lines alone, and be
 * right about it on hand-made modules as well as converter output. Reading one line too far costs
 * seconds per module on a network share; stopping one line too early loses books and mislabels a
 * whole Bible as a New Testament.
 */
class BibleTranslationSummaryTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-summary-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun summaryOf(content: String, maxLines: Int = Bible.HEADER_SCAN_LINE_LIMIT) =
        Bible.readTranslationSummary(
            File(dir, "module.spb").apply { writeText(content, Charsets.UTF_8) }.absolutePath,
            maxLines = maxLines,
        )

    @Test
    fun `a whole Bible reports both testaments and its title`() {
        val summary = summaryOf(
            """
            ##Title: King James Version
            1 Genesis 50
            43 John 21
            -----
            B001C001V001 1 1 1 In the beginning
            """.trimIndent()
        )
        assertEquals("King James Version", summary?.title)
        assertTrue(summary!!.hasOldTestament)
        assertTrue(summary.hasNewTestament)
    }

    @Test
    fun `a New Testament only module reports no Old Testament`() {
        // Book ids 40-66 are the New Testament; nothing below 40 appears here.
        val summary = summaryOf(
            """
            ##Title: New Testament
            40 Matthew 28
            66 Revelation 22
            -----
            B040C001V001 40 1 1 The book of the generation
            """.trimIndent()
        )
        assertFalse(summary!!.hasOldTestament)
        assertTrue(summary.hasNewTestament)
    }

    @Test
    fun `an Old Testament only module reports no New Testament`() {
        val summary = summaryOf(
            """
            ##Title: Tanakh
            1 Genesis 50
            39 Malachi 4
            -----
            B001C001V001 1 1 1 In the beginning
            """.trimIndent()
        )
        assertTrue(summary!!.hasOldTestament)
        assertFalse(summary.hasNewTestament)
    }

    @Test
    fun `blank lines between header lines do not end the scan`() {
        // Hand-made modules space their book list out; a blank line is not the end of the header.
        val summary = summaryOf(
            """
            ##Title: Spaced Out

            1 Genesis 50

            43 John 21
            -----
            B001C001V001 1 1 1 In the beginning
            """.trimIndent()
        )
        assertEquals("Spaced Out", summary?.title)
        assertTrue(summary!!.hasOldTestament, "the book after the blank line still counts")
        assertTrue(summary.hasNewTestament)
    }

    @Test
    fun `a comment line that is not a book header is stepped over`() {
        val summary = summaryOf(
            """
            ##Title: Commented
            ##Copyright: public domain
            ##Chapter sign:
            1 Genesis 50
            -----
            B001C001V001 1 1 1 In the beginning
            """.trimIndent()
        )
        assertEquals("Commented", summary?.title)
        assertTrue(summary!!.hasOldTestament)
    }

    @Test
    fun `a line that is neither a comment nor a book header is ignored`() {
        // Not every non-`##` line in a hand-made header is a book row; anything the book-header
        // shape does not match must simply not count towards either testament.
        val summary = summaryOf(
            """
            ##Title: Noisy
            this line is not a book header at all
            43 John 21
            -----
            B043C001V001 43 1 1 In the beginning was the Word
            """.trimIndent()
        )
        assertFalse(summary!!.hasOldTestament)
        assertTrue(summary.hasNewTestament)
    }

    @Test
    fun `a module with no separator stops at the first verse line`() {
        // Legacy modules omit the `-----`; the scan has to end on the first `B...` code instead,
        // or it would read the entire verse body looking for a header that never ends.
        val summary = summaryOf(
            """
            ##Title: No Separator
            1 Genesis 50
            B001C001V001 1 1 1 In the beginning
            B001C001V002 1 1 2 And the earth was without form
            """.trimIndent()
        )
        assertEquals("No Separator", summary?.title)
        assertTrue(summary!!.hasOldTestament)
        assertFalse(summary.hasNewTestament, "no New Testament book was ever declared")
    }

    @Test
    fun `a title with a tab after the colon is trimmed the same as a space`() {
        val summary = summaryOf("##Title:\tTabbed\n1 Genesis 50\n-----\n")
        assertEquals("Tabbed", summary?.title)
    }

    @Test
    fun `a module with no title line reports none`() {
        val summary = summaryOf("1 Genesis 50\n-----\nB001C001V001 1 1 1 In the beginning")
        assertNull(summary?.title)
        assertTrue(summary!!.hasOldTestament)
    }

    @Test
    fun `a file that is not there has no summary at all`() {
        assertNull(Bible.readTranslationSummary(File(dir, "absent.spb").absolutePath))
    }

    @Test
    fun `the line budget stops the scan early`() {
        // What the title-only caller relies on: only the first few lines are read, so a book
        // declared past the budget is not seen.
        val summary = summaryOf(
            """
            ##Title: Budgeted
            ##Copyright: public domain
            43 John 21
            -----
            """.trimIndent(),
            maxLines = 2,
        )
        assertEquals("Budgeted", summary?.title)
        assertFalse(summary!!.hasNewTestament, "the book line is past the budget")
    }
}
