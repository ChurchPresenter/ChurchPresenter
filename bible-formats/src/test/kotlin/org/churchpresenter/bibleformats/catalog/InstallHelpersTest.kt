package org.churchpresenter.bibleformats.catalog

import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.file.Files
import javax.net.ssl.SSLException
import java.nio.channels.UnresolvedAddressException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The small decisions the install pipelines are built from: is this file a Bible module, what comes
 * out of an archive, how long to wait before retrying, and where the raw downloads are fetched from.
 *
 * Each is reached directly rather than through a download, because each is the part that decides
 * something. The pipelines around them are covered by the per-source suites, which drive a
 * `MockEngine`; nothing here touches a network or a clock.
 */
class InstallHelpersTest {

    private lateinit var dir: File
    private val restore = mutableMapOf<String, String?>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-install-helpers-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        restore.forEach { (k, v) -> if (v == null) System.clearProperty(k) else System.setProperty(k, v) }
        restore.clear()
        dir.deleteRecursively()
    }

    private fun setProperty(key: String, value: String?) {
        restore.putIfAbsent(key, System.getProperty(key))
        if (value == null) System.clearProperty(key) else System.setProperty(key, value)
    }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = File(dir, "archive.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return zip
    }

    // ── looksLikeModule ─────────────────────────────────────────────────────────

    /**
     * `.spb` modules open with a `##Title:` header. The check reads only the first line, because it
     * runs on every candidate an archive yields and those can be tens of megabytes.
     */
    @Test
    fun `a file whose first line is a module header looks like a module`() {
        val module = File(dir, "kjv.spb").apply { writeText("##Title: KJV\n1 Genesis 50\n") }

        assertTrue(BibleInstallSupport.looksLikeModule(module))
    }

    @Test
    fun `a file that opens with anything else does not`() {
        val readme = File(dir, "README.txt").apply { writeText("This archive contains a Bible.\n") }

        assertFalse(BibleInstallSupport.looksLikeModule(readme))
    }

    @Test
    fun `an empty file does not look like a module rather than throwing`() {
        assertFalse(BibleInstallSupport.looksLikeModule(File(dir, "empty.spb").apply { createNewFile() }))
    }

    /** A path that is not there at all is the same answer, not an exception out of the installer. */
    @Test
    fun `a file that does not exist does not look like a module`() {
        assertFalse(BibleInstallSupport.looksLikeModule(File(dir, "absent.spb")))
    }

    // ── extractEntries ──────────────────────────────────────────────────────────

    @Test
    fun `only the wanted entries come out of the archive`() {
        val zip = zipOf("bible.spb" to "##Title: KJV", "readme.txt" to "ignore me")

        val extracted = BibleInstallSupport.extractEntries(zip, dir) { it.endsWith(".spb") }

        assertEquals(setOf("bible.spb"), extracted.keys)
        assertEquals("##Title: KJV", extracted.getValue("bible.spb").readText())
    }

    @Test
    fun `an entry nested in folders is matched on its file name`() {
        val zip = zipOf("modules/en/kjv.spb" to "##Title: KJV")

        val extracted = BibleInstallSupport.extractEntries(zip, dir) { it == "kjv.spb" }

        assertEquals(setOf("kjv.spb"), extracted.keys)
    }

    @Test
    fun `an archive with nothing wanted in it yields nothing`() {
        val zip = zipOf("readme.txt" to "no modules here")

        assertTrue(BibleInstallSupport.extractEntries(zip, dir) { it.endsWith(".spb") }.isEmpty())
    }

    /** A file that is not a zip at all is an empty result, not a thrown ZipException. */
    @Test
    fun `a file that is not an archive yields nothing`() {
        val notAZip = File(dir, "corrupt.zip").apply { writeText("this is not a zip") }

        assertTrue(BibleInstallSupport.extractEntries(notAZip, dir) { true }.isEmpty())
    }

    // ── downloadRetryDelayMs ────────────────────────────────────────────────────

    /**
     * The backoff doubles per attempt and is jittered, so what is pinned is the envelope: never
     * below the floor it was given, never above the ceiling, and growing with the attempt.
     */
    @Test
    fun `the retry delay never drops below its floor`() {
        repeat(6) { attempt ->
            val delay = BibleInstallSupport.downloadRetryDelayMs(attempt, floorMs = 100)
            assertTrue(delay >= 100, "attempt $attempt gave ${delay}ms, under the 100ms floor")
        }
    }

    @Test
    fun `the retry delay grows with the attempt and then stops growing`() {
        val first = BibleInstallSupport.downloadRetryDelayMs(0, floorMs = 100)
        val later = BibleInstallSupport.downloadRetryDelayMs(4, floorMs = 100)

        assertTrue(later > first, "a later attempt should wait longer: ${first}ms then ${later}ms")
        val past = BibleInstallSupport.downloadRetryDelayMs(9, floorMs = 100)
        assertTrue(past <= later * 2, "the shift is clamped, so attempt 9 cannot run away: ${past}ms")
    }

    /** Called without a floor it uses the production default — the shape every caller but a test gets. */
    @Test
    fun `the default floor applies when none is given`() {
        assertTrue(BibleInstallSupport.downloadRetryDelayMs(0) >= 1)
    }

    // ── rawBase ─────────────────────────────────────────────────────────────────

    @Test
    fun `raw downloads come from the archive's own host by default`() {
        setProperty("churchpresenter.bebliaRawBase", null)

        assertEquals(
            "https://raw.githubusercontent.com/${BebliaCatalogIndex.OWNER}/${BebliaCatalogIndex.REPO}",
            BebliaCatalogIndex.rawBase(),
        )
    }

    /**
     * The override exists so a test — or a church behind a mirror — can point the downloads
     * somewhere else without touching the catalogue URL, which is a separate property.
     */
    @Test
    fun `an override redirects the raw downloads`() {
        setProperty("churchpresenter.bebliaRawBase", "https://mirror.invalid/raw")

        assertEquals("https://mirror.invalid/raw", BebliaCatalogIndex.rawBase())
        assertTrue(BebliaCatalogIndex.rawUrlFor("abc", "KJV.xml").startsWith("https://mirror.invalid/raw/abc/"))
    }

    @Test
    fun `a blank override falls back rather than producing an empty host`() {
        setProperty("churchpresenter.bebliaRawBase", "   ")

        assertTrue(BebliaCatalogIndex.rawBase().startsWith("https://raw.githubusercontent.com/"))
    }

    // ── Which failures are the operator's own network or disk ───────────────────

    private fun isEnvironment(e: Throwable): Boolean =
        with(BibleInstallSupport) { e.isOperatorEnvironment() }

    @Test
    fun `a network that never resolved or connected is the operator's, not a defect`() {
        // A DNS failure and a full disk had become separate Sentry issues that both meant "a Bible
        // download did not finish" — a fact about the church's network, not about the app.
        assertTrue(isEnvironment(UnresolvedAddressException()))
        assertTrue(isEnvironment(ConnectException("refused")))
        assertTrue(isEnvironment(SocketTimeoutException("timed out")))
        assertTrue(isEnvironment(SSLException("bad certificate")))
    }

    @Test
    fun `a disk with no room left is the operator's too`() {
        assertTrue(
            isEnvironment(
                BibleInstallSupport.InsufficientDiskSpaceException(0, IOException("write failed"))
            )
        )
    }

    @Test
    fun `a connection the server closed mid-response is the operator's`() {
        // ktor CIO raises EOFException("...the server prematurely closed the connection") when the
        // bytes stop arriving. Reported once from a church whose network does that to
        // raw.githubusercontent.com; it is TruncatedBodyException's fact, one layer lower.
        assertTrue(isEnvironment(java.io.EOFException("the server prematurely closed the connection")))
    }

    @Test
    fun `a cancelled download is the user changing their mind, not a fault`() {
        // Closing the download browser cancels whatever it started, and ktor delivers that to the
        // request's own catch as an IOException over a channel closed underneath it — which is how
        // "eBible catalogue fetch failed" got filed against someone who had merely closed a dialog.
        assertTrue(isEnvironment(CancellationException("Job was cancelled")))
        assertTrue(isEnvironment(IOException("channel closed", CancellationException("Job was cancelled"))))
    }

    @Test
    fun `something this code got wrong is still reported`() {
        assertFalse(isEnvironment(IllegalStateException("a bug in the unpacker")))
        assertFalse(isEnvironment(IOException("a read that failed for no stated reason")))
    }

    @Test
    fun `a wrapped cause is found, because ktor wraps`() {
        val wrapped = IOException("request failed", UnresolvedAddressException())

        assertTrue(isEnvironment(wrapped))
    }

    @Test
    fun `the cause chain is not walked for ever`() {
        // Matches isStall's bound: deep enough for ktor's wrapping, shallow enough to stay cheap.
        var deep: Throwable = UnresolvedAddressException()
        repeat(8) { deep = IOException("layer", deep) }

        assertFalse(isEnvironment(deep), "a cause buried past the bound is not searched for")
    }

}
