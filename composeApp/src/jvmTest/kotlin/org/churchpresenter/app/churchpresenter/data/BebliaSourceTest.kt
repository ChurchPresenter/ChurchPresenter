package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.BebliaSource.toBibleModule
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Installing a translation from the Holy Bible XML archive.
 *
 * The end-to-end path is the same shape as the other two — a body served over `MockEngine`, converted
 * by the real [converter.XmlToSpbConverter], written to a per-test temp directory, no mocks — but the
 * download is a bare XML file rather than an archive. That is the difference worth pinning: there is
 * nothing to unzip, so the install never reports [InstallPhase.EXTRACTING] and the parse owns the
 * slice of the progress bar the other sources spend extracting.
 *
 * `catalog()` is not driven end-to-end, because it always asks [BebliaCatalogIndex] for the real
 * archive at its default URL; the mapping from an already-fetched index to the browse list is checked
 * instead, exactly as [ZefaniaSourceTest] does.
 */
class BebliaSourceTest {

    private lateinit var dir: File
    private lateinit var targetDir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-beblia-source-test").toFile()
        targetDir = File(dir, "Bibles").apply { mkdirs() }
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    // --- fixtures ---

    private fun xmlBible(title: String = "English KJV") = """<?xml version="1.0" encoding="UTF-8"?>
        <bible translation="$title" status="Public Domain" link="https://example.invalid/x">
        <testament name="Old"><book number="1"><chapter number="1">
        <verse number="1">In the beginning God created the heaven and the earth.</verse>
        <verse number="2">And the earth was without form, and void.</verse>
        </chapter></book></testament>
        </bible>""".trimIndent()

    private fun httpServingBytes(body: ByteArray, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine {
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        },
    )

    private fun httpServing(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        httpServingBytes(body.toByteArray(Charsets.UTF_8), status)

    private fun httpFailing() = HttpClient(MockEngine { throw java.io.IOException("no route to host") })

    /** Answers with a promised length and then delivers none of it — the shape the Sentry report had. */
    private fun httpNeverDelivering(body: ByteArray) = HttpClient(
        MockEngine {
            respond(
                content = ByteReadChannel(ByteArray(0)),
                headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
            )
        },
    )

    /** The real git blob hash of [bytes], as the manifest publishes it. */
    private fun blobShaOf(bytes: ByteArray): String {
        val temp = File(dir, "blob-sha-scratch").apply { writeBytes(bytes) }
        return BibleInstallSupport.gitBlobSha1(temp).also { temp.delete() }
    }

    private fun module(
        checksum: String = "",
        sizeBytes: Long = 0,
        language: String = "ENG",
        copyright: String = "Public Domain",
    ) = BibleModule(
        sourceId = BibleSourceId.BEBLIA,
        downloadKey = "${"0".repeat(40)}/EnglishKJBible.xml",
        checksum = checksum,
        sizeBytes = sizeBytes,
        language = language,
        identifier = "KJ",
        displayName = "English KJV",
        copyright = copyright,
        fileStem = "ENG_KJ",
    )

    private fun install(
        http: HttpClient,
        module: BibleModule = module(),
        into: File = targetDir,
        onProgress: (InstallProgress) -> Unit = {},
    ) = runBlocking { BebliaSource.installBeblia(module, into, http, retryFloorMs = 0L, onProgress) }

    // --- catalogue mapping ---

    @Test
    fun `an index row becomes a browse row pinned to the manifest's commit`() {
        val commit = "a".repeat(40)
        val indexModule = BebliaCatalogIndex.Module(
            file = "EnglishKJBible.xml",
            blobSha = "abc",
            sizeBytes = 4_404_123,
            language = "ENG",
            languageName = "English",
            identifier = "KJ",
            displayName = "English KJV",
            copyright = "Public Domain",
            sourceUrl = "https://example.invalid/x",
            otBookCount = 39,
            ntBookCount = 27,
            fileStem = "ENG_KJ",
        )

        val row = with(BebliaSource) { indexModule.toBibleModule(commit) }

        assertEquals(BibleSourceId.BEBLIA, row.sourceId)
        assertEquals("$commit/EnglishKJBible.xml", row.downloadKey)
        assertEquals("abc", row.checksum)
        assertEquals("Public Domain", row.copyright, "this archive states a copyright before download")
        assertEquals(Testament.FULL, row.testament, "taken from the counts, not from the title")
        assertEquals("English", row.languageName)
    }

    @Test
    fun `the shared language table wins over the manifest's own name`() {
        val indexModule = BebliaCatalogIndex.Module(
            file = "x.xml", blobSha = "", sizeBytes = 0, language = "AFR",
            languageName = "Afrikaans, from the manifest", identifier = "", displayName = "x",
            copyright = "", sourceUrl = "", otBookCount = 0, ntBookCount = 0, fileStem = "AFR",
        )

        val row = with(BebliaSource) {
            indexModule.toBibleModule("c", mapOf("AFR" to LanguageNaming("Afrikaans")))
        }
        assertEquals("Afrikaans", row.languageName)
    }

    @Test
    fun `the manifest names the language where the shared table has never heard of it`() {
        val indexModule = BebliaCatalogIndex.Module(
            file = "x.xml", blobSha = "", sizeBytes = 0, language = "LUS",
            languageName = "Lushai", identifier = "", displayName = "x",
            copyright = "", sourceUrl = "", otBookCount = 0, ntBookCount = 0, fileStem = "LUS",
        )

        val row = with(BebliaSource) { indexModule.toBibleModule("c", emptyMap()) }
        assertEquals("Lushai", row.languageName, "a bare code would be the alternative")
    }

    @Test
    fun `book names are only localised for the languages the app has a table for`() {
        assertTrue(BebliaSource.hasLocalisedBookNames("eng"))
        assertTrue(BebliaSource.hasLocalisedBookNames("RUS"))
        assertFalse(BebliaSource.hasLocalisedBookNames("LUS"))
        assertFalse(BebliaSource.hasLocalisedBookNames(""))
    }

    // --- install ---

    @Test
    fun `a translation is downloaded, converted and installed`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)

        val outcome = install(
            httpServingBytes(body),
            module(checksum = blobShaOf(body), sizeBytes = body.size.toLong()),
        )

        val success = assertIs<BibleInstallOutcome.Success>(outcome)
        assertEquals("ENG_KJ.spb", success.file.name)
        assertEquals("English KJV", success.title)
        assertEquals(1, success.books)
        assertEquals("Public Domain", success.rights)

        val lines = success.file.readLines()
        assertTrue(lines.first().startsWith("##spDataVersion:"))
        assertTrue(lines.any { it == "##Copyright:\tPublic Domain" })
        assertTrue(lines.any { it.startsWith("B001C001V001\t") })
        assertTrue(lines.any { it.contains("In the beginning God created") })
    }

    @Test
    fun `the scratch directory is cleaned up afterwards`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)
        install(httpServingBytes(body), module(sizeBytes = body.size.toLong()))

        assertFalse(BibleInstallSupport.scratchIn(targetDir).exists())
    }

    @Test
    fun `a body whose hash is not the one the manifest published is refused`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)

        val outcome = install(
            httpServingBytes(body),
            module(checksum = "0".repeat(40), sizeBytes = body.size.toLong()),
        )

        assertIs<BibleInstallOutcome.ChecksumMismatch>(outcome)
        assertFalse(File(targetDir, "ENG_KJ.spb").exists(), "nothing is installed on a mismatch")
    }

    @Test
    fun `a body of the wrong length is refused before it is even parsed`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)
        val outcome = install(httpServingBytes(body), module(sizeBytes = body.size + 1L))
        assertIs<BibleInstallOutcome.ChecksumMismatch>(outcome)
    }

    @Test
    fun `a non-2xx answer is reported as an http error`() {
        val outcome = install(httpServing("not found", status = HttpStatusCode.NotFound))
        assertIs<BibleInstallOutcome.HttpError>(outcome).also { assertEquals(404, it.status) }
    }

    @Test
    fun `an unreachable host is reported as a network error`() {
        assertIs<BibleInstallOutcome.NetworkError>(install(httpFailing()))
    }

    @Test
    fun `a download that never gets going is reported as stalled, not as being offline`() {
        // The distinction the Retry button hangs off: this tab must answer it like the other two.
        val body = xmlBible().toByteArray(Charsets.UTF_8)

        val outcome = install(httpNeverDelivering(body), module(sizeBytes = body.size.toLong()))

        assertEquals(BibleInstallOutcome.DownloadStalled, outcome)
        assertTrue(targetDir.listFiles()!!.isEmpty(), "nothing is left behind")
    }

    @Test
    fun `a page that is not xml at all is reported as a damaged download`() {
        val outcome = install(httpServing("<html><body>Sign in to the WiFi</body>"))
        assertIs<BibleInstallOutcome.CorruptArchive>(outcome)
    }

    @Test
    fun `well-formed xml carrying no scripture is a conversion failure`() {
        val outcome = install(httpServing("""<?xml version="1.0"?><bible translation="Empty"/>"""))
        assertIs<BibleInstallOutcome.ConversionFailed>(outcome)
    }

    @Test
    fun `an unusable target directory is reported rather than silently retried`() {
        val notADirectory = File(dir, "a-file").apply { writeText("x") }
        assertIs<BibleInstallOutcome.NoDirectory>(install(httpServing(xmlBible()), into = notADirectory))
    }

    @Test
    fun `an existing module is replaced by a reinstall`() {
        val destination = File(targetDir, "ENG_KJ.spb").apply { writeText("##stale\n") }

        assertIs<BibleInstallOutcome.Success>(install(httpServing(xmlBible())))
        assertFalse(destination.readText().startsWith("##stale"))
    }

    // --- progress ---

    @Test
    fun `progress runs downloading then converting then installing, and never extracting`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)
        val seen = mutableListOf<InstallProgress>()

        assertIs<BibleInstallOutcome.Success>(
            install(httpServingBytes(body), module(sizeBytes = body.size.toLong())) { seen.add(it) }
        )

        assertFalse(
            seen.any { it.phase == InstallPhase.EXTRACTING },
            "there is no archive here, so nothing is ever extracted"
        )
        assertEquals(
            listOf(InstallPhase.DOWNLOADING, InstallPhase.CONVERTING, InstallPhase.INSTALLING),
            seen.map { it.phase }.distinct(),
            "the phases run in order and none repeats after the next has started"
        )
        assertEquals(seen.map { it.fraction }.sorted(), seen.map { it.fraction }, "the bar never rewinds")
        assertEquals(1f, seen.last().fraction)
    }

    @Test
    fun `converting spans the slice the other sources spend extracting`() {
        val body = xmlBible().toByteArray(Charsets.UTF_8)
        val seen = mutableListOf<InstallProgress>()
        install(httpServingBytes(body), module(sizeBytes = body.size.toLong())) { seen.add(it) }

        val converting = seen.filter { it.phase == InstallPhase.CONVERTING }
        assertTrue(converting.isNotEmpty())
        assertTrue(
            converting.all { it.fraction >= BibleInstallSupport.DOWNLOAD_END },
            "converting starts where the download ended, not at the extract boundary"
        )
        assertTrue(converting.any { it.fraction > BibleInstallSupport.PARSE_END }, "the write half is reported too")
        assertTrue(converting.all { it.fraction <= BibleInstallSupport.CONVERT_END })
    }
}
