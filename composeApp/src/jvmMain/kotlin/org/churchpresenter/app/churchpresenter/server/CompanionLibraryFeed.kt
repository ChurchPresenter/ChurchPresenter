package org.churchpresenter.app.churchpresenter.server

import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.io.IOException
import java.sql.SQLException
import kotlinx.coroutines.launch
import org.churchpresenter.bible.Bible
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.calendar.sync.Projection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.utils.Constants

/*
 * The song and Bible libraries as the server publishes them: loaded from disk on start, and
 * replaced whenever the app's own copy changes.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

/**
 * Preloads songs and bible from disk on the server's IO scope.
 * Safe to call at any time; re-call whenever settings change.
 */
fun CompanionServer.preloadData(
    songStorageDir: String,
    bibleStorageDir: String,
    primaryBibleFileName: String
) {
    scope.launch {
        // ── Songs ──────────────────────────────────────────────────────────
        if (songStorageDir.isNotEmpty()) {
            try {
                val dir = File(songStorageDir)
                if (dir.exists() && dir.isDirectory) {
                    val songs = Songs()
                    dir.listFiles { f -> f.extension.lowercase() == Constants.EXTENSION_SPS }
                        ?.sortedBy { it.name }
                        ?.forEach { file ->
                            fun failed(e: Exception) = preloadFailed(
                                "Failed to load song ${file.name}: ${e.message}",
                                "Server: Failed to load song ${file.name}",
                                e,
                            )
                            // An unreadable file, a missing one, or a SongPresenter SQLite database
                            // that will not open.
                            try {
                                songs.loadFromSpsAppend(file.absolutePath)
                            } catch (e: IOException) {
                                failed(e)
                            } catch (e: IllegalArgumentException) {
                                failed(e)
                            } catch (e: SQLException) {
                                failed(e)
                            }
                        }
                    if (songs.getSongCount() > 0) {
                        updateSongs(songs.getSongs())
                    }
                }
            } catch (e: SecurityException) {
                songFolderFailed(songStorageDir, e)
            } catch (e: IllegalArgumentException) {
                // Publishing the catalog: serialization errors are this type.
                songFolderFailed(songStorageDir, e)
            } catch (e: IllegalStateException) {
                songFolderFailed(songStorageDir, e)
            }
        }

        // ── Bible ──────────────────────────────────────────────────────────
        if (bibleStorageDir.isNotEmpty() && primaryBibleFileName.isNotEmpty()) {
            try {
                val file = File(bibleStorageDir, primaryBibleFileName)
                if (file.exists()) {
                    val bible = Bible()
                    // Reports a bad module through loadError rather than throwing; what can
                    // still fail is reaching the file and publishing what was read.
                    bible.loadFromSpb(file.absolutePath)
                    updateBible(bible, primaryBibleFileName)
                }
            } catch (e: SecurityException) {
                bibleFailed(primaryBibleFileName, e)
            } catch (e: IllegalArgumentException) {
                bibleFailed(primaryBibleFileName, e)
            } catch (e: IllegalStateException) {
                bibleFailed(primaryBibleFileName, e)
            }
        }
    }
}

private fun CompanionServer.songFolderFailed(songStorageDir: String, e: Exception) = preloadFailed(
    "Failed to load songs from $songStorageDir: ${e.message}",
    "Server: Failed to load songs from storage",
    e,
)

private fun CompanionServer.bibleFailed(fileName: String, e: Exception) = preloadFailed(
    "Failed to load bible $fileName: ${e.message}",
    "Server: Failed to load bible $fileName",
    e,
)

/** Logs a startup load that failed, and reports it as a warning: the server runs on without it. */
private fun CompanionServer.preloadFailed(logMessage: String, report: String, e: Exception) {
    Log.warn("CompanionServer", logMessage)
    CrashReporter.reportWarning(report, throwable = e, tags = mapOf("subsystem" to "server"))
}

/** Feed the full song list — builds grouped catalog and broadcasts to WS clients. */
fun CompanionServer.updateSongs(songs: List<SongItem>) {
    _songs = songs
    val catalog = buildCatalog(songs)
    _catalog.value = catalog
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_SONGS_UPDATED,
        payload = json.encodeToString(SongCatalogResponse.serializer(), catalog)
    ))
}

/** Feed the primary Bible — builds full nested catalog and broadcasts to WS clients. */
fun CompanionServer.updateBible(bible: Bible, translation: String, filePath: String = "") {
    _bible.value = bible
    _bibleFilePath = filePath
    val catalog = buildBibleCatalog(bible, translation)
    _bibleCatalog.value = catalog
    broadcast(WebSocketMessage(
        type = Constants.WS_EVENT_BIBLE_UPDATED,
        payload = json.encodeToString(BibleCatalogResponse.serializer(), catalog)
    ))
}

/** Records the secondary bible's file path for GET /api/bible/file/secondary — no mobile
 *  companion catalog/broadcast exists for the secondary bible, only InstanceLink uses this. */
fun CompanionServer.updateSecondaryBibleFilePath(filePath: String) {
    if (_secondaryBibleFilePath == filePath) return
    _secondaryBibleFilePath = filePath
    InstanceLinkLogger.log(
        InstanceLinkLogSide.PRIMARY,
        "state_updated",
        mapOf("type" to "secondary_bible_file_path", "filePath" to filePath)
    )
    // Invalidation signal for followers mirroring the secondary bible — they re-download
    // the .spb on this event instead of trusting their local cache forever.
    broadcast(WebSocketMessage(type = Constants.WS_EVENT_SECONDARY_BIBLE_UPDATED, payload = ""))
}

/** Records every configured Bible module in presentation order for Instance Link replicas. */
fun CompanionServer.updateBibleFilePaths(filePaths: List<String>) {
    val existing = filePaths.filter { File(it).exists() }
    if (_bibleFilePaths == existing) return
    _bibleFilePaths = existing
    broadcast(WebSocketMessage(type = Constants.WS_EVENT_SECONDARY_BIBLE_UPDATED, payload = ""))
}

/** The library as songbook records, with each song's usual length, for a phone planning a service. */
fun CompanionServer.songCatalog(): SongCatalogRecordsResponse =
    SongCatalogRecordsResponse(Projection.catalog(_songs, typicalSeconds).values.toList())
