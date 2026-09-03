package org.churchpresenter.bibleformats.catalog

import org.churchpresenter.bibleformats.BibleCatalogNaming
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.utils.Constants
import io.ktor.client.statement.HttpResponse
import java.io.File
import java.net.URLEncoder
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

private val HTTP_OK_RANGE = 200..299
private const val REGEX_GROUP_IDENTIFIER = 3
private const val REGEX_GROUP_DISPLAY_NAME = 4

/**
 * Lists the Bible modules available in the Zefania XML archive.
 *
 * The archive stores each translation as a zip whose name carries everything the browse list needs
 * — `SF_<date>_<LANG>_<IDENTIFIER>_(<Display Name>).zip` — so a single call to the repository's git
 * tree yields the entire catalogue, with sizes, and **nothing is downloaded until the user picks a
 * translation**. There is deliberately no manifest to maintain: the repository's own tree is the
 * source of truth, which is what keeps this from drifting the way a hand-curated index would.
 */
@Suppress("TooManyFunctions") // one index: fetch, parse, filter, cache, look up
object ZefaniaRepositoryIndex {


    const val OWNER = "ChurchPresenter"
    const val REPO = "Zefania-XML-Preservation"
    const val BRANCH = "main"

    /** Bible modules live here; the sibling `Dictionaries/` tree is a different format and is excluded. */
    const val BIBLES_PREFIX = "zefania-sharp-sourceforge-backup/Bibles/"

    /** One row of the browse list, derived entirely from the tree listing. */
    data class Module(
        val path: String,
        val blobSha: String,
        val sizeBytes: Long,
        val language: String,
        val identifier: String,
        val displayName: String,
        /**
         * When this conversion was published. Shown only where it disambiguates: the archive
         * carries a dozen or so translations twice under the same name, an older and a newer
         * conversion of the same text.
         */
        val releaseDate: String,
        val fileStem: String
    ) {
        val fileName: String get() = "$fileStem.${Constants.EXTENSION_SPB}"
    }

    data class Index(val modules: List<Module>, val etag: String = "")

    sealed interface IndexOutcome {
        /** [stale] means this came from the on-disk copy because the archive was unreachable. */
        data class Success(val index: Index, val stale: Boolean = false) : IndexOutcome
        /** GitHub is refusing unauthenticated requests for now; [resetEpochSeconds] says until when. */
        data class RateLimited(val resetEpochSeconds: Long?) : IndexOutcome
        data object NetworkError : IndexOutcome
        data object Failure : IndexOutcome
    }

    // --- tree DTOs ---

    @Serializable
    internal data class TreeEntry(
        val path: String = "",
        val type: String = "",
        val sha: String = "",
        val size: Long = 0
    )

    @Serializable
    internal data class TreeResponse(
        val tree: List<TreeEntry> = emptyList(),
        val truncated: Boolean = false
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultHttp by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 8_000
            }
        }
    }

    private val defaultCacheFile = File(System.getProperty("user.home"), ".churchpresenter/cache/zefania-index.json")

    /**
     * The archive is a preservation project, not a feed — it changes a few times a year. A long TTL
     * is what keeps a church's shared IP inside GitHub's 60-requests-per-hour unauthenticated limit.
     */
    private const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * `SF_2009-01-20_ENG_ACV_(A CONSERVATIVE VERSION).zip` and its many near-misses.
     *
     * Deliberately loose, because the archive's names are not as regular as they first look: dates
     * are occasionally underscore-separated, the language token is sometimes a whole word
     * (`DEUTSCH`), a few modules have no parenthesised title at all, and non-ASCII characters have
     * been replaced by underscores in the file names themselves (`ČSP` survives only as `_SP`) —
     * so the identifier cannot be "everything up to the first underscore". As written this matches
     * all 264 modules currently published; anything it still misses falls through to the lenient
     * path rather than disappearing from the list.
     */
    private val NAME_REGEX = Regex(
        """^SF_(\d{4}[-_]\d{2}[-_]\d{2})_([A-Za-z]{2,8})_(.+?)(?:_\((.*)\))?\.zip$""",
        RegexOption.IGNORE_CASE
    )

    @Volatile
    private var memoryCache: Pair<Long, Index>? = null

    /** `-Dchurchpresenter.zefaniaTreeUrl=` points at a staging archive. For testing, not a setting. */
    internal fun treeUrl(): String =
        System.getProperty("churchpresenter.zefaniaTreeUrl")?.takeIf { it.isNotBlank() }
            ?: "https://api.github.com/repos/$OWNER/$REPO/git/trees/$BRANCH?recursive=1"

    internal fun rawBase(): String =
        System.getProperty("churchpresenter.zefaniaRawBase")?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH"

    fun rawUrlFor(path: String): String = rawBase().trimEnd('/') + "/" + encodePath(path)

    /** Percent-encodes each segment; the archive's paths are full of spaces and parentheses. */
    internal fun encodePath(path: String): String =
        path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    internal fun clearMemoryCache() {
        memoryCache = null
    }

    suspend fun fetch(
        url: String = treeUrl(),
        http: HttpClient = defaultHttp,
        cacheFile: File = defaultCacheFile,
        nowMillis: Long = System.currentTimeMillis(),
    ): IndexOutcome = withContext(Dispatchers.IO) {
        memoryCache?.let { (fetchedAt, index) ->
            if (nowMillis - fetchedAt < CACHE_TTL_MILLIS) return@withContext IndexOutcome.Success(index)
        }

        val cached = readCache(cacheFile)
        val cachedAt = readMeta(cacheFile).first
        if (cached != null && nowMillis - cachedAt < CACHE_TTL_MILLIS) {
            memoryCache = nowMillis to cached
            return@withContext IndexOutcome.Success(cached)
        }

        try {
            val response = http.get(url) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "ChurchPresenter")
                // A 304 does not count against the rate limit, so a returning user spends no quota.
                if (cached != null && cached.etag.isNotBlank()) header("If-None-Match", cached.etag)
            }

            unusableResponseOutcome(response, cached, cacheFile, nowMillis)?.let { return@withContext it }

            val body = response.body<String>()
            val etag = response.headers["ETag"].orEmpty()
            when (val parsed = parseIndex(body, etag)) {
                is IndexOutcome.Success -> {
                    memoryCache = nowMillis to parsed.index
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(body)
                        writeMeta(cacheFile, nowMillis, etag)
                    }
                    parsed
                }
                else -> parsed
            }
            // An offline hall still sees the list it saw last time; the failure then surfaces on the
            // install, which is far more useful than an empty dialog.
        } catch (e: IOException) {
            indexFetchFailed(e, cached)
        } catch (e: UnresolvedAddressException) {
            indexFetchFailed(e, cached)
        } catch (e: IllegalStateException) {
            // See EBibleSource: a buffered body that stops early is ktor's Content-Length check,
            // not an IOException, and nothing above catches it.
            if (!with(BibleInstallSupport) { e.isContentLengthMismatch() }) throw e
            indexFetchFailed(e, cached)
        }
    }

    /**
     * The outcome for a fetch that did not complete: the index as it was last seen, or a failure.
     *
     * One function rather than a body per catch arm — the three of them differ only in the
     * exception they name. The eBible source has the same shape, for the same reason.
     */
    private fun indexFetchFailed(e: Throwable, cached: Index?): IndexOutcome =
        BibleInstallSupport.reported(
            "Zefania index fetch failed",
            e,
            mapOf("subsystem" to "zefania_index"),
            cached?.let { IndexOutcome.Success(it, stale = true) } ?: IndexOutcome.NetworkError,
        )

    /**
     * The outcome for a response that carries no new index — not-modified, rate-limited or an error
     * status — or null when the body should be parsed. A stale cached index beats an empty dialog.
     */
    private suspend fun unusableResponseOutcome(
        response: HttpResponse,
        cached: Index?,
        cacheFile: File,
        nowMillis: Long,
    ): IndexOutcome? {
        if (response.status == HttpStatusCode.NotModified && cached != null) {
            writeMeta(cacheFile, nowMillis, cached.etag)
            memoryCache = nowMillis to cached
            return IndexOutcome.Success(cached)
        }
        if (response.status == HttpStatusCode.Forbidden && response.headers["x-ratelimit-remaining"] == "0") {
            val reset = response.headers["x-ratelimit-reset"]?.toLongOrNull()
            return cached?.let { IndexOutcome.Success(it, stale = true) } ?: IndexOutcome.RateLimited(reset)
        }
        if (response.status.value !in HTTP_OK_RANGE) {
            CrashReporter.reportWarning(
                "Zefania index fetch returned HTTP ${response.status.value}",
                tags = mapOf("subsystem" to "zefania_index")
            )
            return cached?.let { IndexOutcome.Success(it, stale = true) } ?: IndexOutcome.Failure
        }
        return null
    }

    /** Parses a git-tree response body. Pure — this is where nearly all the behaviour lives. */
    internal fun parseIndex(body: String, etag: String = ""): IndexOutcome = try {
        val response = json.decodeFromString(TreeResponse.serializer(), body)
        if (response.truncated) {
            // A partial list rendered as if complete would silently hide translations.
            CrashReporter.reportWarning(
                "Zefania index was truncated by the API",
                tags = mapOf("subsystem" to "zefania_index")
            )
            IndexOutcome.Failure
        } else {
            IndexOutcome.Success(Index(parseTree(response.tree), etag))
        }
    } catch (e: IllegalArgumentException) {
        BibleInstallSupport.reported(
            "Zefania index could not be parsed",
            e,
            mapOf("subsystem" to "zefania_index"),
            IndexOutcome.Failure,
        )
    }

    /**
     * Turns tree entries into browse rows.
     *
     * Sorted by path and deduplicated in that fixed order, so every machine derives the same stem
     * for the same module. That determinism is what lets the dialog show "Installed" by comparing
     * against the files on disk, with nothing pinned anywhere.
     */
    internal fun parseTree(entries: List<TreeEntry>): List<Module> {
        val taken = mutableSetOf<String>()
        return entries
            .asSequence()
            .filter {
                it.type == "blob" && it.path.startsWith(BIBLES_PREFIX) &&
                    it.path.endsWith(".zip", ignoreCase = true)
            }
            .sortedBy { it.path }
            .mapNotNull { entry ->
                val module = parseEntry(entry) ?: return@mapNotNull null
                val stem = BibleCatalogNaming.deduplicate(module.fileStem, taken)
                taken.add(stem)
                module.copy(fileStem = stem)
            }
            .toList()
    }

    /**
     * Language comes from the **directory**, not the file name: the directory is how the archive is
     * curated, and a handful of modules disagree with their own file name.
     */
    internal fun parseEntry(entry: TreeEntry): Module? {
        val relative = entry.path.removePrefix(BIBLES_PREFIX)
        val language = relative.substringBefore('/', "").trim().uppercase()
        if (language.isEmpty() || !relative.contains('/')) return null

        val fileName = entry.path.substringAfterLast('/')
        val match = NAME_REGEX.matchEntire(fileName)

        val identifier: String
        val displayName: String
        val releaseDate: String
        if (match != null) {
            // A few are underscore-separated; normalise so the two shapes render alike.
            releaseDate = match.groupValues[1].replace('_', '-')
            identifier = match.groupValues[REGEX_GROUP_IDENTIFIER]
            // A handful of modules carry no parenthesised title; the identifier is all there is.
            displayName = match.groupValues[REGEX_GROUP_DISPLAY_NAME].ifBlank { identifier }.replace('_', ' ').trim()
        } else {
            // Never drop a module just because its name doesn't follow the convention — fall back
            // to something usable rather than making a translation invisible.
            releaseDate = ""
            identifier = fileName.removeSuffix(".zip").substringAfterLast('_')
            displayName = fileName.removeSuffix(".zip").replace('_', ' ').trim()
        }

        return Module(
            path = entry.path,
            blobSha = entry.sha,
            sizeBytes = entry.size,
            language = language,
            identifier = identifier,
            displayName = displayName.ifBlank { fileName.removeSuffix(".zip") },
            releaseDate = releaseDate,
            fileStem = BibleCatalogNaming.fileStem(language, identifier)
        )
    }

    private fun readCache(cacheFile: File): Index? {
        if (!cacheFile.isFile) return null
        val body = runCatching { cacheFile.readText() }.getOrNull() ?: return null
        return (parseIndex(body, readMeta(cacheFile).second) as? IndexOutcome.Success)?.index
    }

    private fun readMeta(cacheFile: File) = BibleInstallSupport.BibleIndexCache.readMeta(cacheFile)

    private fun writeMeta(cacheFile: File, fetchedAt: Long, etag: String) =
        BibleInstallSupport.BibleIndexCache.writeMeta(cacheFile, fetchedAt, etag)
}
