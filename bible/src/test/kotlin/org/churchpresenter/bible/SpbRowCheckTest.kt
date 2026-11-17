package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A verse row whose id has a stray digit is not shown at all, so [Bible] reports it instead.
 *
 * The rows are the ones a real Synodal file carried: 2 Corinthians 10:5 stored as `V0050`.
 */
class SpbRowCheckTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-rows").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun checked(vararg lines: String): SpbRowReport = checkSpbRows(lines.asSequence())

    @Test
    fun `a file of well formed rows is clean`() {
        val report = checked(
            "B047C010V004\t47\t10\t4\tOne",
            "B047C010V005\t47\t10\t5\tTwo",
        )
        assertTrue(report.isClean)
        assertSame(SpbRowReport.CLEAN, report)
    }

    @Test
    fun `a verse with a stray trailing digit is reported where it is`() {
        val report = checked(
            "##Title: x",
            "B047C010V004\t47\t10\t4\tOne",
            "B047C010V0050\t47\t10\t5\tTwo",
        )
        assertEquals(1, report.total)
        assertEquals(
            SpbRowIssue(lineNumber = 3, verseId = "B047C010V0050", book = 47, chapter = 10, verse = 5),
            report.examples.single(),
        )
    }

    @Test
    fun `a book or chapter with the wrong digit count is reported too`() {
        val report = checked(
            "B47C010V005\t47\t10\t5\tA",
            "B047C10V005\t47\t10\t5\tB",
        )
        assertEquals(2, report.total)
    }

    @Test
    fun `only the first fifty examples are kept but every row is counted`() {
        val rows = (1..60).map { "B001C001V${it.toString().padStart(3, '0')}0\t1\t1\t$it\tText" }
        val report = checkSpbRows(rows.asSequence())
        assertEquals(60, report.total)
        assertEquals(50, report.examples.size)
        assertEquals(1, report.examples.first().lineNumber)
    }

    @Test
    fun `the older format's code lines and wrapped text are not rows`() {
        val report = checked(
            "B001C001V001",
            "In the beginning",
            "B001C001V0010",
            "      30",
        )
        assertTrue(report.isClean)
    }

    @Test
    fun `a loaded module carries the report and a reload clears it`() {
        val broken = SpbFixture.buildContent(
            title = "T",
            books = listOf(SpbFixture.Book(47, "2 Corinthians", 13)),
            verses = listOf(SpbFixture.Verse(47, 10, 4, "Four")),
        ) + "B047C010V0050\t47\t10\t5\tFive\n"
        val bible = Bible()

        bible.loadFromSpb(SpbFixture.spbFile(dir, "broken.spb", broken).absolutePath)
        assertEquals(1, bible.rowReport.total)
        assertEquals(6, bible.rowReport.examples.single().lineNumber)

        bible.loadFromSpb(SpbFixture.spbFile(dir, "clean.spb").absolutePath)
        assertTrue(bible.rowReport.isClean)
    }

    @Test
    fun `the row with the stray digit is the one missing from the loaded module`() {
        val broken = SpbFixture.buildContent(
            title = "T",
            books = listOf(SpbFixture.Book(47, "2 Corinthians", 13)),
            verses = listOf(SpbFixture.Verse(47, 10, 4, "Four")),
        ) + "B047C010V0050\t47\t10\t5\tFive\n"
        val bible = SpbFixture.loadedBible(dir, broken)

        assertEquals(listOf("4. Four"), bible.getChapter(47, 10).verses)
        assertEquals(1, bible.rowReport.total)
    }
}
