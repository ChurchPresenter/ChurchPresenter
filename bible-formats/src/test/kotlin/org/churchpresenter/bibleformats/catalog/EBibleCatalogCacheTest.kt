package org.churchpresenter.bibleformats.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The eBible catalogue's cache lifecycle: what is served from memory, what is served from disk, and
 * what goes back to the network.
 *
 * The catalogue is a 1,500-row CSV fetched over the internet, and a hall with a slow line should not
 * pay for it twice in one session — nor should a hall with no line at all lose the list it saw last
 * time. Both caches are checked before the fetch, so the order matters and is asserted here by
 * counting requests rather than by timing anything: `fetchCatalog` takes its clock as a parameter,
 * so a TTL expiry is arithmetic and the suite never waits.
 */
class EBibleCatalogCacheTest {

    private lateinit var dir: File
    private lateinit var cacheFile: File
    private var requests = 0

    private val url = "https://ebible.invalid/translations.csv"
    // The name columns are included because the language table below is built from them.
    private val header = "languageCode,translationId,languageName,languageNameInEnglish," +
        "shortTitle,title,Copyright,Redistributable,downloadable,UpdateDate"

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-ebible-cache-test").toFile()
        cacheFile = File(dir, "cache/ebible-catalog.csv")
        requests = 0
        EBibleSource.clearMemoryCache()
    }

    @AfterTest
    fun cleanUp() {
        EBibleSource.clearMemoryCache()
        dir.deleteRecursively()
    }

    private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\n")

    private val oneTranslation =
        csv("eng,engKJV,English,English,KJV,King James Version,Public Domain,True,True,2024-01-01")

    private fun http(body: String = oneTranslation, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine {
            requests++
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "text/csv"))
        },
    )

    private fun fetch(client: HttpClient, now: Long) = runBlocking {
        EBibleSource.fetchCatalog(url = url, http = client, cacheFile = cacheFile, nowMillis = now)
    }

    @Test
    fun `closing the browser mid-fetch cancels rather than reporting a failure`() = runBlocking {
        // Closing the download browser cancels BibleCatalogViewModel's scope while the request is
        // still suspended, and ktor delivers that as an IOException over a channel closed
        // underneath it rather than as a CancellationException — so the fetch caught it and filed
        // "eBible catalogue fetch failed" against a user who had merely changed their mind.
        //
        // The request is held until the test has cancelled the job, so the cancellation is the
        // reason the call ends. Nothing here waits on a clock: both sides signal each other.
        val inFlight = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine {
                requests++
                inFlight.complete(Unit)
                awaitCancellation()
            },
        )

        val job = async {
            runCatching {
                EBibleSource.fetchCatalog(url = url, http = client, cacheFile = cacheFile, nowMillis = 1_000)
            }
        }
        inFlight.await()
        job.cancel()

        assertFailsWith<CancellationException> { job.await() }
        assertEquals(1, requests)
    }

    @Test
    fun `a network failure that is not a cancellation still yields an outcome`() {
        // The other side of the arm above: a real failure must not start propagating instead of
        // reaching the install dialog with something to show.
        val client = HttpClient(MockEngine { requests++; throw IOException("connection reset") })

        assertIs<BibleCatalogOutcome.NetworkError>(fetch(client, now = 1_000))
    }

    @Test
    fun `a fetched catalogue is written to the cache file`() {
        val outcome = fetch(http(), now = 1_000)

        assertIs<BibleCatalogOutcome.Success>(outcome)
        assertEquals(1, outcome.modules.size)
        assertTrue(cacheFile.exists(), "the next launch has to have something to read")
        assertTrue(cacheFile.readText().contains("engKJV"))
    }

    @Test
    fun `a second call inside the ttl is served from memory without asking again`() {
        val client = http()
        fetch(client, now = 1_000)
        val again = fetch(client, now = 1_000 + 60_000)

        assertIs<BibleCatalogOutcome.Success>(again)
        assertEquals(1, requests, "the catalogue was already in memory; asking again is a wasted round trip")
    }

    /**
     * A fresh process has an empty memory cache but a populated disk one — the common case on the
     * second launch of the day, and the one that decides whether the tab opens instantly or waits.
     */
    @Test
    fun `a fresh process reads the disk cache rather than the network`() {
        fetch(http(), now = 1_000)
        EBibleSource.clearMemoryCache()
        requests = 0

        val outcome = fetch(http(), now = 1_000 + 60_000)

        assertIs<BibleCatalogOutcome.Success>(outcome)
        assertEquals(1, outcome.modules.size)
        assertEquals(0, requests, "a cache written a minute ago is not stale")
    }

    @Test
    fun `a cache older than the ttl is refetched`() {
        fetch(http(), now = 1_000)
        EBibleSource.clearMemoryCache()
        requests = 0

        val eightDays = 8L * 24 * 60 * 60 * 1000
        val outcome = fetch(http(), now = 1_000 + eightDays)

        assertIs<BibleCatalogOutcome.Success>(outcome)
        assertEquals(1, requests, "a week-old catalogue has to be refreshed")
    }

    @Test
    fun `a server error is reported rather than presented as an empty catalogue`() {
        val outcome = fetch(http(status = HttpStatusCode.InternalServerError), now = 1_000)

        assertIs<BibleCatalogOutcome.Failure>(outcome)
    }

    /**
     * The language table exists so the Zefania tab can name its languages. It reads what is already
     * known and never fetches — a second tab reaching out to a second host to spell a name would be
     * a worse trade than showing the bare code.
     */
    @Test
    fun `the language table is read from the cache without a fetch`() {
        fetch(http(), now = 1_000)
        EBibleSource.clearMemoryCache()
        requests = 0

        val names = runBlocking { EBibleSource.cachedLanguageNames(cacheFile) }

        assertEquals(0, requests, "naming a language must never cost a round trip")
        // Keyed by the uppercase code, which is the spelling the language filter looks rows up by.
        assertEquals("English", names.getValue("ENG").english, "the catalogue named this language")
    }

    @Test
    fun `the language table is empty rather than absent when nothing has been cached`() {
        assertTrue(runBlocking { EBibleSource.cachedLanguageNames(File(dir, "never-written.csv")) }.isEmpty())
    }
}
