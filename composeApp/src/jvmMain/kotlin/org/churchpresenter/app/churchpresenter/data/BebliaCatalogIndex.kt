package org.churchpresenter.app.churchpresenter.data

import converter.BibleCatalogNaming
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.io.File
import java.net.URLEncoder

/**
 * Lists the Bible modules available in the Holy Bible XML archive.
 *
 * Unlike the Zefania archive, whose file names carry everything a browse list needs, this one is
 * 1048 flat `.xml` files named only `<Language><Edition>Bible.xml`, with the title, the copyright and
 * the book counts buried inside files that average five megabytes. Neither a tree listing nor a
 * ranged read would recover them.
 *
 * So the archive publishes a generated `catalog.json` alongside its files — see
 * `scripts/generate_beblia_catalog.py`, which builds it — and this fetches that one small document.
 * It is a curated index rather than a derived one, which is a real cost: it has to be regenerated
 * when the archive's content changes. What it buys is a browse list that states each translation's
 * actual copyright *before* anything is downloaded, and testament coverage taken from the file's real
 * contents rather than guessed from its title.
 *
 * The manifest names the commit its blob hashes were read at, and downloads are pinned to that
 * commit. Without the pin, an upstream edit after generation would hand every user a checksum
 * mismatch — reported as "the download was incomplete" — until someone regenerated the manifest.
 * Pinned, a lagging manifest simply serves the older file it actually describes.
 */
object BebliaCatalogIndex {

    const val OWNER = "ChurchPresenter"
    const val REPO = "Holy-Bible-XML-Format"
    const val BRANCH = "master"

    /** One row of the browse list, taken entirely from the manifest. */
    data class Module(
        val file: String,
        val blobSha: String,
        val sizeBytes: Long,
        val language: String,
        /**
         * What the manifest calls [language] in English; blank where it could not name it.
         *
         * This archive reaches languages neither eBible nor [BibleLanguageNames] has ever listed —
         * over 250 codes against the Zefania archive's 63 — so without a name of its own the filter
         * would show a bare code for most of them.
         */
        val languageName: String,
        val identifier: String,
        val displayName: String,
        /** The translation's own copyright statement, which this archive publishes up front. */
        val copyright: String,
        val sourceUrl: String,
        val otBookCount: Int,
        val ntBookCount: Int,
        val fileStem: String
    ) {
        val fileName: String get() = "$fileStem.${Constants.EXTENSION_SPB}"
    }

    /** [commit] is what downloads are pinned to, so it travels with the modules it describes. */
    data class Index(val commit: String, val modules: List<Module>, val etag: String = "")

    /**
     * No `RateLimited` case, deliberately: this comes off `raw.githubusercontent.com`, a CDN with no
     * API quota, where the Zefania listing comes off the GitHub API's 60-requests-per-hour limit.
     */
    sealed interface IndexOutcome {
        /** [stale] means this came from the on-disk copy because the archive was unreachable. */
        data class Success(val index: Index, val stale: Boolean = false) : IndexOutcome
        data object NetworkError : IndexOutcome
        data object Failure : IndexOutcome
    }

    // --- manifest DTOs ---

    @Serializable
    internal data class Entry(
        val file: String = "",
        val sha: String = "",
        val size: Long = 0,
        val title: String = "",
        val id: String = "",
        val lang: String = "",
        /** English name for [lang], where the generator could resolve one. */
        val langName: String = "",
        /** How the generator arrived at [lang]; recorded for review, never shown. */
        val langFrom: String = "",
        val rights: String = "",
        val url: String = "",
        val ot: Int = 0,
        val nt: Int = 0
    )

    /**
     * Every field is defaulted and unknown ones are ignored, so the archive can add a column without
     * an app release. [schemaVersion] is informational only and is never refused on — refusing would
     * strand every installed copy the moment someone bumped it. A breaking change gets a new path.
     */
    @Serializable
    internal data class CatalogFile(
        val schemaVersion: Int = 1,
        val commit: String = "",
        val bibles: List<Entry> = emptyList()
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

    private val defaultCacheFile = File(System.getProperty("user.home"), ".churchpresenter/cache/beblia-catalog.json")

    /** The archive is a preservation project, not a feed; it changes a few times a year. */
    private const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    @Volatile
    private var memoryCache: Pair<Long, Index>? = null

    /** `-Dchurchpresenter.bebliaCatalogUrl=` points at a staging archive. For testing, not a setting. */
    internal fun catalogUrl(): String =
        System.getProperty("churchpresenter.bebliaCatalogUrl")?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/catalog.json"

    internal fun rawBase(): String =
        System.getProperty("churchpresenter.bebliaRawBase")?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/$OWNER/$REPO"

    /** [commit] rather than the branch, so the bytes match the hash the manifest published. */
    fun rawUrlFor(commit: String, file: String): String =
        rawBase().trimEnd('/') + "/" + encodePath(commit) + "/" + encodePath(file)

    private fun encodePath(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    internal fun clearMemoryCache() {
        memoryCache = null
    }

    suspend fun fetch(
        url: String = catalogUrl(),
        http: HttpClient = defaultHttp,
        cacheFile: File = defaultCacheFile,
        nowMillis: Long = System.currentTimeMillis(),
    ): IndexOutcome = withContext(Dispatchers.IO) {
        memoryCache?.let { (fetchedAt, index) ->
            if (nowMillis - fetchedAt < CACHE_TTL_MILLIS) return@withContext IndexOutcome.Success(index)
        }

        val cached = readCache(cacheFile)
        val cachedAt = BibleInstallSupport.BibleIndexCache.readMeta(cacheFile).first
        if (cached != null && nowMillis - cachedAt < CACHE_TTL_MILLIS) {
            memoryCache = nowMillis to cached
            return@withContext IndexOutcome.Success(cached)
        }

        try {
            val response = http.get(url) {
                header("User-Agent", "ChurchPresenter")
                if (cached != null && cached.etag.isNotBlank()) header("If-None-Match", cached.etag)
            }

            if (response.status.value == 304 && cached != null) {
                BibleInstallSupport.BibleIndexCache.writeMeta(cacheFile, nowMillis, cached.etag)
                memoryCache = nowMillis to cached
                return@withContext IndexOutcome.Success(cached)
            }

            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Holy Bible XML catalogue fetch returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "beblia_catalog")
                )
                return@withContext cached?.let { IndexOutcome.Success(it, stale = true) } ?: IndexOutcome.Failure
            }

            val body = response.body<String>()
            val etag = response.headers["ETag"].orEmpty()
            when (val parsed = parseCatalog(body, etag)) {
                is IndexOutcome.Success -> {
                    memoryCache = nowMillis to parsed.index
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(body)
                        BibleInstallSupport.BibleIndexCache.writeMeta(cacheFile, nowMillis, etag)
                    }
                    parsed
                }
                // A manifest that arrived but did not parse is not written over a good cached copy.
                else -> cached?.let { IndexOutcome.Success(it, stale = true) } ?: parsed
            }
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Holy Bible XML catalogue fetch failed",
                throwable = e,
                tags = mapOf("subsystem" to "beblia_catalog")
            )
            // An offline hall still sees the list it saw last time; the failure then surfaces on the
            // install, which is far more useful than an empty dialog.
            cached?.let { IndexOutcome.Success(it, stale = true) } ?: IndexOutcome.NetworkError
        }
    }

    /**
     * Parses a manifest body. Pure — this is where nearly all the behaviour lives.
     *
     * Rows are sorted by file name and deduplicated in that fixed order, so every machine derives the
     * same stem for the same translation. That determinism is what lets the dialog show "Installed"
     * by comparing against the files on disk, with nothing pinned anywhere. The stem is deliberately
     * *not* carried in the manifest: keeping the naming rule in [BibleCatalogNaming] alone is what
     * stops the generator drifting from the app.
     */
    internal fun parseCatalog(body: String, etag: String = ""): IndexOutcome = try {
        val catalog = json.decodeFromString(CatalogFile.serializer(), body)
        val entries = catalog.bibles.filter { it.file.isNotBlank() }
        when {
            // A manifest that parsed to nothing is a truncated or half-written file, not an empty
            // archive — presenting it as an empty catalogue would look like a working, bare tab.
            entries.isEmpty() -> {
                CrashReporter.reportWarning(
                    "Holy Bible XML catalogue listed no translations",
                    tags = mapOf("subsystem" to "beblia_catalog")
                )
                IndexOutcome.Failure
            }
            // Without the commit the downloads have nothing to pin to, and pinning is what makes the
            // published hashes meaningful.
            catalog.commit.isBlank() -> {
                CrashReporter.reportWarning(
                    "Holy Bible XML catalogue named no commit",
                    tags = mapOf("subsystem" to "beblia_catalog")
                )
                IndexOutcome.Failure
            }
            else -> IndexOutcome.Success(Index(catalog.commit, toModules(entries), etag))
        }
    } catch (e: Exception) {
        CrashReporter.reportWarning(
            "Holy Bible XML catalogue could not be parsed",
            throwable = e,
            tags = mapOf("subsystem" to "beblia_catalog")
        )
        IndexOutcome.Failure
    }

    internal fun toModules(entries: List<Entry>): List<Module> {
        val taken = mutableSetOf<String>()
        return entries.sortedBy { it.file }.map { entry ->
            val language = entry.lang.trim().uppercase().ifBlank { BibleCatalogNaming.UNKNOWN_LANGUAGE }
            val stem = BibleCatalogNaming.deduplicate(
                BibleCatalogNaming.fileStem(language, entry.id),
                taken
            )
            taken.add(stem)
            Module(
                file = entry.file,
                blobSha = entry.sha,
                sizeBytes = entry.size,
                language = language,
                languageName = entry.langName.trim(),
                identifier = entry.id,
                // Never let a row arrive nameless: an untitled entry would be an unclickable blank.
                displayName = entry.title.ifBlank { entry.file.removeSuffix(".xml") },
                copyright = entry.rights,
                sourceUrl = entry.url,
                otBookCount = entry.ot,
                ntBookCount = entry.nt,
                fileStem = stem
            )
        }
    }

    private fun readCache(cacheFile: File): Index? {
        if (!cacheFile.isFile) return null
        val body = runCatching { cacheFile.readText() }.getOrNull() ?: return null
        val etag = BibleInstallSupport.BibleIndexCache.readMeta(cacheFile).second
        return (parseCatalog(body, etag) as? IndexOutcome.Success)?.index
    }
}
