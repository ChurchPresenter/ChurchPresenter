package org.churchpresenter.bible

import org.churchpresenter.diagnostics.CrashReportSweep
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a module that will not read reports about itself.
 *
 * Neither load path throws — a folder of translations is loaded together and one bad file must not
 * take the others with it — so [Bible.loadError] is the only thing that distinguishes "this module
 * is broken" from "this module is empty". Before it existed the two were indistinguishable, and an
 * operator whose `.spb` had been truncated saw a Bible tab with no books and no reason given.
 *
 * The failure that is actually reachable on demand is invalid UTF-8: both readers open the file
 * with a reporting decoder, so a byte sequence that is not valid UTF-8 throws out of the read
 * wherever the decoder reaches it. Where in the file that lands is what separates the two cases
 * below — a small file is decoded whole on the first buffer fill and yields nothing, while one with
 * enough valid text ahead of the damage yields that text and reports a partial read.
 */
class BibleLoadErrorTest {

    private lateinit var dir: File
    private val sweep = CrashReportSweep()

    @BeforeTest
    fun createDir() {
        sweep.mark()
        dir = Files.createTempDirectory("cp-bible-load-error").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
        sweep.sweep()
    }

    private val goodModule = SpbFixture.buildContent(
        title = "Good Bible",
        books = listOf(SpbFixture.Book(1, "Genesis", 1)),
        verses = listOf(SpbFixture.Verse(1, 1, 1, "In the beginning.")),
    )

    /** `0xC3 0x28` is a lead byte followed by something that cannot continue it. */
    private val invalidUtf8 = byteArrayOf(0xC3.toByte(), 0x28)

    @Test
    fun `a module that is not there says so instead of loading empty`() {
        val missing = File(dir, "does-not-exist.spb")
        val b = Bible()

        b.loadFromSpb(missing.absolutePath) // must not throw

        val error = assertNotNull(b.loadError, "a module that cannot be opened is a load failure")
        assertEquals("does-not-exist.spb", error.fileName)
        assertEquals(missing.absolutePath, error.resourcePath)
        assertFalse(error.partial, "nothing was read, so there is nothing to show as far as it goes")
        assertEquals(0, b.getBookCount())
        assertEquals(0, b.getVerseCount())
    }

    @Test
    fun `the book-list scan reports a module that is not there too`() {
        val b = Bible()

        b.loadBooksOnly(File(dir, "absent.spb").absolutePath) // must not throw

        assertNotNull(b.loadError, "the startup header scan must not fail silently either")
        assertTrue(b.getBooks().isEmpty())
    }

    @Test
    fun `a module that will not decode reports why`() {
        val broken = File(dir, "broken.spb").also {
            it.writeBytes("##Title: Broken\n1 Genesis 1\n-----\n".toByteArray() + invalidUtf8)
        }

        val b = Bible().also { it.loadFromSpb(broken.absolutePath) }

        val error = assertNotNull(b.loadError)
        assertEquals("broken.spb", error.fileName)
        assertTrue(error.reason.isNotBlank(), "the reason is the only thing that says what went wrong")
    }

    /**
     * The header is read on its own path, so it fails on its own — and on a file this small the
     * decoder reaches the damage while filling its first buffer, before the scan sees a single line.
     */
    @Test
    fun `the book-list scan reports a module that will not decode`() {
        val broken = File(dir, "broken.spb").also {
            it.writeBytes("##Title: Broken\n1 Genesis 1\n".toByteArray() + invalidUtf8)
        }

        val b = Bible().also { it.loadBooksOnly(broken.absolutePath) }

        assertNotNull(b.loadError)
        assertTrue(b.getBooks().isEmpty())
    }

    /**
     * A module that stops being readable partway through is shown as far as it goes.
     *
     * The damage has to sit past the reader's first buffer fill for any of the file to parse at
     * all, which is what the verse padding here is for: it is not decoration, it is the difference
     * between this case and the one above.
     */
    @Test
    fun `a module that stops being readable keeps what it read and says it is partial`() {
        val verses = (1..400).joinToString("\n") { v ->
            "B001C001V${v.toString().padStart(3, '0')} 1 1 $v " +
                "Verse $v, padded out so the readable part fills more than one decode buffer."
        }
        val truncated = File(dir, "truncated.spb").also {
            it.writeBytes(
                "##Title: Truncated\n1 Genesis 1\n-----\n$verses\n".toByteArray() + invalidUtf8,
            )
        }

        val b = Bible().also { it.loadFromSpb(truncated.absolutePath) }

        val error = assertNotNull(b.loadError)
        assertTrue(error.partial, "verses were read before the failure")
        assertTrue(b.getVerseCount() > 0, "what parsed is kept")
        assertEquals(listOf("Genesis"), b.getBooks(), "and its book list is still built")
        assertEquals("Verse 1, padded out so the readable part fills more than one decode buffer.",
            b.getVerseDetails(1, 1, 1)?.second)
    }

    @Test
    fun `a module that reads cleanly reports no error`() {
        val good = SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)

        val b = Bible().also { it.loadFromSpb(good.absolutePath) }

        assertNull(b.loadError)
        assertEquals(listOf("Genesis"), b.getBooks())
    }

    @Test
    fun `a later clean load clears the error left by a failed one`() {
        val good = SpbFixture.spbFile(dir, name = "good.spb", content = goodModule)
        val b = Bible()

        b.loadFromSpb(File(dir, "absent.spb").absolutePath)
        assertNotNull(b.loadError)

        b.loadFromSpb(good.absolutePath)

        assertNull(b.loadError, "the error must describe the most recent load, not an older one")
    }

    @Test
    fun `a malformed module that still decodes is empty rather than failed`() {
        val junk = File(dir, "junk.spb").also { it.writeText("this is not a bible module at all") }

        val b = Bible().also { it.loadFromSpb(junk.absolutePath) }

        assertEquals(0, b.getVerseCount())
        assertNull(b.loadError, "nothing failed — the file simply holds no verses to find")
    }

    @Test
    fun `a classpath module reports its resource name rather than a path`() {
        val b = Bible().also { it.loadFromSpb("bible-fixtures/no-such-resource.spb") }

        assertEquals("no-such-resource.spb", assertNotNull(b.loadError).fileName)
    }
}
