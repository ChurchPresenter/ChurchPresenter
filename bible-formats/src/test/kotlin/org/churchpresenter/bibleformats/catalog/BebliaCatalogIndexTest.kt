package org.churchpresenter.bibleformats.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Reading the Holy Bible XML archive's `catalog.json`.
 *
 * The manifest is generated data committed beside the files it describes, so the two failure modes
 * that matter are a half-written one and a stale one. A manifest that parses to no translations, or
 * that names no commit for the downloads to pin to, is refused outright — presented as a catalogue it
 * would look like a working but empty tab, which is far harder to diagnose than an error. Everything
 * else falls back to the cached copy and says so, because a hall with no internet should still see
 * the list it saw last time.
 *
 * The cache lifecycle is exercised over `MockEngine` with a counter rather than a clock: nothing here
 * waits, and `nowMillis` is passed in so the TTL is arithmetic instead of elapsed time.
 */
class BebliaCatalogIndexTest {

    private lateinit var dir: File
    private lateinit var cacheFile: File
    private val restore = mutableMapOf<String, String?>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-beblia-index-test").toFile()
        cacheFile = File(dir, "beblia-catalog.json")
        BebliaCatalogIndex.clearMemoryCache()
    }

    @AfterTest
    fun cleanUp() {
        restore.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
        restore.clear()
        BebliaCatalogIndex.clearMemoryCache()
        dir.deleteRecursively()
    }

    private fun setProperty(key: String, value: String?) {
        restore.putIfAbsent(key, System.getProperty(key))
        if (value == null) System.clearProperty(key) else System.setProperty(key, value)
    }

    // --- fixtures ---

    private fun entry(
        file: String = "EnglishKJBible.xml",
        sha: String = "abc123",
        size: Long = 4_404_123,
        title: String = "English KJV",
        id: String = "KJ",
        lang: String = "ENG",
        langName: String = "English",
        rights: String = "Public Domain",
        ot: Int = 39,
        nt: Int = 27,
    ) = """{"file":"$file","sha":"$sha","size":$size,"title":"$title","id":"$id","lang":"$lang",
        "langName":"$langName","langFrom":"filename","rights":"$rights",
        "url":"https://example.invalid/x","ot":$ot,"nt":$nt}""".trimIndent().replace("\n", "")

    private fun manifest(vararg entries: String, commit: String = "0".repeat(40)) =
        """{"schemaVersion":1,"commit":"$commit","bibles":[${entries.joinToString(",")}]}"""

    private fun httpServing(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        etag: String = "",
        counter: IntArray? = null,
    ) = HttpClient(
        MockEngine {
            counter?.let { it[0]++ }
            respond(
                content = body,
                status = status,
                headers = if (etag.isBlank()) headersOf(HttpHeaders.ContentType, "application/json")
                else headersOf(HttpHeaders.ETag, etag),
            )
        },
    )

    private fun httpFailing(counter: IntArray? = null) = HttpClient(
        MockEngine {
            counter?.let { it[0]++ }
            throw java.io.IOException("no route to host")
        },
    )

    // --- URLs ---

    @Test
    fun `the catalogue and raw urls point at the archive by default`() {
        setProperty("churchpresenter.bebliaCatalogUrl", null)
        setProperty("churchpresenter.bebliaRawBase", null)

        assertEquals(
            "https://raw.githubusercontent.com/ChurchPresenter/Holy-Bible-XML-Format/master/catalog.json",
            BebliaCatalogIndex.catalogUrl()
        )
        assertEquals(
            "https://raw.githubusercontent.com/ChurchPresenter/Holy-Bible-XML-Format/abc/EnglishKJBible.xml",
            BebliaCatalogIndex.rawUrlFor("abc", "EnglishKJBible.xml")
        )
    }

    @Test
    fun `both urls can be pointed at a staging archive`() {
        setProperty("churchpresenter.bebliaCatalogUrl", "https://staging.invalid/catalog.json")
        setProperty("churchpresenter.bebliaRawBase", "https://staging.invalid/raw/")

        assertEquals("https://staging.invalid/catalog.json", BebliaCatalogIndex.catalogUrl())
        assertEquals("https://staging.invalid/raw/abc/A%20File.xml", BebliaCatalogIndex.rawUrlFor("abc", "A File.xml"))
    }

    @Test
    fun `a blank override is treated as unset rather than as an empty url`() {
        setProperty("churchpresenter.bebliaCatalogUrl", "  ")
        assertTrue(BebliaCatalogIndex.catalogUrl().startsWith("https://raw.githubusercontent.com/"))
    }

    // --- parsing ---

    @Test
    fun `a manifest entry becomes a browse row`() {
        val outcome = BebliaCatalogIndex.parseCatalog(manifest(entry()), etag = "W/\"x\"")

        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome).index
        assertEquals("0".repeat(40), index.commit)
        assertEquals("W/\"x\"", index.etag)
        val module = index.modules.single()
        assertEquals("EnglishKJBible.xml", module.file)
        assertEquals("abc123", module.blobSha)
        assertEquals(4_404_123, module.sizeBytes)
        assertEquals("ENG", module.language)
        assertEquals("English", module.languageName)
        assertEquals("English KJV", module.displayName)
        assertEquals("Public Domain", module.copyright)
        assertEquals(39, module.otBookCount)
        assertEquals(27, module.ntBookCount)
        assertEquals("ENG_KJ", module.fileStem)
        assertEquals("ENG_KJ.spb", module.fileName)
    }

    @Test
    fun `unknown fields are ignored so the archive can add a column without an app release`() {
        val body = """{"schemaVersion":1,"commit":"${"0".repeat(40)}","generatedAt":"2026-08-10",
            "bibles":[{"file":"x.xml","lang":"ENG","id":"A","somethingNew":42}]}""".trimIndent().replace("\n", "")

        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(BebliaCatalogIndex.parseCatalog(body)).index
        assertEquals("ENG_A", index.modules.single().fileStem)
    }

    @Test
    fun `a manifest listing no translations is refused rather than shown as an empty archive`() {
        assertIs<BebliaCatalogIndex.IndexOutcome.Failure>(BebliaCatalogIndex.parseCatalog(manifest()))
    }

    @Test
    fun `a manifest naming no commit is refused, since downloads have nothing to pin to`() {
        assertIs<BebliaCatalogIndex.IndexOutcome.Failure>(
            BebliaCatalogIndex.parseCatalog(manifest(entry(), commit = ""))
        )
    }

    @Test
    fun `a malformed manifest is a failure rather than a crash`() {
        assertIs<BebliaCatalogIndex.IndexOutcome.Failure>(BebliaCatalogIndex.parseCatalog("{not json"))
    }

    @Test
    fun `an entry with no file name is dropped`() {
        val body = manifest(entry(file = ""), entry(file = "Keep.xml", id = "KEEP"))
        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(BebliaCatalogIndex.parseCatalog(body)).index
        assertEquals(listOf("Keep.xml"), index.modules.map { it.file })
    }

    @Test
    fun `an untitled entry falls back to its file name rather than arriving blank`() {
        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(
            BebliaCatalogIndex.parseCatalog(manifest(entry(title = "")))
        ).index
        assertEquals("EnglishKJBible", index.modules.single().displayName)
    }

    @Test
    fun `an entry with no language code becomes the unknown code rather than a blank`() {
        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(
            BebliaCatalogIndex.parseCatalog(manifest(entry(lang = "", id = "")))
        ).index
        assertEquals("UND", index.modules.single().language)
        assertEquals("UND", index.modules.single().fileStem)
    }

    @Test
    fun `translations that reduce to the same stem are separated`() {
        val body = manifest(
            entry(file = "A.xml", lang = "ENG", id = ""),
            entry(file = "B.xml", lang = "ENG", id = ""),
            entry(file = "C.xml", lang = "ENG", id = ""),
        )
        val index = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(BebliaCatalogIndex.parseCatalog(body)).index
        assertEquals(listOf("ENG", "ENG_2", "ENG_3"), index.modules.map { it.fileStem })
    }

    @Test
    fun `stems do not depend on the order the manifest happens to list its entries`() {
        val a = entry(file = "A.xml", lang = "ENG", id = "")
        val b = entry(file = "B.xml", lang = "ENG", id = "")

        val forwards = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(
            BebliaCatalogIndex.parseCatalog(manifest(a, b))
        ).index.modules.associate { it.file to it.fileStem }
        val backwards = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(
            BebliaCatalogIndex.parseCatalog(manifest(b, a))
        ).index.modules.associate { it.file to it.fileStem }

        assertEquals(forwards, backwards, "the same manifest must name the same files the same way")
    }

    // --- caching ---

    @Test
    fun `a fetched manifest is cached to disk and reused inside the ttl`() = runBlocking {
        val calls = intArrayOf(0)
        val http = httpServing(manifest(entry()), counter = calls)

        val first = BebliaCatalogIndex.fetch("https://x.invalid/c.json", http, cacheFile, nowMillis = 1_000)
        assertIs<BebliaCatalogIndex.IndexOutcome.Success>(first)
        assertTrue(cacheFile.isFile, "the manifest is written to disk")

        BebliaCatalogIndex.clearMemoryCache()
        val second = BebliaCatalogIndex.fetch("https://x.invalid/c.json", http, cacheFile, nowMillis = 2_000)
        assertIs<BebliaCatalogIndex.IndexOutcome.Success>(second)
        assertEquals(1, calls[0], "a second fetch inside the TTL asks the network for nothing")
    }

    @Test
    fun `an expired cache is refetched`() = runBlocking {
        val calls = intArrayOf(0)
        val http = httpServing(manifest(entry()), counter = calls)
        val week = 7L * 24 * 60 * 60 * 1000

        BebliaCatalogIndex.fetch("https://x.invalid/c.json", http, cacheFile, nowMillis = 0)
        BebliaCatalogIndex.clearMemoryCache()
        BebliaCatalogIndex.fetch("https://x.invalid/c.json", http, cacheFile, nowMillis = week + 1)

        assertEquals(2, calls[0])
    }

    @Test
    fun `an unreachable archive falls back to the cached copy and says it is stale`() = runBlocking {
        BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpServing(manifest(entry())), cacheFile, 0)
        BebliaCatalogIndex.clearMemoryCache()
        val week = 7L * 24 * 60 * 60 * 1000

        val outcome = BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpFailing(), cacheFile, week + 1)

        val success = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome)
        assertTrue(success.stale, "an offline hall still sees the list it saw last time")
        assertEquals("EnglishKJBible.xml", success.index.modules.single().file)
    }

    @Test
    fun `an unreachable archive with nothing cached is a network error`() {
        runBlocking {
            val outcome = BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpFailing(), cacheFile, 0)
            assertIs<BebliaCatalogIndex.IndexOutcome.NetworkError>(outcome)
        }
    }

    @Test
    fun `a server error falls back to the cached copy`() = runBlocking {
        BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpServing(manifest(entry())), cacheFile, 0)
        BebliaCatalogIndex.clearMemoryCache()
        val week = 7L * 24 * 60 * 60 * 1000

        val outcome = BebliaCatalogIndex.fetch(
            "https://x.invalid/c.json",
            httpServing("nope", status = HttpStatusCode.InternalServerError),
            cacheFile,
            week + 1,
        )
        assertTrue(assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome).stale)
    }

    @Test
    fun `a manifest that arrives corrupt does not replace a good cached copy`() = runBlocking {
        BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpServing(manifest(entry())), cacheFile, 0)
        BebliaCatalogIndex.clearMemoryCache()
        val week = 7L * 24 * 60 * 60 * 1000

        val outcome = BebliaCatalogIndex.fetch(
            "https://x.invalid/c.json",
            httpServing("{not json"),
            cacheFile,
            week + 1,
        )

        val success = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome)
        assertTrue(success.stale)
        assertEquals(1, success.index.modules.size)
        assertNotEquals("{not json", cacheFile.readText(), "the good copy on disk is left alone")
    }

    @Test
    fun `an unreadable cache is ignored rather than crashing the fetch`() = runBlocking {
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText("{ this was never json")

        val outcome = BebliaCatalogIndex.fetch("https://x.invalid/c.json", httpServing(manifest(entry())), cacheFile, 0)
        assertEquals(1, assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome).index.modules.size)
    }

    @Test
    fun `a 304 reuses the cached copy without re-parsing a body`() = runBlocking {
        BebliaCatalogIndex.fetch(
            "https://x.invalid/c.json",
            httpServing(manifest(entry()), etag = "W/\"v1\""),
            cacheFile,
            0,
        )
        BebliaCatalogIndex.clearMemoryCache()
        val week = 7L * 24 * 60 * 60 * 1000

        val outcome = BebliaCatalogIndex.fetch(
            "https://x.invalid/c.json",
            httpServing("", status = HttpStatusCode.NotModified),
            cacheFile,
            week + 1,
        )

        val success = assertIs<BebliaCatalogIndex.IndexOutcome.Success>(outcome)
        assertTrue(!success.stale, "a revalidated cache is current, not stale")
        assertEquals("EnglishKJBible.xml", success.index.modules.single().file)
    }
}
