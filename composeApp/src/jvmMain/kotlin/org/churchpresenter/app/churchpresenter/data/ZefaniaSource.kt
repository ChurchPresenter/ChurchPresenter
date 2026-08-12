package org.churchpresenter.app.churchpresenter.data

import converter.bible.XmlToSpbConverter
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.io.File

/**
 * The Zefania XML archive: a preservation mirror of the SourceForge Zefania project.
 *
 * Smaller and less consistently licensed than [EBibleSource], but it carries translations eBible
 * doesn't, so it stays on offer as a second tab. Each module is one XML file inside a zip, and the
 * copyright is only visible once that file has been parsed — which is why it is reported after the
 * install rather than in the list.
 */
object ZefaniaSource : BibleSource {

    override val sourceId = BibleSourceId.ZEFANIA

    override suspend fun catalog(nowMillis: Long): BibleCatalogOutcome =
        when (val outcome = ZefaniaRepositoryIndex.fetch(nowMillis = nowMillis)) {
            is ZefaniaRepositoryIndex.IndexOutcome.Success -> {
                // The archive names its folders by code alone, so the names come from elsewhere.
                val languageNames = BibleLanguageNames.table()
                BibleCatalogOutcome.Success(
                    outcome.index.modules.map { it.toBibleModule(languageNames) },
                    outcome.stale
                )
            }
            is ZefaniaRepositoryIndex.IndexOutcome.RateLimited ->
                BibleCatalogOutcome.RateLimited(outcome.resetEpochSeconds)
            ZefaniaRepositoryIndex.IndexOutcome.NetworkError -> BibleCatalogOutcome.NetworkError
            ZefaniaRepositoryIndex.IndexOutcome.Failure -> BibleCatalogOutcome.Failure
        }

    internal fun ZefaniaRepositoryIndex.Module.toBibleModule(
        languageNames: Map<String, LanguageNaming> = emptyMap()
    ) = BibleModule(
        sourceId = BibleSourceId.ZEFANIA,
        downloadKey = path,
        checksum = blobSha,
        sizeBytes = sizeBytes,
        language = language,
        languageName = languageNames[language]?.english.orEmpty(),
        languageNativeName = languageNames[language]?.native.orEmpty(),
        identifier = identifier,
        displayName = displayName,
        releaseDate = releaseDate,
        fileStem = fileStem
    )

    override suspend fun install(
        module: BibleModule,
        targetDir: File,
        onProgress: (InstallProgress) -> Unit,
    ): BibleInstallOutcome =
        installZefania(module, targetDir, BibleInstallSupport.defaultHttp, onProgress = onProgress)

    internal suspend fun installZefania(
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
                    url = ZefaniaRepositoryIndex.rawUrlFor(module.downloadKey),
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
                    "Zefania download stalled (${module.fileStem})",
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
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Zefania download failed (${module.fileStem})",
                    throwable = e,
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.NetworkError
            }

            if (result.status !in 200..299) {
                CrashReporter.reportWarning(
                    "Zefania download returned HTTP ${result.status} (${module.fileStem})",
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.HttpError(result.status)
            }
            if (module.sizeBytes > 0 && result.bytesWritten != module.sizeBytes) {
                return@withContext BibleInstallOutcome.ChecksumMismatch
            }
            // The git blob hash comes free with the tree listing, so integrity is checked end to
            // end without the archive publishing a checksum of its own. It also rejects the two
            // bodies a file host hands back instead of a module — a 404 page and an LFS pointer.
            if (module.checksum.isNotBlank() &&
                !BibleInstallSupport.gitBlobSha1(zipFile).equals(module.checksum, ignoreCase = true)
            ) {
                return@withContext BibleInstallOutcome.ChecksumMismatch
            }

            onProgress(InstallProgress(InstallPhase.EXTRACTING, BibleInstallSupport.DOWNLOAD_END))
            val xmlFile = BibleInstallSupport
                .extractEntries(zipFile, scratch) { it.endsWith(".xml", ignoreCase = true) }
                .values.maxByOrNull { it.length() }
                ?: return@withContext BibleInstallOutcome.CorruptArchive
            onProgress(InstallProgress(InstallPhase.EXTRACTING, BibleInstallSupport.EXTRACT_END))

            val parsed = try {
                XmlToSpbConverter.parse(xmlFile)
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Zefania module could not be parsed (${module.fileStem})",
                    throwable = e,
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.ConversionFailed
            }
            if (parsed.books.isEmpty() || parsed.books.sumOf { b -> b.chapters.sumOf { it.verses.size } } == 0) {
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
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Could not write Bible into place (${module.fileStem})",
                    throwable = e,
                    tags = mapOf("subsystem" to "bible_install", "module" to module.fileStem)
                )
                return@withContext BibleInstallOutcome.WriteFailed
            }
            onProgress(InstallProgress(InstallPhase.INSTALLING, 1f))
            return@withContext BibleInstallOutcome.Success(
                destination,
                parsed.name,
                parsed.books.size,
                parsed.rights
            )
        } finally {
            scratch.deleteRecursively()
        }
    }
}
