package org.churchpresenter.bibleformats.catalog

import org.churchpresenter.bibleformats.BibleCatalogNaming
import org.churchpresenter.bibleformats.UsfxToSpbConverter
import org.churchpresenter.bibleformats.XmlToSpbConverter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter
import java.io.File
import java.io.IOException
import org.xml.sax.SAXException
import java.nio.channels.UnresolvedAddressException

/**
 * The outcome for a catalogue fetch that did not complete: the list as it was last seen, or a
 * failure.
 *
 * One function rather than a body per catch arm — the three of them (network, DNS, a body that
 * stopped short) differ only in the exception they name, and writing the mapping once is what keeps
 * `fetchCatalog` readable as the sequence it is. Top-level rather than a member because the object
 * is already at detekt's function ceiling, and this is a mapping rather than part of its interface.
 */
private fun catalogFetchFailed(e: Throwable, cached: List<BibleModule>?): BibleCatalogOutcome =
    BibleInstallSupport.reported(
        "eBible catalogue fetch failed",
        e,
        mapOf("subsystem" to "ebible_catalog"),
        cached?.let { BibleCatalogOutcome.Success(it, stale = true) } ?: BibleCatalogOutcome.NetworkError,
    )

/**
 * eBible.org — the primary archive: around 1,300 translations across a thousand languages.
 *
 * Two things make it the better source. Its catalogue publishes a `Redistributable` flag and a
 * copyright string per translation, so the list can say what a translation is licensed for
 * **before** anything is downloaded, and only translations the publisher has marked redistributable
 * are ever offered. And each download ships a `BookNames.xml` giving book names in the
 * translation's own language, which removes the guesswork the Zefania path needs curated tables for.
 *
 * Downloads are USFX; see [UsfxToSpbConverter].
 */
object EBibleSource : BibleSource {


    override val sourceId = BibleSourceId.EBIBLE

    private const val CATALOG_URL = "https://ebible.org/Scriptures/translations.csv"
    private const val DOWNLOAD_BASE = "https://ebible.org/Scriptures"

    /** The archive is revised in batches, not continuously; a week keeps traffic negligible. */
    private const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    private val defaultCacheFile = File(System.getProperty("user.home"), ".churchpresenter/cache/ebible-catalog.csv")

    @Volatile
    private var memoryCache: Pair<Long, List<BibleModule>>? = null

    /** `-Dchurchpresenter.ebibleCatalogUrl=` points at a staging catalogue. For testing only. */
    internal fun catalogUrl(): String =
        System.getProperty("churchpresenter.ebibleCatalogUrl")?.takeIf { it.isNotBlank() } ?: CATALOG_URL

    internal fun downloadUrlFor(translationId: String): String = "$DOWNLOAD_BASE/${translationId}_usfx.zip"

    @Volatile
    private var languageNameCache: Map<String, LanguageNaming>? = null

    internal fun clearMemoryCache() {
        memoryCache = null
        languageNameCache = null
    }

    /**
     * Uppercase language code to its names, for every language this catalogue carries.
     *
     * Reads whatever is already known — the parsed catalogue in memory, else the copy on disk — and
     * **never fetches**: it exists so the Zefania tab can name its languages, and making that tab
     * reach out to a second host to do so would be a worse trade than showing a bare code.
     */
    internal suspend fun cachedLanguageNames(cacheFile: File = defaultCacheFile): Map<String, LanguageNaming> =
        languageNameCache ?: withContext(Dispatchers.IO) {
            val modules = memoryCache?.second ?: readCache(cacheFile).orEmpty()
            // Either spelling on its own is worth keeping, so this is an or, not an and.
            modules.filter { it.languageName.isNotBlank() || it.languageNativeName.isNotBlank() }
                .associate { it.language to LanguageNaming(it.languageName, it.languageNativeName) }
                .also { languageNameCache = it }
        }

    override suspend fun catalog(nowMillis: Long): BibleCatalogOutcome =
        fetchCatalog(nowMillis = nowMillis)

    internal suspend fun fetchCatalog(
        url: String = catalogUrl(),
        http: HttpClient = BibleInstallSupport.defaultHttp,
        cacheFile: File = defaultCacheFile,
        nowMillis: Long = System.currentTimeMillis(),
    ): BibleCatalogOutcome = withContext(Dispatchers.IO) {
        memoryCache?.let { (fetchedAt, modules) ->
            if (nowMillis - fetchedAt < CACHE_TTL_MILLIS) return@withContext BibleCatalogOutcome.Success(modules)
        }

        val cached = readCache(cacheFile)
        if (cached != null && nowMillis - readFetchedAt(cacheFile) < CACHE_TTL_MILLIS) {
            memoryCache = nowMillis to cached
            return@withContext BibleCatalogOutcome.Success(cached)
        }

        try {
            val response = http.get(url) { header("User-Agent", "ChurchPresenter") }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "eBible catalogue returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "ebible_catalog")
                )
                return@withContext cached?.let { BibleCatalogOutcome.Success(it, stale = true) }
                    ?: BibleCatalogOutcome.Failure
            }
            val body = response.body<String>()
            val modules = parseCatalog(body)
            if (modules.isEmpty()) {
                CrashReporter.reportWarning(
                    "eBible catalogue parsed to nothing",
                    tags = mapOf("subsystem" to "ebible_catalog")
                )
                return@withContext cached?.let { BibleCatalogOutcome.Success(it, stale = true) }
                    ?: BibleCatalogOutcome.Failure
            }
            memoryCache = nowMillis to modules
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(body)
                File(cacheFile.parentFile, cacheFile.name + ".meta").writeText(nowMillis.toString())
            }
            BibleCatalogOutcome.Success(modules)
        } catch (e: CancellationException) {
            // Closing the download browser cancels the fetch. That is the user's doing, not a fault.
            throw e
        } catch (e: IOException) {
            // ktor does not always deliver that cancellation as a CancellationException: a request
            // suspended when the scope dies surfaces as an IOException over a channel closed
            // underneath it, and the arm above never sees it. Asking the context whether it is
            // still alive is what tells "the user closed the dialog" from "the network failed" —
            // without it, closing the browser filed "eBible catalogue fetch failed" against
            // someone who had merely changed their mind.
            currentCoroutineContext().ensureActive()
            catalogFetchFailed(e, cached)
        } catch (e: UnresolvedAddressException) {
            currentCoroutineContext().ensureActive()
            catalogFetchFailed(e, cached)
        } catch (e: IllegalStateException) {
            // A catalogue body is buffered whole, so a connection that drops mid-response surfaces
            // as ktor's own Content-Length check rather than as an IOException — and, uncaught,
            // as a crash out of this coroutine.
            if (!with(BibleInstallSupport) { e.isContentLengthMismatch() }) throw e
            currentCoroutineContext().ensureActive()
            catalogFetchFailed(e, cached)
        }
    }

    /**
     * Reads the published CSV into browse rows.
     *
     * Only rows the publisher marks redistributable *and* downloadable are returned — the archive
     * catalogues translations it is not permitted to hand out, and offering those would put the app
     * in the position of distributing them.
     */
    internal fun parseCatalog(body: String): List<BibleModule> {
        val rows = Csv.parse(body)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().trim('﻿') }
        fun index(name: String) = header.indexOfFirst { it.equals(name, ignoreCase = true) }

        val idIndex = index("translationId")
        val languageIndex = index("languageCode")
        if (idIndex < 0 || languageIndex < 0) return emptyList()
        val titleIndex = index("shortTitle")
        val longTitleIndex = index("title")
        val copyrightIndex = index("Copyright")
        val redistributableIndex = index("Redistributable")
        val downloadableIndex = index("downloadable")
        val updateIndex = index("UpdateDate")
        // Deliberately not part of the required-column guard above: a catalogue that stopped
        // publishing these should lose the names, not the whole list.
        val languageNameEnglishIndex = index("languageNameInEnglish")
        val languageNameIndex = index("languageName")
        val otBooksIndex = index("OTbooks")
        val ntBooksIndex = index("NTbooks")

        val taken = mutableSetOf<String>()
        return rows.drop(1)
            .mapNotNull { row ->
                fun cell(i: Int) = row.getOrNull(i)?.trim().orEmpty()
                val translationId = cell(idIndex)
                if (translationId.isEmpty()) return@mapNotNull null
                if (!cell(redistributableIndex).isTrue() || !cell(downloadableIndex).isTrue()) return@mapNotNull null

                val language = cell(languageIndex).uppercase()
                val title = cell(titleIndex).ifBlank { cell(longTitleIndex) }.ifBlank { translationId }
                BibleModule(
                    sourceId = BibleSourceId.EBIBLE,
                    downloadKey = translationId,
                    sizeBytes = 0,
                    language = language,
                    // English first, because the autonym is no help to someone typing "english" —
                    // but both are kept, since it is the only spelling a speaker of the language
                    // would think to type. A row publishing no English name falls back to the
                    // autonym rather than showing a bare code.
                    languageName = cell(languageNameEnglishIndex).ifBlank { cell(languageNameIndex) },
                    languageNativeName = cell(languageNameIndex),
                    identifier = translationId,
                    displayName = title,
                    copyright = cell(copyrightIndex),
                    releaseDate = cell(updateIndex),
                    // What the translation actually contains, so the browse list doesn't have to
                    // read it off the title.
                    otBookCount = cell(otBooksIndex).toIntOrNull() ?: 0,
                    ntBookCount = cell(ntBooksIndex).toIntOrNull() ?: 0,
                    fileStem = BibleCatalogNaming.fileStem(language, translationId)
                )
            }
            // Deduplicated in catalogue order, so every machine derives the same installed names.
            .map { module ->
                val stem = BibleCatalogNaming.deduplicate(module.fileStem, taken)
                taken.add(stem)
                module.copy(fileStem = stem)
            }
    }

    private fun String.isTrue(): Boolean = lowercase() in setOf("true", "yes", "1")

    private fun readCache(cacheFile: File): List<BibleModule>? {
        if (!cacheFile.isFile) return null
        val body = runCatching { cacheFile.readText() }.getOrNull() ?: return null
        return parseCatalog(body).takeIf { it.isNotEmpty() }
    }

    private fun readFetchedAt(cacheFile: File): Long =
        runCatching { File(cacheFile.parentFile, cacheFile.name + ".meta").readText().trim().toLong() }
            .getOrDefault(0L)

    override suspend fun install(
        module: BibleModule,
        targetDir: File,
        onProgress: (InstallProgress) -> Unit,
    ): BibleInstallOutcome =
        installEBible(module, targetDir, BibleInstallSupport.defaultHttp, onProgress = onProgress)

    @Suppress("LongMethod") // download -> convert -> install is one pipeline; a split buys no seam

    internal suspend fun installEBible(
        module: BibleModule,
        targetDir: File,
        http: HttpClient,
        retryFloorMs: Long = BibleInstallSupport.DEFAULT_DOWNLOAD_RETRY_FLOOR_MS,
        onProgress: (InstallProgress) -> Unit,
    ): BibleInstallOutcome = withContext(Dispatchers.IO) {
        if (!BibleInstallSupport.usableDirectory(targetDir)) return@withContext BibleInstallOutcome.NoDirectory

        val scratch = BibleInstallSupport.scratchIn(targetDir)
        try {
            scratch.deleteRecursively()
            scratch.mkdirs()
            val zipFile = File(scratch, "module.zip")
            val spbPart = File(scratch, module.fileName)

            val result = try {
                BibleInstallSupport.downloadTo(
                    url = downloadUrlFor(module.downloadKey),
                    destination = zipFile,
                    http = http,
                    expectedBytes = module.sizeBytes,
                    retryFloorMs = retryFloorMs,
                ) { onProgress(InstallProgress(InstallPhase.DOWNLOADING, it)) }
            } catch (e: CancellationException) {
                // Closing the dialog cancels the install. That is the user's doing, not a fault.
                throw e
            } catch (e: BibleInstallSupport.DownloadStalledException) {
                CrashReporter.reportWarning(
                    "eBible download stalled (${module.fileStem})",
                    throwable = e,
                    tags = mapOf(
                        "subsystem" to "bible_install",
                        "module" to module.fileStem,
                        "reason" to "stalled",
                        "attempts" to e.attempts.toString(),
                        "bytes_written" to e.bytesWritten.toString(),
                        "expected_bytes" to module.sizeBytes.toString(),
                    )
                )
                return@withContext BibleInstallOutcome.DownloadStalled
            } catch (e: IOException) {
                return@withContext BibleInstallSupport.reported(
                    "eBible download failed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.NetworkError,
                )
            } catch (e: UnresolvedAddressException) {
                return@withContext BibleInstallSupport.reported(
                    "eBible download failed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.NetworkError,
                )
            }

            if (result.status !in 200..299) {
                CrashReporter.reportWarning(
                    "eBible download returned HTTP ${result.status} (${module.fileStem})",
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.HttpError(result.status)
            }

            onProgress(InstallProgress(InstallPhase.EXTRACTING, BibleInstallSupport.DOWNLOAD_END))
            // The scripture and the book-name list are separate entries in the same archive.
            val entries = BibleInstallSupport.extractEntries(zipFile, scratch) {
                it.endsWith("_usfx.xml", ignoreCase = true) || it.equals("BookNames.xml", ignoreCase = true)
            }
            val usfx = entries.entries.firstOrNull { it.key.endsWith("_usfx.xml", ignoreCase = true) }?.value
                ?: return@withContext BibleInstallOutcome.CorruptArchive
            val bookNames = entries.entries.firstOrNull { it.key.equals("BookNames.xml", ignoreCase = true) }?.value
            onProgress(InstallProgress(InstallPhase.EXTRACTING, BibleInstallSupport.EXTRACT_END))

            val parsed = try {
                UsfxToSpbConverter.parse(
                    usfxFile = usfx,
                    bookNamesFile = bookNames,
                    name = module.displayName,
                    language = module.language,
                    rights = module.copyright,
                    source = downloadUrlFor(module.downloadKey)
                )
            } catch (e: IOException) {
                return@withContext BibleInstallSupport.reported(
                    "eBible module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            } catch (e: SAXException) {
                return@withContext BibleInstallSupport.reported(
                    "eBible module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            } catch (e: IllegalArgumentException) {
                return@withContext BibleInstallSupport.reported(
                    "eBible module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            }
            if (!parsed.hasVerses) {
                return@withContext BibleInstallOutcome.ConversionFailed
            }

            XmlToSpbConverter.write(parsed, spbPart) { fraction ->
                onProgress(
                    InstallProgress(
                        InstallPhase.CONVERTING,
                        BibleInstallSupport.EXTRACT_END +
                            (BibleInstallSupport.CONVERT_END - BibleInstallSupport.EXTRACT_END) * fraction
                    )
                )
            }
            if (!BibleInstallSupport.looksLikeModule(spbPart)) return@withContext BibleInstallOutcome.ConversionFailed

            onProgress(InstallProgress(InstallPhase.INSTALLING, BibleInstallSupport.CONVERT_END))
            val destination = File(targetDir, module.fileName)
            try {
                BibleInstallSupport.moveIntoPlace(spbPart, destination)
            } catch (e: IOException) {
                return@withContext BibleInstallSupport.reported(
                    "Could not write Bible into place (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.WriteFailed,
                )
            } catch (e: SecurityException) {
                return@withContext BibleInstallSupport.reported(
                    "Could not write Bible into place (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.WriteFailed,
                )
            }
            onProgress(InstallProgress(InstallPhase.INSTALLING, 1f))
            BibleInstallOutcome.Success(destination, parsed.name, parsed.books.size, parsed.rights)
        } finally {
            scratch.deleteRecursively()
        }
    }
}

/**
 * Just enough CSV for the eBible catalogue: quoted fields, embedded commas, doubled quotes.
 *
 * A dependency would be overkill for one file, but hand-splitting on commas is not an option —
 * copyright strings in this catalogue routinely contain both commas and quotes.
 */
internal object Csv {
    fun parse(body: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < body.length) {
            val char = body[index]
            when {
                quoted && char == '"' && body.getOrNull(index + 1) == '"' -> {
                    field.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    row.add(field.toString()); field.setLength(0)
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (field.isNotEmpty() || row.isNotEmpty()) {
                        row.add(field.toString()); field.setLength(0)
                        rows.add(row.toList()); row.clear()
                    }
                    if (char == '\r' && body.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row.toList())
        }
        return rows
    }
}
