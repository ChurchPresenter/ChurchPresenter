package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Listing the Bible modules available in the Zefania archive.
 *
 * The whole browse list is derived from file names, so the parsing rules carry real weight: the
 * archive's names contain spaces, parentheses and underscores *inside* the display name
 * (`(1933_1953 AFRIKAANS BYBEL)`), and a module whose name doesn't follow the convention must still
 * appear rather than silently vanishing from the list. Language is taken from the directory, which
 * is how the archive is actually curated.
 *
 * The stems have to be reproducible: the dialog decides whether a translation is already installed
 * by comparing a derived name against the files on disk, and nothing is pinned anywhere, so two
 * machines parsing the same listing must agree exactly.
 *
 * The rest is about a hall with no internet. GitHub allows 60 unauthenticated requests an hour per
 * address, which a church shares, so a cached listing has to survive across sessions and a failure
 * has to fall back to it rather than showing an empty window.
 *
 * Every request is served by a `MockEngine` passed into the call and every file goes to a per-test
 * temp directory. The in-memory cache is process-wide, so it is cleared in both hooks.
 */
class ZefaniaRepositoryIndexTest {

    private lateinit var dir: File
    private lateinit var cacheFile: File
    private val requests = mutableListOf<HttpRequestData>()

    private val url = "https://api.invalid/repos/x/y/git/trees/main?recursive=1"
    private val prefix = ZefaniaRepositoryIndex.BIBLES_PREFIX

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-zefania-index-test").toFile()
        cacheFile = File(dir, "cache/zefania-index.json")
        ZefaniaRepositoryIndex.clearMemoryCache()
    }

    @AfterTest
    fun cleanUp() {
        ZefaniaRepositoryIndex.clearMemoryCache()
        dir.deleteRecursively()
    }

    private fun blob(path: String, size: Long = 1000, sha: String = "abc") =
        """{"path":"$path","type":"blob","sha":"$sha","size":$size}"""

    private fun tree(vararg entries: String, truncated: Boolean = false) =
        """{"truncated":$truncated,"tree":[${entries.joinToString(",")}]}"""

    /** The real shapes found in the archive. */
    private val realisticTree = tree(
        blob("$prefix" + "ENG/A Conservative Version/SF_2009-01-20_ENG_ACV_(A CONSERVATIVE VERSION).zip",
            1255567,
            "sha1"),
        blob("$prefix" + "AFR/1933/1953 Afrikaans Bybel/SF_2009-01-20_AFR_AFR3353_(1933_1953 AFRIKAANS BYBEL).zip",
            1293091,
            "sha2"),
        blob("$prefix" + "SWA/Neno/SF_2009-01-20_SWA_SWA_(SWAHILI NT).zip", 500, "sha3"),
        blob("$prefix" + "CZE/Cesky studijni preklad/SF_2015-01-01_CZE_ČSP_(Český studijní překlad).zip", 600, "sha4"),
        blob("zefania-sharp-sourceforge-backup/Dictionaries/GER/X/SF_2005-12-09_GER_LUTH_(KONKORDANZ).zip",
            700,
            "sha5"),
        blob("iso639_codes.xml", 800, "sha6"),
        treeEntry(prefix + "ENG"),
    )

    private fun treeEntry(path: String) = """{"path":"$path","type":"tree","sha":"sha7"}"""

    private fun httpServing(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Map<String, String> = emptyMap(),
    ) = HttpClient(
        MockEngine { request ->
            requests.add(request)
            val built = headersOf(*buildList {
                add(HttpHeaders.ContentType to listOf("application/json"))
                headers.forEach { (k, v) -> add(k to listOf(v)) }
            }.toTypedArray())
            respond(content = body, status = status, headers = built)
        },
    )

    private fun httpFailing() = HttpClient(
        MockEngine { throw java.io.IOException("no route to host") },
    )

    private fun fetch(http: HttpClient, now: Long = 1_000L) = runBlocking {
        ZefaniaRepositoryIndex.fetch(url = url, http = http, cacheFile = cacheFile, nowMillis = now)
    }

    private fun modulesOf(body: String) =
        assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(ZefaniaRepositoryIndex.parseIndex(body)).index.modules

    // --- the staging override ---

    /** [System] properties are process-wide, so every test that touches one restores it. */
    private fun withProperty(name: String, value: String?, block: () -> Unit) {
        val original = System.getProperty(name)
        try {
            if (value == null) System.clearProperty(name) else System.setProperty(name, value)
            block()
        } finally {
            if (original == null) System.clearProperty(name) else System.setProperty(name, original)
        }
    }

    @Test
    fun `the default tree url is used when no staging override is set`() {
        withProperty("churchpresenter.zefaniaTreeUrl", null) {
            assertEquals(
                "https://api.github.com/repos/ChurchPresenter/Zefania-XML-Preservation/git/trees/main?recursive=1",
                ZefaniaRepositoryIndex.treeUrl(),
            )
        }
    }

    @Test
    fun `a staging override replaces the default tree url`() {
        withProperty("churchpresenter.zefaniaTreeUrl", "https://staging.invalid/tree") {
            assertEquals("https://staging.invalid/tree", ZefaniaRepositoryIndex.treeUrl())
        }
    }

    @Test
    fun `a blank tree url override is treated as unset`() {
        withProperty("churchpresenter.zefaniaTreeUrl", "") {
            assertTrue(ZefaniaRepositoryIndex.treeUrl().startsWith("https://api.github.com/"))
        }
    }

    @Test
    fun `the default raw base is used when no staging override is set`() {
        withProperty("churchpresenter.zefaniaRawBase", null) {
            assertEquals(
                "https://raw.githubusercontent.com/ChurchPresenter/Zefania-XML-Preservation/main",
                ZefaniaRepositoryIndex.rawBase(),
            )
        }
    }

    @Test
    fun `a staging override replaces the default raw base`() {
        withProperty("churchpresenter.zefaniaRawBase", "https://staging.invalid/raw") {
            assertEquals("https://staging.invalid/raw", ZefaniaRepositoryIndex.rawBase())
        }
    }

    @Test
    fun `a blank raw base override is treated as unset`() {
        withProperty("churchpresenter.zefaniaRawBase", "") {
            assertTrue(ZefaniaRepositoryIndex.rawBase().startsWith("https://raw.githubusercontent.com/"))
        }
    }

    // --- parsing ---

    @Test
    fun `a module name yields its language, identifier and display name`() {
        val module = modulesOf(realisticTree).single { it.identifier == "ACV" }

        assertEquals("ENG", module.language)
        assertEquals("A CONSERVATIVE VERSION", module.displayName)
        assertEquals("2009-01-20", module.releaseDate)
        assertEquals(1255567L, module.sizeBytes)
        assertEquals("sha1", module.blobSha)
        assertEquals("ENG_ACV.spb", module.fileName)
    }

    @Test
    fun `underscores inside the display name survive`() {
        val module = modulesOf(realisticTree).single { it.language == "AFR" }

        assertEquals("1933 1953 AFRIKAANS BYBEL", module.displayName)
        assertEquals("AFR3353", module.identifier)
        assertEquals("AFR_3353.spb", module.fileName, "the identifier repeats the language, so it is stripped")
    }

    @Test
    fun `the installed name follows the naming rules`() {
        val byLanguage = modulesOf(realisticTree).associateBy { it.language }

        assertEquals("ENG_ACV.spb", byLanguage.getValue("ENG").fileName)
        assertEquals("AFR_3353.spb", byLanguage.getValue("AFR").fileName)
        assertEquals("SWA.spb", byLanguage.getValue("SWA").fileName, "identifier equal to the language collapses")
        assertEquals("CZE_CSP.spb", byLanguage.getValue("CZE").fileName, "non-ASCII identifiers are transliterated")
    }

    @Test
    fun `dictionaries, loose files and directories are excluded`() {
        val paths = modulesOf(realisticTree).map { it.path }

        assertEquals(4, paths.size)
        assertTrue(paths.none { it.contains("Dictionaries") }, "dictionaries use a different format")
        assertTrue(paths.none { it.endsWith(".xml") })
    }

    @Test
    fun `the archive's irregular names are still parsed`() {
        // Every one of these is a real file name from the archive. Names are not as regular as the
        // convention suggests, and each of these shapes used to fall through to the lenient path
        // and produce a poor name — the Czech one became "CZE_EKLAD".
        val body = tree(
            // Non-ASCII replaced by underscores: ČSP survives only as _SP.
            blob(prefix + "CZE/Studijni/SF_2015-05-20_CZE__SP_(_ESK_ STUDIJN_ P_EKLAD).zip", 1, "a"),
            // Underscore-separated date, and parentheses inside the title.
            blob(prefix + "ENG/Gal/SF_2004_04_25_ENG_MARCGAL_(THE EPISTLE TO THE GALATIANS(DETERING)).zip", 2, "b"),
            // An identifier containing underscores.
            blob(prefix + "ENG/Wycliffe/SF_2009-01-20_ENG_BIBLE_WYCLIFFE_(JOHN WYCLIFFE BIBLE).zip", 3, "c"),
            // No parenthesised title at all.
            blob(prefix + "GER/Allioli/SF_2020-02-04_DEU_ALLIOLI_ARNDT_1914_DEUTSCH.zip", 4, "d"),
            // A whole word where the language token should be.
            blob(prefix + "GER/Luther/SF_2012-08-14_DEUTSCH_LUT_1545_LH_(LUTHER 1545 (LETZTE HAND)).zip", 5, "e"),
        )

        val byLanguage = modulesOf(body).groupBy { it.language }

        assertEquals("CZE_SP.spb", byLanguage.getValue("CZE").single().fileName)
        assertEquals(
            "2004-04-25",
            byLanguage.getValue("ENG").first { it.identifier == "MARCGAL" }.releaseDate,
            "an underscore-separated date is normalised to match the others",
        )
        assertEquals(
            "ENG_BIBLEWYCLIFFE.spb",
            byLanguage.getValue("ENG").first { it.identifier.startsWith("BIBLE") }.fileName,
        )
        assertTrue(
            byLanguage.getValue("GER").any { it.displayName.contains("ALLIOLI") },
            "a module with no title in its name must still be listed",
        )
        assertTrue(
            byLanguage.getValue("GER").any { it.displayName.contains("LUTHER 1545") },
            "the language is taken from the directory, so an odd language token is harmless",
        )
    }

    @Test
    fun `a zip directly under the Bibles prefix with no language folder is dropped`() {
        // parseEntry reads the language from the first path segment after the prefix; a path with
        // no subdirectory at all has nothing there rather than something wrong there.
        val body = tree(blob(prefix + "loose-file-with-no-language-folder.zip", 10, "shaZ"))

        assertTrue(modulesOf(body).isEmpty(), "a module with no language directory must not crash the listing")
    }

    @Test
    fun `a name that does not follow the convention still appears`() {
        val body = tree(blob("$prefix" + "ENG/Odd/some-old-bible.zip", 900, "shaX"))

        val module = modulesOf(body).single()

        assertEquals("ENG", module.language, "the directory is still authoritative")
        assertTrue(module.displayName.isNotBlank(), "a module must never become invisible")
        assertTrue(module.fileName.endsWith(".spb"))
    }

    @Test
    fun `colliding stems are separated`() {
        val body = tree(
            blob("$prefix" + "ENG/One/SF_2009-01-20_ENG_KJV_(KING JAMES).zip", 1, "a"),
            blob("$prefix" + "ENG/Two/SF_2010-01-20_ENG_KJV_(KING JAMES REVISED).zip", 2, "b"),
        )

        val names = modulesOf(body).map { it.fileName }

        assertEquals(listOf("ENG_KJV.spb", "ENG_KJV_2.spb"), names)
    }

    @Test
    fun `parsing the same listing twice yields identical names`() {
        // The "Installed" badge compares derived names against files on disk with nothing pinned,
        // so two machines must agree exactly.
        assertEquals(
            modulesOf(realisticTree).map { it.fileName },
            modulesOf(realisticTree).map { it.fileName },
        )
    }

    @Test
    fun `a truncated listing is refused rather than shown as complete`() {
        val body = tree(blob("$prefix" + "ENG/X/SF_2009-01-20_ENG_ACV_(A).zip"), truncated = true)

        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.Failure, ZefaniaRepositoryIndex.parseIndex(body))
    }

    @Test
    fun `paths with spaces and parentheses are encoded for download`() {
        val encoded = ZefaniaRepositoryIndex.encodePath("Bibles/ENG/A Version/SF_(X).zip")

        assertEquals("Bibles/ENG/A%20Version/SF_%28X%29.zip", encoded)
    }

    // --- fetching and caching ---

    @Test
    fun `a successful fetch is cached to disk`() {
        val outcome = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(fetch(httpServing(realisticTree)))

        assertEquals(4, outcome.index.modules.size)
        assertFalse(outcome.stale)
        assertTrue(cacheFile.isFile)
    }

    @Test
    fun `a second fetch inside the cache window does not go back to the network`() {
        fetch(httpServing(realisticTree), now = 1_000L)
        ZefaniaRepositoryIndex.clearMemoryCache()

        // Any request through this client throws, so a Success proves nothing was requested.
        val second = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(fetch(httpFailing(), now = 2_000L))

        assertFalse(second.stale)
        assertEquals(4, second.index.modules.size)
    }

    @Test
    fun `an unreachable host falls back to the cached listing`() {
        fetch(httpServing(realisticTree), now = 1_000L)
        ZefaniaRepositoryIndex.clearMemoryCache()
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        val offline = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(fetch(httpFailing(), now = muchLater))

        assertTrue(offline.stale, "an offline result must be flagged so the dialog can say so")
        assertEquals(4, offline.index.modules.size)
    }

    @Test
    fun `an unreachable host with no cache is a network error`() {
        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.NetworkError, fetch(httpFailing()))
    }

    @Test
    fun `a corrupt cache file is ignored rather than thrown`() {
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText("{ this is not json")

        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.NetworkError, fetch(httpFailing()))
    }

    @Test
    fun `a cache file that parsed as a truncated listing is treated as no cache at all`() {
        // Valid JSON, unlike the test above — it fails to become a usable Index for a different
        // reason, by parsing successfully to Failure rather than throwing.
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(tree(blob(prefix + "ENG/X/SF_2009-01-20_ENG_ACV_(A).zip"), truncated = true))

        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.NetworkError, fetch(httpFailing()))
    }

    @Test
    fun `a meta file with unreadable content is treated as never fetched`() {
        fetch(httpServing(realisticTree), now = 1_000L)
        ZefaniaRepositoryIndex.clearMemoryCache()
        File(cacheFile.parentFile, cacheFile.name + ".meta").writeText("not a timestamp")
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        // The fallback age is 0L — recent relative to a small clock reading, but far in the past
        // relative to a realistic one — so this has to land outside the cache window and reach the
        // network rather than silently trusting a cache whose age it could not read.
        val fresh = tree(blob(prefix + "ENG/Y/SF_2009-01-20_ENG_KJV_(KING JAMES).zip", 1, "shaY"))
        val outcome = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(
            fetch(httpServing(fresh), now = muchLater),
        )

        assertEquals("KJV",
            outcome.index.modules.single().identifier,
            "a stale age must not serve the old cache unrefreshed")
    }

    @Test
    fun `a truncated listing served fresh over the network is a failure, not cached`() {
        // Distinct from parseIndex's own truncation test: this drives it through fetch() itself, on
        // the branch where the HTTP call actually succeeds but what comes back cannot be trusted —
        // and confirms nothing gets written to disk for a later call to serve back as if good.
        val outcome = fetch(httpServing(tree(blob(prefix + "ENG/X/SF_2009-01-20_ENG_ACV_(A).zip"), truncated = true)))

        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.Failure, outcome)
        assertFalse(cacheFile.isFile, "a listing that failed to parse must not be cached as if it succeeded")
    }

    @Test
    fun `a server error with no cache is a failure`() {
        assertEquals(
            ZefaniaRepositoryIndex.IndexOutcome.Failure,
            fetch(httpServing("nope", status = HttpStatusCode.InternalServerError)),
        )
    }

    @Test
    fun `being rate limited is reported with when to retry`() {
        val outcome = fetch(
            httpServing(
                "rate limited",
                status = HttpStatusCode.Forbidden,
                headers = mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1700000000"),
            ),
        )

        assertEquals(ZefaniaRepositoryIndex.IndexOutcome.RateLimited(1_700_000_000L), outcome)
    }

    @Test
    fun `being rate limited still shows the cached listing when there is one`() {
        fetch(httpServing(realisticTree), now = 1_000L)
        ZefaniaRepositoryIndex.clearMemoryCache()
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        val outcome = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(
            fetch(
                httpServing("rate limited",
                    status = HttpStatusCode.Forbidden,
                    headers = mapOf("x-ratelimit-remaining" to "0")),
                now = muchLater,
            ),
        )

        assertTrue(outcome.stale)
        assertEquals(4, outcome.index.modules.size)
    }

    @Test
    fun `an unchanged listing is revalidated without re-downloading it`() {
        fetch(httpServing(realisticTree, headers = mapOf("ETag" to "\"v1\"")), now = 1_000L)
        ZefaniaRepositoryIndex.clearMemoryCache()
        requests.clear()
        val muchLater = 1_000L + 30L * 24 * 60 * 60 * 1000

        val outcome = assertIs<ZefaniaRepositoryIndex.IndexOutcome.Success>(
            fetch(httpServing("", status = HttpStatusCode.NotModified), now = muchLater),
        )

        assertFalse(outcome.stale, "a revalidated listing is current, not stale")
        assertEquals(4, outcome.index.modules.size)
        assertEquals("\"v1\"", requests.single().headers["If-None-Match"], "a 304 costs no rate-limit quota")
    }
}
