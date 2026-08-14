package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Installing a translation from the Zefania archive — the counterpart to [EBibleSourceTest].
 *
 * The end-to-end path is the same shape as eBible's: a real zip served over `MockEngine`, converted
 * by the real [converter.XmlToSpbConverter] and written to a per-test temp directory, no mocks
 * anywhere. Two things differ from eBible and get their own coverage here: the archive publishes no
 * checksum of its own, so integrity is verified against the git blob hash the tree listing carries
 * instead — and a module's zip can hold more than one `.xml` entry, so the largest one is trusted
 * over misc. sidecar files a module happens to ship.
 *
 * `catalog()` itself is not driven end-to-end: it always asks [ZefaniaRepositoryIndex] for the real
 * archive at its default URL, exactly as [EBibleSource.catalog] does for eBible, so the mapping from
 * an already-fetched [ZefaniaRepositoryIndex.Index] to the browse list is what is checked instead.
 */
class ZefaniaSourceTest {

    private lateinit var dir: File
    private lateinit var targetDir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-zefania-source-test").toFile()
        targetDir = File(dir, "Bibles").apply { mkdirs() }
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    // --- fixtures ---

    private fun xmlBible(bookName: String = "The First Book of Moses") = """<?xml version="1.0" encoding="utf-8"?>
        <XMLBIBLE biblename="A Conservative Version">
        <INFORMATION><title>A Conservative Version</title><identifier>ACV</identifier>
        <language>ENG</language><rights>public domain</rights>
        <source>https://example.invalid/x</source></INFORMATION>
        <BIBLEBOOK bnumber="1" bname="$bookName">
        <CHAPTER cnumber="1"><VERS vnumber="1">In the beginning God created the heavens and the earth.</VERS>
        <VERS vnumber="2">Now the earth was formless and void.</VERS></CHAPTER>
        </BIBLEBOOK>
        </XMLBIBLE>""".trimIndent()

    private fun emptyXmlBible() = """<?xml version="1.0" encoding="utf-8"?>
        <XMLBIBLE biblename="Empty">
        <INFORMATION><title>Empty</title><language>ENG</language></INFORMATION>
        </XMLBIBLE>""".trimIndent()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun httpServingBytes(body: ByteArray, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine {
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/zip"))
        },
    )

    private fun httpFailing() = HttpClient(MockEngine { throw java.io.IOException("no route to host") })

    /**
     * A server that stops part-way through the first answer and serves the tail when asked for it —
     * a throttled link, as far as the download can tell.
     */
    private fun httpStallingThenServing(body: ByteArray) = HttpClient(
        MockEngine { request ->
            val start = request.headers[HttpHeaders.Range]
                ?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            if (start == 0) {
                respond(
                    content = ByteReadChannel(body.copyOfRange(0, body.size / 2)),
                    headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
                )
            } else {
                respond(
                    content = ByteReadChannel(body.copyOfRange(start, body.size)),
                    status = HttpStatusCode.PartialContent,
                    headers = Headers.build {
                        append(HttpHeaders.ContentLength, (body.size - start).toString())
                        append(HttpHeaders.ContentRange, "bytes $start-${body.size - 1}/${body.size}")
                    },
                )
            }
        },
    )

    /** A server that promises the module and then delivers nothing, every time. */
    private fun httpNeverDelivering(body: ByteArray) = HttpClient(
        MockEngine {
            respond(
                content = ByteReadChannel(ByteArray(0)),
                headers = headersOf(HttpHeaders.ContentLength, body.size.toString()),
            )
        },
    )

    /** The real git blob hash of [bytes], as the tree listing would publish it. */
    private fun blobShaOf(bytes: ByteArray): String {
        val temp = File(dir, "blob-sha-scratch").apply { writeBytes(bytes) }
        return BibleInstallSupport.gitBlobSha1(temp).also { temp.delete() }
    }

    private fun module(
        checksum: String = "",
        sizeBytes: Long = 0,
        path: String = "zefania-sharp-sourceforge-backup/Bibles/ENG/A Conservative Version/" +
            "SF_2009-01-20_ENG_ACV_(A CONSERVATIVE VERSION).zip",
    ) = BibleModule(
        sourceId = BibleSourceId.ZEFANIA,
        downloadKey = path,
        checksum = checksum,
        sizeBytes = sizeBytes,
        language = "ENG",
        identifier = "ACV",
        displayName = "A Conservative Version",
        fileStem = "ENG_ACV",
    )

    private fun install(http: HttpClient, module: BibleModule = module()) = runBlocking {
        // Zero backoff: the retry is what is under test, not how long it waits before it.
        ZefaniaSource.installZefania(module, targetDir, http, retryFloorMs = 0L) {}
    }

    @Test
    fun `the source identifies itself as Zefania`() {
        assertEquals(BibleSourceId.ZEFANIA, ZefaniaSource.sourceId)
    }

    // --- installing, end to end ---

    @Test
    fun `a translation is downloaded, converted and installed under its derived name`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = assertIs<BibleInstallOutcome.Success>(install(httpServingBytes(bytes)))

        assertEquals(File(targetDir, "ENG_ACV.spb"), outcome.file)
        assertEquals(1, outcome.books)
        assertEquals("public domain", outcome.rights)

        val text = outcome.file.readText()
        assertTrue(text.startsWith("##spDataVersion:"))
        assertTrue(text.contains("##Title:\tA Conservative Version"))
        assertTrue(text.contains("##Copyright:\tpublic domain"), "the licence travels with the file")
        assertTrue(text.contains("1\tThe First Book of Moses\t1"), "the module's own book name is used for English")
        assertTrue(text.contains("B001C001V001\t1\t1\t1\tIn the beginning God created the heavens and the earth."))
    }

    @Test
    fun `a download that stops half way still installs, and the file is whole`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = assertIs<BibleInstallOutcome.Success>(
            install(
                httpStallingThenServing(bytes),
                module(checksum = blobShaOf(bytes), sizeBytes = bytes.size.toLong()),
            )
        )

        // The hash and the size are checked against the tree listing, so a spliced or short file
        // could not reach Success — this asserts the resumed download is byte-for-byte right.
        assertEquals(1, outcome.books)
    }

    @Test
    fun `a download that never gets going is reported as stalled, not as being offline`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = install(httpNeverDelivering(bytes), module(sizeBytes = bytes.size.toLong()))

        assertEquals(BibleInstallOutcome.DownloadStalled, outcome)
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a matching git blob hash is accepted`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = install(
            httpServingBytes(bytes),
            module(checksum = blobShaOf(bytes), sizeBytes = bytes.size.toLong())
        )

        assertIs<BibleInstallOutcome.Success>(outcome)
    }

    @Test
    fun `a git blob hash that does not match the archive is rejected`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = install(
            httpServingBytes(bytes),
            module(checksum = "0".repeat(40), sizeBytes = bytes.size.toLong())
        )

        assertEquals(BibleInstallOutcome.ChecksumMismatch, outcome)
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a size that does not match what the tree listing published is rejected`() {
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = install(httpServingBytes(bytes), module(sizeBytes = bytes.size.toLong() + 1))

        assertEquals(BibleInstallOutcome.ChecksumMismatch, outcome)
    }

    @Test
    fun `the largest xml entry in the archive is the one converted`() {
        // A module can ship more than one .xml file; only the scripture itself should be trusted.
        val bytes = zipOf("readme.xml" to "<note>see website</note>", "module.xml" to xmlBible())

        val outcome = assertIs<BibleInstallOutcome.Success>(install(httpServingBytes(bytes)))

        assertEquals(1, outcome.books)
    }

    @Test
    fun `an archive with no xml in it is rejected`() {
        val bytes = zipOf("readme.txt" to "nothing here")

        assertEquals(BibleInstallOutcome.CorruptArchive, install(httpServingBytes(bytes)))
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `xml with no books converts to nothing and is rejected`() {
        val bytes = zipOf("module.xml" to emptyXmlBible())

        assertEquals(BibleInstallOutcome.ConversionFailed, install(httpServingBytes(bytes)))
    }

    @Test
    fun `a book with no verses in it is also rejected`() {
        // A module can carry a book with no scripture in it — the empty-books check alone would
        // let this through, since the book list itself is not empty.
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <XMLBIBLE biblename="Hollow">
            <INFORMATION><title>Hollow</title><language>ENG</language></INFORMATION>
            <BIBLEBOOK bnumber="1" bname="Genesis"></BIBLEBOOK>
            </XMLBIBLE>""".trimIndent()
        val bytes = zipOf("module.xml" to xml)

        assertEquals(BibleInstallOutcome.ConversionFailed, install(httpServingBytes(bytes)))
    }

    @Test
    fun `a target directory that is actually a file is rejected up front`() {
        val fileWhereADirectoryIsExpected = File(dir, "not-a-directory").apply { writeText("occupied") }
        val bytes = zipOf("module.xml" to xmlBible())

        val outcome = runBlocking {
            ZefaniaSource.installZefania(module(), fileWhereADirectoryIsExpected, httpServingBytes(bytes)) {}
        }

        assertEquals(BibleInstallOutcome.NoDirectory, outcome)
    }

    @Test
    fun `content that is not xml at all is rejected rather than crashing`() {
        val bytes = zipOf("module.xml" to "not xml, just some garbage bytes")

        assertEquals(BibleInstallOutcome.ConversionFailed, install(httpServingBytes(bytes)))
    }

    @Test
    fun `a 404 page served instead of an archive is rejected`() {
        val body = "<!DOCTYPE html><title>404</title>".toByteArray()

        assertEquals(BibleInstallOutcome.CorruptArchive, install(httpServingBytes(body)))
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a server error is reported without writing anything`() {
        val outcome = install(httpServingBytes("nope".toByteArray(), status = HttpStatusCode.NotFound))

        assertEquals(BibleInstallOutcome.HttpError(404), outcome)
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `an unreachable host is a network error`() {
        assertEquals(BibleInstallOutcome.NetworkError, install(httpFailing()))
    }

    @Test
    fun `a failed install leaves an already-installed Bible byte-identical`() {
        val installed = File(targetDir, "ENG_ACV.spb")
        val original = "##spDataVersion:\t1\n##Title:\tthe one already in use\n"
        installed.writeText(original)

        install(httpServingBytes("<html>404</html>".toByteArray()))
        assertEquals(original, installed.readText(), "a bad download must not touch the live Bible")

        install(httpFailing())
        assertEquals(original, installed.readText(), "a dropped connection must not touch it either")
    }

    @Test
    fun `re-installing replaces the Bible rather than accumulating`() {
        val bytes = zipOf("module.xml" to xmlBible())
        install(httpServingBytes(bytes))
        install(httpServingBytes(bytes))

        assertEquals(listOf("ENG_ACV.spb"), targetDir.list()!!.sorted(), "no scratch files are left behind")
    }

    @Test
    fun `progress runs forward through every phase and ends complete`() {
        val bytes = zipOf("module.xml" to xmlBible())
        val reported = mutableListOf<InstallProgress>()

        val outcome = runBlocking {
            ZefaniaSource.installZefania(module(), targetDir, httpServingBytes(bytes)) { reported.add(it) }
        }

        assertIs<BibleInstallOutcome.Success>(outcome)
        val fractions = reported.map { it.fraction }
        assertEquals(1f, fractions.last(), "the bar must finish full")
        assertEquals(fractions.sorted(), fractions, "the bar must never go backwards")
        assertEquals(
            listOf(
                InstallPhase.DOWNLOADING,
                InstallPhase.EXTRACTING,
                InstallPhase.CONVERTING,
                InstallPhase.INSTALLING,
            ),
            reported.map { it.phase }.distinct(),
            "each phase should be announced, in order",
        )
    }

    // --- the catalogue mapping ---

    private fun indexModule(language: String) = ZefaniaRepositoryIndex.Module(
        path = "${ZefaniaRepositoryIndex.BIBLES_PREFIX}$language/X/SF_2009-01-20_${language}_ACV_(X).zip",
        blobSha = "abc123",
        sizeBytes = 4242,
        language = language,
        identifier = "ACV",
        displayName = "X",
        releaseDate = "2009-01-20",
        fileStem = "${language}_ACV",
    )

    @Test
    fun `a language name is attached from the lookup when the archive has one`() {
        // The archive names its folders by code alone, so this is the only place a Zefania module
        // can acquire a language name.
        val mapped = with(ZefaniaSource) {
            indexModule("GER").toBibleModule(mapOf("GER" to LanguageNaming("German", "Deutsch")))
        }

        assertEquals("German", mapped.languageName)
        assertEquals("Deutsch", mapped.languageNativeName)
        assertEquals("GER", mapped.language, "the code stays as the archive spelled it")
    }

    @Test
    fun `a curated code with no settled autonym leaves the native name blank`() {
        val mapped = with(ZefaniaSource) {
            indexModule("UND").toBibleModule(mapOf("UND" to LanguageNaming("Unknown")))
        }

        assertEquals("Unknown", mapped.languageName)
        assertEquals("", mapped.languageNativeName)
    }

    @Test
    fun `a language absent from the lookup keeps a blank name rather than a guess`() {
        val mapped = with(ZefaniaSource) {
            indexModule("XKX").toBibleModule(mapOf("GER" to LanguageNaming("German", "Deutsch")))
        }

        assertEquals("", mapped.languageName)
        assertEquals("", mapped.languageNativeName)
    }

    @Test
    fun `a listed module carries its identity and size into the browse list`() {
        val prefix = ZefaniaRepositoryIndex.BIBLES_PREFIX
        val tree = """{"truncated":false,"tree":[{"path":"$prefix""" +
            """ENG/A Conservative Version/SF_2009-01-20_ENG_ACV_(A CONSERVATIVE VERSION).zip",""" +
            """"type":"blob","sha":"abc123","size":4242}]}"""
        val http = HttpClient(MockEngine {
            respond(
                content = tree,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

        // ZefaniaSource.catalog() always asks ZefaniaRepositoryIndex for the real archive at its
        // default URL, so it cannot be pointed at this MockEngine directly. Populating the index's
        // process-wide in-memory cache first — through its own public fetch(), the same seam its
        // own tests use — lets catalog() read that cache instead of ever reaching the network, since
        // the cache check only compares timestamps and is not keyed by URL.
        ZefaniaRepositoryIndex.clearMemoryCache()
        runBlocking {
            ZefaniaRepositoryIndex.fetch(
                url = "https://zefania-index.invalid",
                http = http,
                cacheFile = File(dir, "seed-cache.json"),
                nowMillis = 5_000L,
            )
        }

        val outcome = runBlocking { ZefaniaSource.catalog(nowMillis = 5_000L) }

        val success = assertIs<BibleCatalogOutcome.Success>(outcome)
        val mapped = success.modules.single()
        assertEquals(BibleSourceId.ZEFANIA, mapped.sourceId)
        assertEquals("abc123", mapped.checksum)
        assertEquals(4242L, mapped.sizeBytes)
        assertEquals("ENG_ACV", mapped.fileStem)
        assertEquals("ACV", mapped.identifier)
        assertEquals("A CONSERVATIVE VERSION", mapped.displayName)

        ZefaniaRepositoryIndex.clearMemoryCache()
    }
}
