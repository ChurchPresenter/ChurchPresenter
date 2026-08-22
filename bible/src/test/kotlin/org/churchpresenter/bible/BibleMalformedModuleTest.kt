package org.churchpresenter.bible

import org.churchpresenter.diagnostics.CrashReportSweep
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
 * Modules that are readable but not what the format expects.
 *
 * [BibleLoadErrorTest] covers the file failing to *read* — invalid UTF-8, a missing file. This
 * covers the other half: the bytes arrive fine and the content is wrong. A `.spb` is plain text an
 * operator can and does edit by hand, so lines that nearly match the format reach the loader in the
 * field.
 *
 * The loader is deliberately forgiving of those: a line that does not match a header or a verse is
 * skipped rather than failing the module, because losing one mistyped line is better than losing
 * the translation. These tests pin that forgiveness, so a later "tightening" that starts throwing
 * has to argue with them first.
 */
class BibleMalformedModuleTest {

    private lateinit var dir: File
    private val sweep = CrashReportSweep()

    @BeforeTest
    fun createDir() {
        sweep.mark()
        dir = Files.createTempDirectory("cp-bible-malformed").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
        sweep.sweep()
    }

    private fun moduleOf(content: String, name: String = "handedited.spb"): String =
        File(dir, name).also { it.writeText(content, Charsets.UTF_8) }.absolutePath

    @Test
    fun `a header line that is not a header is skipped, and the rest of the module still loads`() {
        val b = Bible()
        b.loadFromSpb(
            moduleOf(
                """
                ##Title: Hand Edited
                1 Genesis chapters
                19 Psalms 2
                -----
                B019C023V001 19 23 1 The LORD is my shepherd.
                """.trimIndent()
            )
        )

        assertNull(b.loadError, "a skipped line is not a failure")
        assertEquals(listOf("Psalms"), b.getBooks(), "the unparseable header is simply absent")
        assertEquals(1, b.getVerseCount())
    }

    @Test
    fun `a verse line that is not a verse is skipped rather than failing the module`() {
        val b = Bible()
        b.loadFromSpb(
            moduleOf(
                """
                ##Title: Hand Edited
                1 Genesis 1
                -----
                B001C001V001 1 1 1 In the beginning.
                BxxxCyyyVzzz this line is not a verse
                B001C001V002 1 1 2 And the earth was without form.
                """.trimIndent()
            )
        )

        assertNull(b.loadError)
        assertEquals(2, b.getVerseCount(), "both real verses survive the junk between them")
    }

    @Test
    fun `a module with a header and no verses loads its books and reports nothing wrong`() {
        val b = Bible()
        b.loadFromSpb(
            moduleOf(
                """
                ##Title: Headers Only
                1 Genesis 50
                -----
                """.trimIndent()
            )
        )

        assertNull(b.loadError, "an empty module is not a broken one")
        assertEquals(listOf("Genesis"), b.getBooks())
        assertEquals(0, b.getVerseCount())
    }

    @Test
    fun `loadBooksOnly reads the books without their verses`() {
        val b = Bible()
        b.loadBooksOnly(SpbFixture.spbFile(dir).absolutePath)

        assertEquals(listOf("Genesis", "Psalms", "John"), b.getBooks())
        assertEquals(0, b.getVerseCount(), "loadBooksOnly must never populate verses")
        assertNull(b.loadError)
    }

    @Test
    fun `a directory in place of a module is reported, not thrown`() {
        val asDirectory = File(dir, "folder.spb").also { it.mkdirs() }
        val b = Bible()
        b.loadFromSpb(asDirectory.absolutePath)

        val error = assertNotNull(b.loadError, "a module that would not open must say so")
        assertEquals("folder.spb", error.fileName, "the operator recognises the file, not the path")
        assertEquals(0, b.getVerseCount())
    }

    @Test
    fun `loadBooksOnly reports a directory the same way`() {
        val asDirectory = File(dir, "alsofolder.spb").also { it.mkdirs() }
        val b = Bible()
        b.loadBooksOnly(asDirectory.absolutePath)

        assertNotNull(b.loadError)
        assertTrue(b.getBooks().isEmpty())
    }

    @Test
    fun `loading a second module clears the first one's error`() {
        val b = Bible()
        b.loadFromSpb(File(dir, "missing.spb").absolutePath)
        assertNotNull(b.loadError, "the missing module must report")

        b.loadFromSpb(SpbFixture.spbFile(dir, name = "good.spb").absolutePath)
        assertNull(b.loadError, "a good module must not inherit the previous failure")
        assertTrue(b.getVerseCount() > 0)
    }
}
