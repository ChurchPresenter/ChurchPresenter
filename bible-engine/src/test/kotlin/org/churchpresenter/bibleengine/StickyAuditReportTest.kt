package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.tools.Category
import org.churchpresenter.bibleengine.tools.StickyRow
import org.churchpresenter.bibleengine.tools.audit
import org.churchpresenter.bibleengine.tools.classify
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StickyAuditReportTest {

    private lateinit var dir: File
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("sticky-audit").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun run(path: String?) = audit(path, PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))

    private fun report() = out.toString("UTF-8")

    private fun row(newBook: Int?, transcript: String, prevBook: Int? = null) =
        StickyRow("t", prevBook, null, newBook, null, transcript, "")

    @Test
    fun `no path prints the usage and no report`() {
        run(null)

        assertContains(err.toString("UTF-8"), "Usage: stickyAudit")
        assertEquals("", report())
    }

    @Test
    fun `a missing file is reported and nothing else`() {
        run(File(dir, "absent.jsonl").absolutePath)

        assertContains(err.toString("UTF-8"), "File not found")
        assertEquals("", report())
    }

    @Test
    fun `a word reaching far past its book's stem is flagged as an over-extension`() {
        val v = classify(row(66, "он открывает нам истину"))

        assertEquals(Category.STEM_OVEREXTENSION, v.category, v.detail)
        assertContains(v.detail, "открывает")
    }

    @Test
    fun `an inflection a character past its stem is confident`() {
        assertEquals(Category.CONFIDENT, classify(row(66, "в откровенья сказано")).category)
    }

    @Test
    fun `a multi-word alias explains a jump outright`() {
        assertEquals(Category.CONFIDENT, classify(row(22, "the song of songs")).category)
    }

    @Test
    fun `the report counts every category and lists the rows worth reading`() {
        val log = File(dir, "sticky-log-S1.jsonl")
        log.writeText(
            listOf(
                """{"ts":"t1","prevBook":43,"prevChapter":3,"newBook":65,"newChapter":1,""" +
                    """"transcript":"друзья нас не поняли","translation":""}""",
                """{"ts":"t2","prevBook":19,"prevChapter":14,"newBook":19,"newChapter":null,""" +
                    """"transcript":"","translation":"and so"}""",
                """{"ts":"t3","prevBook":null,"newBook":25,"transcript":"их плач в ночь"}""",
                """{"ts":"t4","prevBook":1,"newBook":66,"transcript":"он открывает нам"}""",
                """{"ts":"t5","prevBook":1,"newBook":66,"transcript":"откровение"}""",
                """{"ts":"t6","prevBook":43,"prevChapter":1,"newBook":43,"newChapter":2,"transcript":"x"}""",
                "",
                "not json at all",
                "{}",
            ).joinToString("\n"),
            Charsets.UTF_8,
        )

        run(log.absolutePath)
        val text = report()

        assertContains(text, "=== sticky-audit sticky-log-S1.jsonl  jumps=7")
        assertContains(text, "unexplained=1 chapter-cleared=1 short-alias=1 stem-overext=1 confident=1 other=2")
        assertContains(text, "UNEXPLAINED (1)")
        assertContains(text, "CHAPTER-CLEARED SAME-BOOK (1)")
        assertContains(text, "SHORT ALIAS (1)")
        assertContains(text, "STEM OVER-EXTENSION (1)")
        assertContains(text, "CONFIDENT (1)")
        assertContains(text, "OTHER (2)")
        assertContains(text, "book 65 <- book 43  ts=t1")
        assertContains(text, "transcript: \"and so\"")
        assertTrue(err.toString("UTF-8").isEmpty())
    }

    @Test
    fun `a log of confident jumps only prints just the confident line`() {
        val log = File(dir, "sticky-log-S2.jsonl")
        log.writeText("""{"ts":"t","prevBook":1,"newBook":66,"transcript":"откровение"}""", Charsets.UTF_8)

        run(log.absolutePath)
        val text = report()

        assertContains(text, "jumps=1")
        assertContains(text, "CONFIDENT (1)")
        assertFalse(text.contains("UNEXPLAINED ("))
        assertFalse(text.contains("OTHER ("))
    }
}
