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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Listing eBible.org's catalogue and installing a translation from it.
 *
 * The licensing filter is the part that matters most. eBible catalogues translations it is *not*
 * permitted to hand out alongside those it is, and the difference is a column in the CSV — so a row
 * that isn't marked redistributable must never reach the browse list, or the app would be offering
 * to distribute something nobody granted it the right to. The same catalogue is also where the
 * copyright text comes from, which is why eBible rows can state their licence before anything is
 * downloaded while Zefania rows cannot.
 *
 * Parsing that CSV by splitting on commas would not survive contact with it: copyright strings
 * routinely contain commas and quotes, so the parser is exercised on exactly that.
 *
 * The install half is the end-to-end path — a real zip holding real USFX, served over `MockEngine`,
 * converted and written to a per-test temp directory with no mocks anywhere.
 */
class EBibleSourceTest {

    private lateinit var dir: File
    private lateinit var targetDir: File
    private lateinit var cacheFile: File

    private val url = "https://ebible.invalid/translations.csv"

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-ebible-test").toFile()
        targetDir = File(dir, "Bibles").apply { mkdirs() }
        cacheFile = File(dir, "cache/ebible-catalog.csv")
        EBibleSource.clearMemoryCache()
    }

    @AfterTest
    fun cleanUp() {
        EBibleSource.clearMemoryCache()
        dir.deleteRecursively()
    }

    private val header = "languageCode,translationId,shortTitle,title,Copyright,Redistributable,downloadable,UpdateDate"

    private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\n")

    private fun httpServing(body: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine { respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "text/csv")) },
    )

    private fun httpServingBytes(body: ByteArray, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine {
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/zip"))
        },
    )

    private fun httpFailing() = HttpClient(MockEngine { throw java.io.IOException("no route to host") })

    private fun fetch(http: HttpClient, now: Long = 1_000L) = runBlocking {
        EBibleSource.fetchCatalog(url = url, http = http, cacheFile = cacheFile, nowMillis = now)
    }

    private fun modulesOf(body: String) = EBibleSource.parseCatalog(body)

    // --- language names ---

    /** The real catalogue publishes both an English name and the language's own name for itself. */
    private val namedHeader =
        "languageCode,translationId,languageName,languageNameInEnglish,shortTitle,title,Copyright,Redistributable,downloadable,UpdateDate"

    private fun namedCsv(vararg rows: String) = (listOf(namedHeader) + rows).joinToString("\n")

    @Test
    fun `the English language name is read off the catalogue`() {
        val modules = modulesOf(namedCsv("deu,luther,Deutsch,German,Luther,Luther Bible,PD,True,True,2020-01-01"))

        assertEquals("German", modules.single().languageName)
        assertEquals("Deutsch", modules.single().languageNativeName)
    }

    @Test
    fun `the autonym is kept even when an English name is published too`() {
        // Both spellings are wanted: "русский" is the only one a Russian speaker would type, and
        // this row publishes an English name, so a fallback would never reach it.
        val modules = modulesOf(namedCsv("rus,synodal,русский,Russian,Synodal,Synodal Bible,PD,True,True,2020-01-01"))

        assertEquals("Russian", modules.single().languageName)
        assertEquals("русский", modules.single().languageNativeName)
    }

    @Test
    fun `a missing English name falls back to the language's own name for itself`() {
        val modules = modulesOf(namedCsv("aai,aaiNT,Miniafia,,Miniafia NT,Miniafia,PD,True,True,2020-01-01"))

        // Both fields end up the same, which is what collapses the label back to a single name.
        assertEquals("Miniafia", modules.single().languageName)
        assertEquals("Miniafia", modules.single().languageNativeName)
    }

    @Test
    fun `a catalogue with no name columns still parses, just without names`() {
        // The columns are deliberately outside the required-column guard: a catalogue that stopped
        // publishing them should cost the names, not the whole list.
        val modules = modulesOf(csv("eng,acv,ACV,A Conservative Version,PD,True,True,2020-01-01"))

        assertEquals(1, modules.size)
        assertEquals("", modules.single().languageName)
        assertEquals("", modules.single().languageNativeName)
        assertEquals("ENG", modules.single().language, "everything else still parses")
    }

    @Test
    fun `the published book counts are read so the testament need not be guessed`() {
        val header = "languageCode,translationId,shortTitle,OTbooks,NTbooks,Copyright,Redistributable,downloadable,UpdateDate"
        val body = listOf(
            header,
            "ach,achNT,New Testament in Achi,0,27,PD,True,True,2020-01-01",
            "eng,acv,A Conservative Version,39,27,PD,True,True,2020-01-01",
        ).joinToString("\n")

        val modules = modulesOf(body).associateBy { it.identifier }

        // The name spells the words out, so the old name-only rule called this a whole Bible.
        assertEquals(Testament.NEW, modules.getValue("achNT").testament)
        assertEquals(Testament.FULL, modules.getValue("acv").testament)
    }

    @Test
    fun `a catalogue with no book counts falls back to reading the name`() {
        val modules = modulesOf(csv("eng,kjvNT,KJV NT,King James NT,PD,True,True,2020-01-01"))

        assertEquals(0, modules.single().ntBookCount)
        assertEquals(Testament.NEW, modules.single().testament)
    }

    @Test
    fun `the cached lookup carries both spellings for the Zefania tab to borrow`() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(namedCsv("rus,synodal,русский,Russian,Synodal,Synodal Bible,PD,True,True,2020-01-01"))

        val names = runBlocking { EBibleSource.cachedLanguageNames(cacheFile) }

        assertEquals(LanguageNaming("Russian", "русский"), names["RUS"])
    }

    @Test
    fun `the source identifies itself as eBible`() {
        assertEquals(BibleSourceId.EBIBLE, EBibleSource.sourceId)
    }

    // --- the staging override ---

    private val stagingProperty = "churchpresenter.ebibleCatalogUrl"

    /** [System] properties are process-wide, so every test that touches this one restores it. */
    private fun withStagingProperty(value: String?, block: () -> Unit) {
        val original = System.getProperty(stagingProperty)
        try {
            if (value == null) System.clearProperty(stagingProperty) else System.setProperty(stagingProperty, value)
            block()
        } finally {
            if (original == null) System.clearProperty(stagingProperty) else System.setProperty(stagingProperty, original)
        }
    }

    @Test
    fun `the default catalogue url is used when no staging override is set`() {
        withStagingProperty(null) {
            assertEquals("https://ebible.org/Scriptures/translations.csv", EBibleSource.catalogUrl())
        }
    }

    @Test
    fun `a staging override replaces the default catalogue url`() {
        withStagingProperty("https://staging.invalid/translations.csv") {
            assertEquals("https://staging.invalid/translations.csv", EBibleSource.catalogUrl())
        }
    }

    @Test
    fun `a blank staging override is treated as unset`() {
        withStagingProperty("") {
            assertEquals("https://ebible.org/Scriptures/translations.csv", EBibleSource.catalogUrl())
        }
    }

    // --- the licensing filter ---

    @Test
    fun `only translations marked redistributable are offered`() {
        val body = csv(
            "eng,engbsb,Berean Standard Bible,,public domain,True,True,2024-01-01",
            "eng,engniv,New International Version,,Copyright Biblica,False,True,2024-01-01",
        )

        val modules = modulesOf(body)

        assertEquals(listOf("engbsb"), modules.map { it.identifier })
        assertTrue(
            modules.none { it.displayName.contains("International") },
            "a translation the archive may not hand out must never reach the list",
        )
    }

    @Test
    fun `a translation that is not downloadable is left out`() {
        val body = csv("eng,engxyz,Some Bible,,public domain,True,False,2024-01-01")

        assertTrue(modulesOf(body).isEmpty())
    }

    @Test
    fun `the copyright is available before anything is downloaded`() {
        val body = csv("eng,engbsb,Berean Standard Bible,,public domain,True,True,2024-01-01")

        assertEquals("public domain", modulesOf(body).single().copyright)
    }

    // --- CSV handling ---

    @Test
    fun `a CRLF line ending is treated as a single line break`() {
        // The catalogue is fetched over HTTP, not authored by hand, so a Windows-exported CSV
        // reaching this parser is a real possibility, not a hypothetical.
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\r\n1,2\r\n"))
    }

    @Test
    fun `a bare carriage return is also treated as a line break`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\r1,2\r"))
    }

    @Test
    fun `a blank line does not become an empty row`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\n\n1,2\n"))
    }

    @Test
    fun `a copyright containing commas and quotes survives parsing`() {
        // A quoted field, with commas inside it and an escaped quote — all of which appear in the
        // real catalogue, and any of which would derail a split on commas.
        val quoted = "\"Copyright © 1996-2016, Biblical Studies Press, L.L.C. \"\"NET Bible\"\"\""
        val body = csv("eng,engnet,NET Bible,,$quoted,True,True,2016-01-01")

        val module = modulesOf(body).single()

        assertEquals("engnet", module.identifier)
        assertEquals("Copyright © 1996-2016, Biblical Studies Press, L.L.C. \"NET Bible\"", module.copyright)
    }

    @Test
    fun `a byte order mark on the header does not hide the columns`() {
        // eBible serves this file with a BOM; without stripping it the first column is unreadable.
        val body = "﻿" + csv("eng,engbsb,Berean Standard Bible,,public domain,True,True,2024-01-01")

        assertEquals(1, modulesOf(body).size)
    }

    @Test
    fun `rows shorter than the header do not derail the parse`() {
        val body = csv(
            "eng,engbsb,Berean Standard Bible,,public domain,True,True,2024-01-01",
            "eng,engtrunc",
            "rus,russyn,Russian Synodal,,public domain,True,True,2024-01-01",
        )

        assertEquals(listOf("engbsb", "russyn"), modulesOf(body).map { it.identifier })
    }

    @Test
    fun `a body that is not the expected catalogue yields nothing`() {
        assertTrue(modulesOf("<html><body>404</body></html>").isEmpty())
    }

    @Test
    fun `a completely empty response yields nothing`() {
        assertTrue(modulesOf("").isEmpty())
    }

    @Test
    fun `a header missing the translation id column yields nothing`() {
        val body = "languageCode,shortTitle,title,Copyright,Redistributable,downloadable,UpdateDate\n" +
            "eng,Berean,,public domain,True,True,2024-01-01"

        assertTrue(modulesOf(body).isEmpty())
    }

    @Test
    fun `a header missing the language column yields nothing`() {
        val body = "translationId,shortTitle,title,Copyright,Redistributable,downloadable,UpdateDate\n" +
            "engbsb,Berean,,public domain,True,True,2024-01-01"

        assertTrue(modulesOf(body).isEmpty())
    }

    // --- naming ---

    @Test
    fun `installed names follow the shared naming rules`() {
        val body = csv(
            "eng,engbsb,Berean Standard Bible,,public domain,True,True,2024-01-01",
            "eng,engnet,NET Bible,,free,True,True,2024-01-01",
            "rus,russyn,Russian Synodal,,public domain,True,True,2024-01-01",
        )

        val names = modulesOf(body).map { it.fileName }

        // The translation id already begins with the language, so it is not repeated.
        assertEquals(listOf("ENG_BSB.spb", "ENG_NET.spb", "RUS_SYN.spb"), names)
    }

    @Test
    fun `colliding names are separated`() {
        val body = csv(
            "eng,engbsb,First,,public domain,True,True,2024-01-01",
            "eng,eng-bsb,Second,,public domain,True,True,2024-01-01",
        )

        assertEquals(listOf("ENG_BSB.spb", "ENG_BSB_2.spb"), modulesOf(body).map { it.fileName })
    }

    @Test
    fun `parsing the same catalogue twice yields identical names`() {
        val body = csv(
            "eng,engbsb,First,,public domain,True,True,2024-01-01",
            "eng,eng-bsb,Second,,public domain,True,True,2024-01-01",
        )

        assertEquals(modulesOf(body).map { it.fileName }, modulesOf(body).map { it.fileName })
    }

    // --- fetching and caching ---

    @Test
    fun `a successful fetch is cached to disk`() {
        val outcome = assertIs<BibleCatalogOutcome.Success>(
            fetch(httpServing(csv("eng,engbsb,Berean,,public domain,True,True,2024-01-01"))),
        )

        assertEquals(1, outcome.modules.size)
        assertFalse(outcome.stale)
        assertTrue(cacheFile.isFile)
    }

    @Test
    fun `a second fetch inside the cache window does not go back to the network`() {
        fetch(httpServing(csv("eng,engbsb,Berean,,public domain,True,True,2024-01-01")), now = 1_000L)
        EBibleSource.clearMemoryCache()

        // Any request through this client throws, so a Success proves nothing was requested.
        val second = assertIs<BibleCatalogOutcome.Success>(fetch(httpFailing(), now = 2_000L))

        assertFalse(second.stale)
        assertEquals(1, second.modules.size)
    }

    @Test
    fun `an unreachable host falls back to the cached catalogue`() {
        fetch(httpServing(csv("eng,engbsb,Berean,,public domain,True,True,2024-01-01")), now = 1_000L)
        EBibleSource.clearMemoryCache()
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        val offline = assertIs<BibleCatalogOutcome.Success>(fetch(httpFailing(), now = muchLater))

        assertTrue(offline.stale, "an offline result must be flagged so the dialog can say so")
        assertEquals(1, offline.modules.size)
    }

    @Test
    fun `an unreachable host with no cache is a network error`() {
        assertEquals(BibleCatalogOutcome.NetworkError, fetch(httpFailing()))
    }

    @Test
    fun `a server error with no cache is a failure`() {
        assertEquals(
            BibleCatalogOutcome.Failure,
            fetch(httpServing("nope", status = HttpStatusCode.InternalServerError)),
        )
    }

    @Test
    fun `a cache file that parses to no translations is treated as no cache at all`() {
        // Only the header row, no data — a corrupt or truncated write from a previous run.
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(header)
        File(cacheFile.parentFile, cacheFile.name + ".meta").writeText("1000")

        val offline = fetch(httpFailing(), now = 1_000L)

        assertEquals(BibleCatalogOutcome.NetworkError, offline, "an empty cache must not be reported as a stale success")
    }

    @Test
    fun `a meta file with unreadable content is treated as never fetched`() {
        fetch(httpServing(csv("eng,engbsb,Berean,,public domain,True,True,2024-01-01")), now = 1_000L)
        EBibleSource.clearMemoryCache()
        File(cacheFile.parentFile, cacheFile.name + ".meta").writeText("not a timestamp")
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        // The fallback age is 0L — "just fetched" relative to a small clock reading, but far in the
        // past relative to a realistic one — so this has to land outside the cache window and reach
        // the network rather than silently trusting a cache whose age it could not read.
        val outcome = assertIs<BibleCatalogOutcome.Success>(
            fetch(httpServing(csv("eng,engnet,NET,,public domain,True,True,2024-01-01")), now = muchLater),
        )

        assertEquals("engnet", outcome.modules.single().identifier, "a stale age must not serve the old cache unrefreshed")
    }

    @Test
    fun `catalog() reaches the same mapping as fetchCatalog()`() {
        // catalog() always asks at its own default URL, so it cannot be pointed at a MockEngine
        // directly — seeding the process-wide memory cache through fetchCatalog() first, at the
        // same timestamp, lets catalog() read that cache instead of ever reaching the network,
        // since the cache check only compares timestamps and is not keyed by URL.
        fetch(httpServing(csv("eng,engbsb,Berean,,public domain,True,True,2024-01-01")), now = 5_000L)

        val outcome = runBlocking { EBibleSource.catalog(nowMillis = 5_000L) }

        val success = assertIs<BibleCatalogOutcome.Success>(outcome)
        assertEquals("engbsb", success.modules.single().identifier)
    }

    // --- installing, end to end ---

    private fun usfx() = """<?xml version="1.0" encoding="utf-8"?><usfx><languageCode>eng</languageCode>
        <book id="GEN"><c id="1"/><v id="1"/>In the beginning God created the heavens and the earth.<ve/>
        <v id="2"/>Now the earth was formless and void.<ve/></book>
        <book id="MAT"><c id="1"/><v id="1"/>This is the record of the genealogy of Jesus Christ.<ve/></book>
        </usfx>""".trimIndent()

    private fun bookNames() = """<?xml version="1.0" encoding="utf-8"?><BookNames>
        <book code="GEN" abbr="Gen" short="Genesis" long="Genesis"/>
        <book code="MAT" abbr="Mat" short="Matthew" long="Matthew"/>
        </BookNames>""".trimIndent()

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

    private fun module(copyright: String = "public domain") = BibleModule(
        sourceId = BibleSourceId.EBIBLE,
        downloadKey = "engbsb",
        language = "ENG",
        identifier = "engbsb",
        displayName = "Berean Standard Bible",
        copyright = copyright,
        fileStem = "ENG_BSB",
    )

    private fun install(http: HttpClient, module: BibleModule = module()) = runBlocking {
        // Zero backoff: the retry is what is under test, not how long it waits before it.
        EBibleSource.installEBible(module, targetDir, http, retryFloorMs = 0L) {}
    }

    /** A server that stops part-way through the first answer and serves the tail when asked. */
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

    @Test
    fun `a download that stops half way still installs`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx(), "BookNames.xml" to bookNames())

        val outcome = assertIs<BibleInstallOutcome.Success>(install(httpStallingThenServing(bytes)))

        assertEquals(2, outcome.books)
        assertTrue(outcome.file.readText().contains("B001C001V001\t1\t1\t1\tIn the beginning"))
    }

    @Test
    fun `a download that never gets going is reported as stalled, not as being offline`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx())
        val http = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(ByteArray(0)),
                    headers = headersOf(HttpHeaders.ContentLength, bytes.size.toString()),
                )
            },
        )

        assertEquals(BibleInstallOutcome.DownloadStalled, install(http))
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a translation is downloaded, converted and installed under its derived name`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx(), "BookNames.xml" to bookNames())

        val outcome = assertIs<BibleInstallOutcome.Success>(install(httpServingBytes(bytes)))

        assertEquals(File(targetDir, "ENG_BSB.spb"), outcome.file)
        assertEquals(2, outcome.books)
        assertEquals("public domain", outcome.rights)

        val text = outcome.file.readText()
        assertTrue(text.startsWith("##spDataVersion:"))
        assertTrue(text.contains("##Title:\tBerean Standard Bible"))
        assertTrue(text.contains("##Copyright:\tpublic domain"), "the licence travels with the file")
        assertTrue(text.contains("1\tGenesis\t1"), "book names come from the archive's own list")
        assertTrue(text.contains("B001C001V001\t1\t1\t1\tIn the beginning God created the heavens and the earth."))
    }

    @Test
    fun `an archive with no scripture in it is rejected`() {
        val bytes = zipOf("BookNames.xml" to bookNames())

        assertEquals(BibleInstallOutcome.CorruptArchive, install(httpServingBytes(bytes)))
        assertTrue(targetDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a translation installs even without a book-name list`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx())

        val outcome = assertIs<BibleInstallOutcome.Success>(install(httpServingBytes(bytes)))

        assertTrue(outcome.file.readText().contains("1\tGenesis\t1"), "falling back to the curated English names")
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
        val installed = File(targetDir, "ENG_BSB.spb")
        val original = "##spDataVersion:\t1\n##Title:\tthe one already in use\n"
        installed.writeText(original)

        install(httpServingBytes("<html>404</html>".toByteArray()))
        assertEquals(original, installed.readText(), "a bad download must not touch the live Bible")

        install(httpFailing())
        assertEquals(original, installed.readText(), "a dropped connection must not touch it either")
    }

    @Test
    fun `re-installing replaces the Bible rather than accumulating`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx(), "BookNames.xml" to bookNames())
        install(httpServingBytes(bytes))
        install(httpServingBytes(bytes))

        assertEquals(listOf("ENG_BSB.spb"), targetDir.list()!!.sorted(), "no scratch files are left behind")
    }

    @Test
    fun `progress runs forward through every phase and ends complete`() {
        val bytes = zipOf("engbsb_usfx.xml" to usfx(), "BookNames.xml" to bookNames())
        val reported = mutableListOf<InstallProgress>()

        val outcome = runBlocking {
            EBibleSource.installEBible(module(), targetDir, httpServingBytes(bytes)) { reported.add(it) }
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
}
