package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSyncMode
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.data.BibleBookNames
import org.churchpresenter.app.churchpresenter.data.BibleLoadError
import java.io.File

/**
 * Reading modules off disk, following a settings change, and mirroring a linked instance.
 */

internal fun BibleViewModel.updateSettings(newSettings: AppSettings) {
    val previous = appSettings
    appSettings = newSettings
    if (translationReloadRequired(previous.bibleSettings, newSettings.bibleSettings)) {
        loadBibles()
    } else {
        applyTranslationOrder()
    }
}

internal fun BibleViewModel.translationReloadRequired(previous: BibleSettings, next: BibleSettings): Boolean {
    if (previous.storageDirectory != next.storageDirectory) return true
    val before = previous.translationSelectionKey()
    val after = next.translationSelectionKey()
    if (before.firstOrNull() != after.firstOrNull()) return true
    return before.toSet() != after.toSet()
}

internal fun BibleViewModel.applyTranslationOrder() {
    val current = _loadedTranslations.value
    if (current.isEmpty()) return
    val desired = appSettings.bibleSettings.translationSelectionKey()
    val reordered = desired.mapNotNull { fileName -> current.firstOrNull { it.fileName == fileName } }
    if (reordered.size != current.size) {
        loadBibles()
        return
    }
    if (reordered == current) return
    _loadedTranslations.value = reordered
    _loadedBibles.value = reordered.map { it.bible }

    _secondaryBible.value = reordered.getOrNull(1)?.bible

    if (_verses.value.isNotEmpty()) _verseSelectionToken.value++
}


internal fun BibleViewModel.invalidateInstanceLinkBibleCache() {
    val primary = File(remoteBibleCacheDir, "primary.spb")
    val secondary = File(remoteBibleCacheDir, "secondary.spb")
    val dynamicDeleted = remoteTranslationCacheFiles.fold(false) { deleted, (_, file) -> file.delete() or deleted }
    val deleted = primary.delete() or secondary.delete() or dynamicDeleted
    remoteTranslationCacheFiles = emptyList()
    InstanceLinkLogger.log(
        InstanceLinkLogSide.FOLLOWER, "cache_invalidated",
        mapOf("kind" to "bible", "deleted" to deleted)
    )
}

internal fun BibleViewModel.setInstanceLinkSource(
    active: Boolean,
    mode: BibleSyncMode,
    fetchBibleFile: (suspend () -> ByteArray?)?,
    fetchSecondaryBibleFile: (suspend () -> ByteArray?)?,
    fetchBibleTranslations: (suspend () -> List<Pair<String, ByteArray>>)? = null,
) {
    if (!active) {
        if (remoteModeActive) {
            remoteModeActive = false
            syncMode = BibleSyncMode.FULL_REPLICA
            remoteBibleCacheFile = null
            remoteSecondaryBibleCacheFile = null
            remoteTranslationCacheFiles = emptyList()
            loadBibles()
        }
        return
    }
    remoteModeActive = true
    syncMode = mode
    if (mode == BibleSyncMode.REFERENCE_ONLY) {

        remoteBibleCacheFile = null
        remoteSecondaryBibleCacheFile = null
        remoteTranslationCacheFiles = emptyList()
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "bible_sync_result",
            mapOf("mode" to mode.name, "primaryDownloaded" to false, "secondaryDownloaded" to false)
        )
        loadBibles()
        return
    }
    viewModelScope.launch {
        val translations = fetchBibleTranslations?.invoke().orEmpty()
        if (translations.isNotEmpty()) {
            remoteTranslationCacheFiles = withContext(Dispatchers.IO) {
                remoteBibleCacheDir.mkdirs()
                translations.mapIndexed { index, (fileName, bytes) ->
                    val cacheFile = File(remoteBibleCacheDir, "translation-$index.spb")
                    cacheFile.writeBytes(bytes)
                    fileName to cacheFile
                }
            }
            remoteBibleCacheFile = remoteTranslationCacheFiles.firstOrNull()?.second
            remoteSecondaryBibleCacheFile = remoteTranslationCacheFiles.getOrNull(1)?.second
            loadBibles()
            return@launch
        }
        val cacheFile = File(remoteBibleCacheDir, "primary.spb")
        var primaryDownloaded = cacheFile.exists()
        if (!cacheFile.exists()) {
            val bytes = fetchBibleFile?.invoke()
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "bible_sync_result",
                    mapOf(
                        "mode" to mode.name,
                        "primaryDownloaded" to false,
                        "secondaryDownloaded" to false,
                        "reason" to "primary_fetch_failed"
                    )
                )
                return@launch
            }
            withContext(ioDispatcher) {
                remoteBibleCacheDir.mkdirs()
                cacheFile.writeBytes(bytes)
            }
            primaryDownloaded = true
        }
        remoteBibleCacheFile = cacheFile

        val secondaryCacheFile = File(remoteBibleCacheDir, "secondary.spb")
        var secondaryDownloaded = secondaryCacheFile.exists()
        if (!secondaryCacheFile.exists()) {
            val bytes = fetchSecondaryBibleFile?.invoke()
            if (bytes != null) {
                withContext(ioDispatcher) {
                    remoteBibleCacheDir.mkdirs()
                    secondaryCacheFile.writeBytes(bytes)
                }
                secondaryDownloaded = true
            }
        }
        remoteSecondaryBibleCacheFile = secondaryCacheFile.takeIf { it.exists() }

        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "bible_sync_result",
            mapOf(
                "mode" to mode.name,
                "primaryDownloaded" to primaryDownloaded,
                "secondaryDownloaded" to secondaryDownloaded
            )
        )
        loadBibles()
    }
}

internal fun BibleViewModel.loadBibles() {
    loadChapterJob?.cancel()
    loadChapterJob = null
    val previousBookId = _primaryBible.value?.getBookId(_selectedBookIndex.value)
    viewModelScope.launch {
        _isLoading.value = true
        _isFullyLoadedFlow.value = false

        _loadErrors.value = emptyList()
        try {
            val useReplica = remoteModeActive && syncMode == BibleSyncMode.FULL_REPLICA
            val configuredTranslations = appSettings.bibleSettings.translationList()
            val primaryPath = if (useReplica) {
                remoteBibleCacheFile?.takeIf { it.exists() }
            } else if (configuredTranslations.firstOrNull()?.fileName?.isNotEmpty() == true &&
                appSettings.bibleSettings.storageDirectory.isNotEmpty()
            ) File(appSettings.bibleSettings.storageDirectory, configuredTranslations.first().fileName)
                .takeIf { it.exists() }
            else null

            val secondaryPath = if (useReplica) {
                remoteSecondaryBibleCacheFile?.takeIf { it.exists() }
            } else if (configuredTranslations.getOrNull(1)?.fileName?.isNotEmpty() == true &&
                appSettings.bibleSettings.storageDirectory.isNotEmpty()
            ) File(appSettings.bibleSettings.storageDirectory, configuredTranslations[1].fileName)
                .takeIf { it.exists() }
            else null
            val translationSources = if (useReplica && remoteTranslationCacheFiles.isNotEmpty()) {
                remoteTranslationCacheFiles
            } else if (useReplica) {
                listOfNotNull(primaryPath, secondaryPath).mapIndexed { index, path ->
                    (configuredTranslations.getOrNull(index)?.fileName ?: path.name) to path
                }
            } else {
                configuredTranslations.mapNotNull { translation ->
                    File(appSettings.bibleSettings.storageDirectory, translation.fileName)
                        .takeIf { it.exists() }
                        ?.let { translation.fileName to it }
                }
            }

            val missingTranslations = if (useReplica) emptyList() else {
                val present = translationSources.map { it.first }.toSet()
                configuredTranslations
                    .filter { it.fileName.isNotEmpty() && it.fileName !in present }
                    .map {
                        BibleLoadError(
                            resourcePath = File(appSettings.bibleSettings.storageDirectory, it.fileName).absolutePath,
                            reason = BibleViewModel.MODULE_FILE_MISSING,
                            partial = false,
                        )
                    }
            }

            val bookNameMappingDeferred = async(ioDispatcher) {
                try { BibleBookNames.getBookNameMapping() } catch (_: Exception) { emptyMap() }
            }
            val englishBookNamesDeferred = async(ioDispatcher) {
                try { BibleBookNames.getEnglishBookNames() } catch (_: Exception) { emptyList() }
            }
            val quickPrimary = primaryPath?.let { path ->
                async(ioDispatcher) {
                    try { Bible().apply { loadBooksOnly(path.absolutePath) } }
                    catch (_: Exception) { null }
                }
            }

            val booksOnlyBible = quickPrimary?.await()
            _bookNameMapping.value = bookNameMappingDeferred.await()
            _englishBookNames.value = englishBookNamesDeferred.await()

            if (booksOnlyBible != null && booksOnlyBible.getBookCount() > 0) {
                _primaryBible.value = booksOnlyBible
                _books.value = booksOnlyBible.getCanonicalBooks()
                refreshFilteredLists()
            }

            val bibleDeferred = translationSources.map { (identity, path) ->
                identity to async(ioDispatcher) {
                    try { Bible().apply { loadFromSpb(path.absolutePath) } }
                    catch (e: Exception) { e.printStackTrace(); null }
                }
            }
            val loadedByFile = bibleDeferred.associate { (fileName, deferred) -> fileName to deferred.await() }
            val orderedIdentities = bibleDeferred.map { it.first }
            val loaded = orderedIdentities.mapNotNull { fileName ->
                loadedByFile[fileName]?.let { BibleViewModel.LoadedTranslation(fileName, it) }
            }
            val useRemoteIdentities = useReplica && remoteTranslationCacheFiles.isNotEmpty()
            val primaryIdentity = if (useRemoteIdentities) orderedIdentities.firstOrNull()
                else configuredTranslations.firstOrNull()?.fileName ?: orderedIdentities.firstOrNull()
            val secondaryIdentity = if (useRemoteIdentities) orderedIdentities.getOrNull(1)
                else configuredTranslations.getOrNull(1)?.fileName ?: orderedIdentities.getOrNull(1)
            val primary = primaryIdentity?.let { loadedByFile[it] }
            val secondary = secondaryIdentity?.let { loadedByFile[it] }

            _loadErrors.value = missingTranslations + orderedIdentities.mapNotNull { identity ->
                val path = translationSources.first { it.first == identity }.second
                val bible = loadedByFile[identity]
                when {
                    bible == null -> BibleLoadError(
                        path.absolutePath,
                        BibleViewModel.MODULE_LOAD_THREW,
                        partial = false
                    )
                    else -> bible.loadError
                }
            }

            if (remoteModeActive) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "bible_load_result",
                    mapOf(
                        "primaryPath" to primaryPath?.absolutePath,
                        "secondaryPath" to secondaryPath?.absolutePath,
                        "primaryLoaded" to (primary != null),
                        "secondaryLoaded" to (secondary != null)
                    )
                )
            }

            _primaryBible.value = primary
            _secondaryBible.value = secondary
            _loadedTranslations.value = loaded
            _loadedBibles.value = loaded.map { it.bible }
            onBibleFilePathsChanged?.invoke(translationSources.map { it.second.absolutePath })
            if (secondary != null) secondaryPath?.let {
                onSecondaryBibleFilePathChanged?.invoke(it.absolutePath)
            }

            if (primary != null) {
                _books.value = primary.getCanonicalBooks()

                val bookCount = minOf(primary.getBookCount(), BibleViewModel.CANONICAL_BOOK_COUNT)
                val clampedBookIndex = if (previousBookId != null) {
                    (0 until bookCount).firstOrNull { primary.getBookId(it) == previousBookId }
                        ?: _selectedBookIndex.value.coerceIn(0, (bookCount - 1).coerceAtLeast(0))
                } else {
                    _selectedBookIndex.value.coerceIn(0, (bookCount - 1).coerceAtLeast(0))
                }
                _selectedBookIndex.value = clampedBookIndex
                val bookId = primary.getBookId(clampedBookIndex)
                val chapterResult = withContext(ioDispatcher) {
                    primary.getChapter(bookId, _selectedChapter.value)
                }
                _verses.value = chapterResult.verses
                _selectedVerseIndex.value = _selectedVerseIndex.value.coerceIn(
                    0,
                    (chapterResult.verses.size - 1).coerceAtLeast(0)
                )
                refreshFilteredLists()

                if (previousBookId != null && _verses.value.isNotEmpty()) {
                    _verseSelectionToken.value++
                }
                onBibleLoaded?.invoke(primary, configuredTranslations.firstOrNull()?.fileName.orEmpty())
            } else if (booksOnlyBible == null) {
                _books.value = emptyList()
                _verses.value = emptyList()
                refreshFilteredLists()
            }
        } finally {
            _isLoading.value = false
            _isFullyLoadedFlow.value = true
        }
    }
}
