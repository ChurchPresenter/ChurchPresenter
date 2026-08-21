package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

private const val DEBOUNCE_MS = 300L

class SongFolderWatcher(
    private val scope: CoroutineScope,
    private val onSongsChanged: () -> Unit
) {
    private var watchJob: Job? = null
    @Volatile private var watchService: WatchService? = null

    fun watchDirectory(rootDir: File) {
        stop()
        if (!rootDir.exists() || !rootDir.isDirectory) return

        val watchService = FileSystems.getDefault().newWatchService()
        this.watchService = watchService
        watchJob = scope.launch(Dispatchers.IO) {
            try {
                // Recursively watch root and all subdirectories
                fun registerDir(dir: File) {
                    try {
                        dir.toPath().register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY
                        )
                    } catch (_: Exception) {
                    }
                    val subdirs = dir.listFiles { file -> file.isDirectory } ?: emptyArray()
                    for (subdir in subdirs) {
                        registerDir(subdir)
                    }
                }
                registerDir(rootDir)

                while (isActive) {
                    val key = watchService.take()
                    var relevant = false

                    for (event in key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue
                        if (isRelevantEvent(event, key, watchService)) relevant = true
                    }

                    if (relevant) {
                        // Debounce: wait a bit for batch operations to settle
                        delay(DEBOUNCE_MS)
                        withContext(Dispatchers.Main) {
                            onSongsChanged()
                        }
                    }

                    if (!key.reset()) break
                }
            } catch (_: ClosedWatchServiceException) {
                // Expected on dispose
            } catch (_: InterruptedException) {
                // Expected on cancel
            } finally {
                watchService.close()
            }
        }
    }


    /**
     * True when this event should trigger a reload: a .song file changed, or a songbook folder
     * appeared — in which case the new folder is registered for watching too.
     */
    internal fun isRelevantEvent(
        event: WatchEvent<*>,
        key: WatchKey,
        watchService: WatchService,
    ): Boolean {
        // OVERFLOW carries no path and its context is null; the caller filters it, but nothing
        // here enforces that and a null dereference would take the watcher's whole coroutine down.
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) return false
        val fileName = event.context()?.toString() ?: return false
        val isSongFile = fileName.substringAfterLast('.', "").lowercase() == Constants.EXTENSION_SONG
        val file = (key.watchable() as? Path)?.resolve(fileName)?.toFile()
        if (file?.isDirectory != true) return isSongFile
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            runCatching {
                file.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
            }
        }
        return true
    }

    fun dispose() {
        stop()
    }

    private fun stop() {
        watchJob?.cancel()
        watchJob = null
        watchService?.let { runCatching { it.close() } }
        watchService = null
    }
}
