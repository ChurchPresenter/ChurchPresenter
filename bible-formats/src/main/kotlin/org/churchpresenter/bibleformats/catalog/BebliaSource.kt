package org.churchpresenter.bibleformats.catalog

import org.churchpresenter.bibleformats.BookNames
import org.churchpresenter.bibleformats.XmlToSpbConverter
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter
import java.io.File
import javax.xml.stream.XMLStreamException
import java.io.IOException
import org.xml.sax.SAXException
import java.nio.channels.UnresolvedAddressException

/**
 * The Holy Bible XML archive: 1048 translations across more than 200 languages, many of which
 * neither [EBibleSource] nor [ZefaniaSource] carries.
 *
 * It is the only one of the three whose files are not archives — each translation is a single bare
 * XML document, so nothing is unzipped and [InstallPhase.EXTRACTING] never appears. It is also the
 * only one that both publishes a copyright up front *and* has none of it verified: eBible states
 * redistribution rights, Zefania states nothing at all, and this archive republishes whatever each
 * contributor wrote. So its rows show a copyright and still carry the unverified licence badge.
 *
 * See [BebliaCatalogIndex] for where the catalogue comes from, and `converter.BebliaParser` for the
 * format and for why book names come out in English for most of these languages.
 */
object BebliaSource : BibleSource {


    override val sourceId = BibleSourceId.BEBLIA

    override suspend fun catalog(nowMillis: Long): BibleCatalogOutcome =
        when (val outcome = BebliaCatalogIndex.fetch(nowMillis = nowMillis)) {
            is BebliaCatalogIndex.IndexOutcome.Success -> {
                // The manifest names languages by code alone, so the names come from elsewhere.
                val languageNames = BibleLanguageNames.table()
                BibleCatalogOutcome.Success(
                    outcome.index.modules.map { it.toBibleModule(outcome.index.commit, languageNames) },
                    outcome.stale
                )
            }
            BebliaCatalogIndex.IndexOutcome.NetworkError -> BibleCatalogOutcome.NetworkError
            BebliaCatalogIndex.IndexOutcome.Failure -> BibleCatalogOutcome.Failure
        }

    /**
     * [BibleModule.downloadKey] is `<commit>/<file>` so a row resolves on its own.
     *
     * A user who opens the dialog, waits while the catalogue refreshes behind them and then installs
     * still gets the blob whose hash their row published, rather than a newer file that would fail
     * the checksum.
     */
    internal fun BebliaCatalogIndex.Module.toBibleModule(
        commit: String,
        languageNames: Map<String, LanguageNaming> = emptyMap()
    ) = BibleModule(
        sourceId = BibleSourceId.BEBLIA,
        downloadKey = "$commit/$file",
        checksum = blobSha,
        sizeBytes = sizeBytes,
        language = language,
        // The shared table wins where it has the code — it is what the other two tabs label the same
        // language with, and it carries autonyms — but it knows a fraction of what this archive
        // reaches, so the manifest's own name fills the rest rather than leaving a bare code.
        languageName = languageNames[language]?.english?.ifBlank { null } ?: languageName,
        languageNativeName = languageNames[language]?.native.orEmpty(),
        identifier = identifier,
        displayName = displayName,
        copyright = copyright,
        otBookCount = otBookCount,
        ntBookCount = ntBookCount,
        fileStem = fileStem
    )

    /**
     * Whether an installed translation will list its books in its own language.
     *
     * These files identify books by a bare number, so the names come from the app's own tables — and
     * those cover under twenty languages. Everything else gets native verse text under English book
     * names, which is worth saying before the download rather than after it.
     */
    fun hasLocalisedBookNames(language: String): Boolean =
        language.trim().uppercase() in BookNames.LANGUAGE_LOOKUPS

    override suspend fun install(
        module: BibleModule,
        targetDir: File,
        onProgress: (InstallProgress) -> Unit,
    ): BibleInstallOutcome =
        installBeblia(module, targetDir, BibleInstallSupport.defaultHttp, onProgress = onProgress)

    @Suppress("LongMethod") // download -> convert -> install is one pipeline; a split buys no seam

    internal suspend fun installBeblia(
        module: BibleModule,
        targetDir: File,
        http: HttpClient,
        /** The download's first backoff delay; 0 in tests, so a failing host costs no wall clock. */
        retryFloorMs: Long = BibleInstallSupport.DEFAULT_DOWNLOAD_RETRY_FLOOR_MS,
        onProgress: (InstallProgress) -> Unit,
    ): BibleInstallOutcome = withContext(Dispatchers.IO) {
        if (!BibleInstallSupport.usableDirectory(targetDir)) return@withContext BibleInstallOutcome.NoDirectory

        val scratch = BibleInstallSupport.scratchIn(targetDir)
        try {
            scratch.deleteRecursively()
            scratch.mkdirs()
            val xmlFile = File(scratch, "module.xml")
            val spbPart = File(scratch, module.fileName)

            val commit = module.downloadKey.substringBefore('/')
            val file = module.downloadKey.substringAfter('/')

            val result = try {
                BibleInstallSupport.downloadTo(
                    url = BebliaCatalogIndex.rawUrlFor(commit, file),
                    destination = xmlFile,
                    http = http,
                    expectedBytes = module.sizeBytes,
                    retryFloorMs = retryFloorMs,
                ) { onProgress(InstallProgress(InstallPhase.DOWNLOADING, it)) }
            } catch (e: CancellationException) {
                // Closing the dialog cancels the install. That is the user's doing, not a fault.
                throw e
            } catch (e: BibleInstallSupport.DownloadStalledException) {
                CrashReporter.reportWarning(
                    "Holy Bible XML download stalled (${module.fileStem})",
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
                    "Holy Bible XML download failed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.NetworkError,
                )
            } catch (e: UnresolvedAddressException) {
                return@withContext BibleInstallSupport.reported(
                    "Holy Bible XML download failed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.NetworkError,
                )
            }

            if (result.status !in 200..299) {
                CrashReporter.reportWarning(
                    "Holy Bible XML download returned HTTP ${result.status} (${module.fileStem})",
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.HttpError(result.status)
            }
            if (module.sizeBytes > 0 && result.bytesWritten != module.sizeBytes) {
                return@withContext BibleInstallOutcome.ChecksumMismatch
            }
            // The git blob hash comes free with the manifest, so integrity is checked end to end
            // against a hash the archive itself published — and it rejects the two bodies a file host
            // hands back instead of a module, a 404 page and an LFS pointer.
            if (module.checksum.isNotBlank() &&
                !BibleInstallSupport.gitBlobSha1(xmlFile).equals(module.checksum, ignoreCase = true)
            ) {
                return@withContext BibleInstallOutcome.ChecksumMismatch
            }

            val parsed = try {
                XmlToSpbConverter.parseBeblia(
                    xmlFile = xmlFile,
                    language = module.language,
                    name = module.displayName,
                    rights = module.copyright,
                    source = BebliaCatalogIndex.rawUrlFor(commit, file),
                    identifier = module.identifier,
                ) { fraction ->
                    onProgress(
                        InstallProgress(
                            InstallPhase.CONVERTING,
                            BibleInstallSupport.DOWNLOAD_END +
                                (BibleInstallSupport.PARSE_END - BibleInstallSupport.DOWNLOAD_END) * fraction
                        )
                    )
                }
            } catch (e: XMLStreamException) {
                // Not XML at all: a captive portal's login page, or a truncated body that still
                // matched no published size. "Damaged download" is the useful thing to say.
                CrashReporter.reportWarning(
                    "Holy Bible XML module was not well-formed (${module.fileStem})",
                    throwable = e,
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.CorruptArchive
            } catch (e: IOException) {
                return@withContext BibleInstallSupport.reported(
                    "Holy Bible XML module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            } catch (e: SAXException) {
                return@withContext BibleInstallSupport.reported(
                    "Holy Bible XML module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            } catch (e: IllegalArgumentException) {
                return@withContext BibleInstallSupport.reported(
                    "Holy Bible XML module could not be parsed (${module.fileStem})",
                    e,
                    mapOf("subsystem" to "bible_install", "module" to module.fileStem),
                    BibleInstallOutcome.ConversionFailed,
                )
            }
            if (parsed.books.isEmpty() || parsed.books.sumOf { b -> b.chapters.sumOf { it.verses.size } } == 0) {
                return@withContext BibleInstallOutcome.ConversionFailed
            }

            XmlToSpbConverter.write(parsed, spbPart) { fraction ->
                onProgress(
                    InstallProgress(
                        InstallPhase.CONVERTING,
                        BibleInstallSupport.PARSE_END +
                            (BibleInstallSupport.CONVERT_END - BibleInstallSupport.PARSE_END) * fraction
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
